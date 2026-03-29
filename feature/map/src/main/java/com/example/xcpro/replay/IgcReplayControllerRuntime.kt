package com.example.xcpro.replay

import android.content.Context
import android.net.Uri
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.weather.wind.data.ReplayAirspeedRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


open class IgcReplayControllerRuntime(
    @ApplicationContext internal val appContext: Context,
    internal val flightDataRepository: FlightDataRepository,
    internal val replaySensorSource: ReplaySensorSource,
    internal val replayAirspeedRepository: ReplayAirspeedRepository,
    private val replayPipelineFactory: ReplayPipelineFactory,
    internal val igcParser: IgcParser
) {
    internal var replayJob: Job? = null
    internal var seekJob: Job? = null
    internal var points: List<IgcPoint> = emptyList()
    internal var currentIndex = 0
    internal var simConfig = DEFAULT_SIM_CONFIG
    internal var sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
    internal var runtimeInterpolator: ReplayRuntimeInterpolator? = null
    internal var uiRuntimeInterpolator: ReplayRuntimeInterpolator? = null
    internal var runtimeTimestampMs: Long = 0L
    internal var resetModeAfterSession = false
    internal var autoStopAfterFinish = false
    internal val uiInterpolatorLock = Any()


    internal val _session = MutableStateFlow(SessionState())
    val session: StateFlow<SessionState> = _session.asStateFlow()

    internal val _events = MutableSharedFlow<ReplayEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ReplayEvent> = _events.asSharedFlow()

    internal val pipeline = replayPipelineFactory.create(
        sessionState = _session.asStateFlow(),
        tag = TAG
    )
    internal var replayRuntime: ReplayPipelineRuntime = pipeline.createRuntime()

    internal val scope: CoroutineScope
        get() = replayRuntime.scope

    internal val replayFusionRepository
        get() = replayRuntime.replayFusionRepository

    private fun ensureScopeActive() {
        replayRuntime = pipeline.ensureScope(replayRuntime) {
            replayJob = null
            seekJob = null
        }
    }

    internal fun ensureReplayPipelineActive() {
        replayRuntime = pipeline.ensureActive(replayRuntime) {
            replayJob = null
            seekJob = null
        }
    }

    suspend fun loadDocument(document: DocumentRef) = loadDocumentRuntime(document)

    suspend fun loadFile(uri: Uri, displayName: String?) = loadFileRuntime(uri, displayName)

    suspend fun loadAsset(assetPath: String, displayName: String? = null) =
        loadAssetRuntime(assetPath, displayName)

    suspend fun loadLog(log: IgcLog, displayName: String? = null) = loadLogRuntime(log, displayName)

    fun setReplayMode(mode: ReplayMode, resetAfterSession: Boolean = false) =
        setReplayModeRuntime(mode, resetAfterSession)

    fun getReplayMode(): ReplayMode = getReplayModeRuntime()

    fun getReplayCadence(): ReplayCadenceProfile = getReplayCadenceRuntime()

    fun getReplayBaroStepMs(): Long = getReplayBaroStepMsRuntime()

    fun getReplayNoiseProfile(): ReplayNoiseProfile = getReplayNoiseProfileRuntime()

    fun getReplayGpsAccuracyMeters(): Float = getReplayGpsAccuracyMetersRuntime()

    fun getReplayInterpolation(): ReplayInterpolation = getReplayInterpolationRuntime()

    fun setReplayCadence(profile: ReplayCadenceProfile) = setReplayCadenceRuntime(profile)

    fun setReplayBaroStepMs(stepMs: Long) = setReplayBaroStepMsRuntime(stepMs)

    fun setReplayNoiseProfile(profile: ReplayNoiseProfile) = setReplayNoiseProfileRuntime(profile)

    fun setReplayGpsAccuracyMeters(accuracyMeters: Float) = setReplayGpsAccuracyMetersRuntime(accuracyMeters)

    fun setReplayInterpolation(interpolation: ReplayInterpolation) =
        setReplayInterpolationRuntime(interpolation)

    fun setAutoStopAfterFinish(enabled: Boolean) = setAutoStopAfterFinishRuntime(enabled)

    fun isAutoStopAfterFinishEnabled(): Boolean = isAutoStopAfterFinishEnabledRuntime()

    fun getInterpolatedReplayHeadingDeg(timestampMs: Long): Double? =
        getInterpolatedReplayHeadingDegRuntime(timestampMs)

    fun getInterpolatedReplayPose(timestampMs: Long): ReplayDisplayPose? =
        getInterpolatedReplayPoseRuntime(timestampMs)

    fun play() {
        ensureReplayPipelineActive()
        AppLogger.i(TAG, "REPLY_PLAY entry scopeActive=${scope.isActive} points=${points.size} jobActive=${replayJob != null}")
        scope.launch {
            AppLogger.i(TAG, "REPLY_PLAY request status=${_session.value.status} points=${points.size} currentIndex=$currentIndex")
            if (points.isEmpty()) {
                AppLogger.w(TAG, "Play requested but no points are loaded")
                return@launch
            }
            if (_session.value.status == SessionStatus.PLAYING) return@launch
            if (currentIndex >= points.size) {
                currentIndex = 0
                resetReplayEmitterState("restart")
            }
            flightDataRepository.setActiveSource(FlightDataRepository.Source.REPLAY)
            AppLogger.d(TAG, "REPLY_PLAY start currentIndex=$currentIndex pts=${points.size}")
            suspendSensors()
            cancelReplayJob()
            replayJob = launch {
                AppLogger.i(TAG, "REPLY_PLAY start idx=$currentIndex total=${points.size}")
                _session.update { it.copy(status = SessionStatus.PLAYING) }
                try {
                    if (simConfig.interpolation == ReplayInterpolation.CATMULL_ROM_RUNTIME) {
                        playRuntimeInterpolation()
                    } else {
                        while (currentIndex < points.size && isActive) {
                            val point = points[currentIndex]
                            val previous = points.getOrNull(currentIndex - 1)
                            sampleEmitter.emitSample(
                                point,
                                previous,
                                _session.value.qnhHpa,
                                _session.value.startTimestampMillis,
                                replayFusionRepository
                            )
                            updateProgress(point.timestampMillis)
                            if (AppLogger.rateLimit(TAG, "replay_frame", 1_000L)) {
                                AppLogger.d(
                                    TAG,
                                    "REPLY_FRAME idx=$currentIndex ts=${point.timestampMillis} " +
                                        "alt=${point.pressureAltitude ?: point.gpsAltitude} speed=${_session.value.speedMultiplier}"
                                )
                            }

                            val nextIndex = currentIndex + 1
                            if (nextIndex >= points.size) {
                                finishReplay()
                                break
                            }
                            val nextPoint = points[nextIndex]
                            val delta = (nextPoint.timestampMillis - point.timestampMillis).coerceAtLeast(MIN_FRAME_INTERVAL_MS)
                            val speed = _session.value.speedMultiplier
                            val delayMillis = (delta / speed).toLong().coerceAtLeast(1L)
                            currentIndex = nextIndex
                            delay(delayMillis)
                        }
                    }
                } catch (c: CancellationException) {
                    AppLogger.w(TAG, "REPLY_PLAY cancelled", c)
                    throw c
                } catch (t: Throwable) {
                    AppLogger.e(TAG, "REPLY_PLAY error", t)
                    _events.tryEmit(ReplayEvent.Failed(t))
                } finally {
                    AppLogger.i(TAG, "REPLY_PLAY end status=${_session.value.status} idx=$currentIndex")
                }
            }
        }
    }

    fun pause() {
        ensureScopeActive()
        scope.launch {
            if (_session.value.status != SessionStatus.PLAYING) return@launch
            cancelReplayJob()
            _session.update { it.copy(status = SessionStatus.PAUSED) }
        }
    }

    fun stop(emitCancelledEvent: Boolean = true) {
        ensureScopeActive()
        val stopScope = scope
        stopScope.launch {
            stopInternal(emitCancelledEvent)
        }
    }

    suspend fun stopAndWait(emitCancelledEvent: Boolean = true) {
        ensureScopeActive()
        val stopContext = scope.coroutineContext
        withContext(stopContext) {
            stopInternal(emitCancelledEvent)
        }
    }

    private suspend fun stopInternal(emitCancelledEvent: Boolean) {
        AppLogger.i(TAG, "REPLY_STOP request")
        cancelReplayJob()
        seekJob?.cancel()
        seekJob = null
        replaySensorSource.reset()
        replayAirspeedRepository.reset()
        // Clear replay data before switching source back to LIVE to avoid stale UI after stop.
        flightDataRepository.clear()
        // Fully reset the replay fusion pipeline so averages/filters don't carry into the next run.
        replayFusionRepository?.stop()
        flightDataRepository.setActiveSource(FlightDataRepository.Source.LIVE)
        // Propagate a null sample in LIVE mode so UI/audio drop back to zero instead of
        // displaying the last replay value until live sensors tick again.
        flightDataRepository.update(null, FlightDataRepository.Source.LIVE)
        silenceReplayAudio("stop")
        replayFusionRepository?.resetQnhToStandard()
        resumeSensors()
        points = emptyList()
        currentIndex = 0
        runtimeInterpolator = null
        uiRuntimeInterpolator = null
        runtimeTimestampMs = 0L

        _session.value = SessionState(speedMultiplier = _session.value.speedMultiplier)
        resetReplayModeIfNeeded()
        if (emitCancelledEvent) {
            _events.tryEmit(ReplayEvent.Cancelled)
        }
    }

    fun setSpeed(multiplier: Double) {
        val clamped = multiplier.coerceIn(DEFAULT_SPEED, MAX_SPEED)
        _session.update { it.copy(speedMultiplier = clamped) }
    }

    fun seekTo(progress: Float) {
        ensureScopeActive()
        seekJob?.cancel()
        seekJob = scope.launch {
            val pts = points
            AppLogger.i(TAG, "REPLY_SEEK request progress=$progress pts=${pts.size}")
            if (pts.isEmpty()) {
                _events.tryEmit(ReplayEvent.Failed(IllegalStateException("No points loaded")))
                return@launch
            }
            flightDataRepository.setActiveSource(FlightDataRepository.Source.REPLAY)
            val clamped = progress.coerceIn(0f, 1f)
            val targetIndex = (clamped * (pts.size - 1)).toInt().coerceIn(0, pts.lastIndex)
            val previousIndex = currentIndex
            currentIndex = targetIndex
            val point = pts[targetIndex]
            val previous = pts.getOrNull(targetIndex - 1)
            val previousTime = _session.value.currentTimestampMillis
            val targetTimeForSession = if (simConfig.interpolation == ReplayInterpolation.CATMULL_ROM_RUNTIME) {
                val start = _session.value.startTimestampMillis
                val duration = _session.value.durationMillis
                (start + (duration * clamped)).toLong()
                    .coerceAtLeast(start)
                    .coerceAtMost(start + duration)
            } else {
                point.timestampMillis
            }
            AppLogger.i(TAG, "REPLY_SEEK progress=$clamped index=$targetIndex ts=$targetTimeForSession")

            // Update session immediately so UI reflects the new position even if play is paused
            _session.update { state ->
                if (state.selection == null) state else
                    state.copy(currentTimestampMillis = targetTimeForSession)
            }

            runCatching {
                // Seeking is a teleport: reset fusion state so thermal/circling/wind estimators don't
                // interpret the jump as a single "mega-sample" or carry stale altitude baselines.
                replayFusionRepository?.stop() ?: return@runCatching
                if (simConfig.interpolation == ReplayInterpolation.CATMULL_ROM_RUNTIME) {
                    val targetTime = targetTimeForSession
                    val stepMs = simConfig.gpsStepMs.coerceAtLeast(1L)
                    runtimeTimestampMs = targetTime
                    runtimeInterpolator?.seekTo(targetTime)
                    val interpolated = runtimeInterpolator?.interpolate(targetTime)
                    val previousInterpolated = runtimeInterpolator
                        ?.interpolate(targetTime - stepMs)
                        ?.point
                    val fixPoint = interpolated?.point ?: point
                    if (previousIndex >= pts.size || targetTime < previousTime) {
                        resetReplayEmitterState("seek")
                    }
                    sampleEmitter.emitSample(
                        fixPoint,
                        previousInterpolated,
                        _session.value.qnhHpa,
                        _session.value.startTimestampMillis,
                        replayFusionRepository,
                        interpolated?.movement
                    )
                    updateProgress(targetTime)
                } else {
                    if (previousIndex >= pts.size || targetIndex < previousIndex) {
                        resetReplayEmitterState("seek")
                    }
                    sampleEmitter.emitSample(
                        point,
                        previous,
                        _session.value.qnhHpa,
                        _session.value.startTimestampMillis,
                        replayFusionRepository
                    )
                    updateProgress(point.timestampMillis)
                }
                if (_session.value.status == SessionStatus.PLAYING) {
                    cancelReplayJob()
                    _session.update { it.copy(status = SessionStatus.PAUSED) }
                    play()
                }
            }.onFailure { t ->
                if (t !is CancellationException) {
                    AppLogger.e(TAG, "Seek failed", t)
                    _events.tryEmit(ReplayEvent.Failed(t))
                }
            }
        }
    }
    companion object {
        internal const val TAG = "IgcReplayController"
        private const val MIN_FRAME_INTERVAL_MS = 1L  // allow sub-second replay cadence
        internal const val MAX_SPEED = 20.0
        internal const val MIN_GPS_ACCURACY_M = 1f
        internal const val MAX_GPS_ACCURACY_M = 50f
        internal const val ASSET_URI_PREFIX = "asset:///"
        internal val DEFAULT_SIM_CONFIG = ReplaySimConfig()
    }

}
