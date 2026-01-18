package com.example.xcpro.replay

import android.content.Context
import android.net.Uri
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.weather.wind.data.ReplayAirspeedRepository
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlin.math.abs
import kotlin.coroutines.coroutineContext

/**
 * Orchestrates IGC replay sessions and forwards replay samples into the fused sensor pipeline.
 *
 * AI-NOTE: Replay uses IGC timestamps as the simulation clock so downstream filters stay deterministic.
 */
@Singleton
class IgcReplayController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val flightDataRepository: FlightDataRepository,
    private val varioServiceManager: VarioServiceManager,
    private val sinkProvider: StillAirSinkProvider,
    private val windRepository: WindSensorFusionRepository,
    private val flightStateSource: FlightStateSource,
    private val replaySensorSource: ReplaySensorSource,
    private val replayAirspeedRepository: ReplayAirspeedRepository,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    private var replayJob: Job? = null
    private var seekJob: Job? = null
    private var points: List<IgcPoint> = emptyList()
    private var currentIndex = 0
    private var simConfig = DEFAULT_SIM_CONFIG
    private var sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
    private var runtimeInterpolator: ReplayRuntimeInterpolator? = null
    private var uiRuntimeInterpolator: ReplayRuntimeInterpolator? = null
    private var runtimeTimestampMs: Long = 0L
    private var resetModeAfterSession = false
    private var autoStopAfterFinish = false
    private val uiInterpolatorLock = Any()


    private val _session = MutableStateFlow(SessionState())
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _events = MutableSharedFlow<ReplayEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ReplayEvent> = _events.asSharedFlow()

    private val pipeline = ReplayPipeline(
        appContext = appContext,
        flightDataRepository = flightDataRepository,
        varioServiceManager = varioServiceManager,
        sinkProvider = sinkProvider,
        windRepository = windRepository,
        flightStateSource = flightStateSource,
        replaySensorSource = replaySensorSource,
        dispatcher = dispatcher,
        sessionState = _session.asStateFlow(),
        tag = TAG
    )

    private val scope: CoroutineScope
        get() = pipeline.scope

    private fun ensureScopeActive() {
        pipeline.ensureScope {
            replayJob = null
            seekJob = null
        }
    }

    private fun ensureReplayPipelineActive() {
        pipeline.ensureActive {
            replayJob = null
            seekJob = null
        }
    }

    suspend fun loadFile(uri: Uri, displayName: String?) {
        ensureReplayPipelineActive()
        var failure: Throwable? = null
        withContext(scope.coroutineContext) {
            try {
                val log = appContext.loadIgcLog(uri)
                AppLogger.i(TAG, "REPLY_LOAD Loaded IGC file ${displayName ?: uri} with ${log.points.size} raw points (qnh=${log.metadata.qnhHpa})")
                prepareSession(
                    log = log,
                    selection = Selection(uri, displayName)
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                failure = t
            }
        }
        failure?.let { t ->
            AppLogger.e(TAG, "Failed to load IGC file ${displayName ?: uri}", t)
            _events.tryEmit(ReplayEvent.Failed(t))
            throw t
        }
    }

    suspend fun loadAsset(assetPath: String, displayName: String? = null) {
        ensureReplayPipelineActive()
        var failure: Throwable? = null
        withContext(scope.coroutineContext) {
            try {
                val log = appContext.loadIgcAssetLog(assetPath)
                val name = displayName ?: assetPath.substringAfterLast('/')
                val uri = Uri.parse("$ASSET_URI_PREFIX$assetPath")
                AppLogger.i(TAG, "REPLY_LOAD Loaded IGC asset $assetPath with ${log.points.size} raw points (qnh=${log.metadata.qnhHpa})")
                prepareSession(
                    log = log,
                    selection = Selection(uri, name)
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                failure = t
            }
        }
        failure?.let { t ->
            AppLogger.e(TAG, "Failed to load IGC asset $assetPath", t)
            _events.tryEmit(ReplayEvent.Failed(t))
            throw t
        }
    }

    suspend fun loadLog(log: IgcLog, displayName: String? = null) {
        ensureReplayPipelineActive()
        var failure: Throwable? = null
        withContext(scope.coroutineContext) {
            try {
                val name = displayName ?: "Replay log"
                val uri = Uri.parse("memory://replay/${name.replace(' ', '_')}")
                AppLogger.i(TAG, "REPLY_LOAD Loaded synthetic IGC log with ${log.points.size} raw points (qnh=${log.metadata.qnhHpa})")
                prepareSession(
                    log = log,
                    selection = Selection(uri, name)
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                failure = t
            }
        }
        failure?.let { t ->
            AppLogger.e(TAG, "Failed to load synthetic IGC log", t)
            _events.tryEmit(ReplayEvent.Failed(t))
            throw t
        }
    }

    fun setReplayMode(mode: ReplayMode, resetAfterSession: Boolean = false) {
        if (_session.value.status == SessionStatus.PLAYING) {
            AppLogger.w(TAG, "Replay mode change ignored while playing")
            return
        }
        if (simConfig.mode == mode) {
            resetModeAfterSession = resetAfterSession
            return
        }
        simConfig = simConfig.copy(mode = mode)
        sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
        resetModeAfterSession = resetAfterSession
        AppLogger.i(TAG, "Replay mode set to ${mode.name} (resetAfterSession=$resetAfterSession)")
    }

    fun getReplayCadence(): ReplayCadenceProfile = ReplayCadenceProfile(
        referenceStepMs = simConfig.referenceStepMs,
        gpsStepMs = simConfig.gpsStepMs
    )

    fun getReplayBaroStepMs(): Long = simConfig.baroStepMs

    fun getReplayNoiseProfile(): ReplayNoiseProfile = ReplayNoiseProfile(
        pressureNoiseSigmaHpa = simConfig.pressureNoiseSigmaHpa,
        gpsAltitudeNoiseSigmaM = simConfig.gpsAltitudeNoiseSigmaM,
        jitterMs = simConfig.jitterMs
    )

    fun getReplayGpsAccuracyMeters(): Float = simConfig.gpsAccuracyMeters

    fun getReplayInterpolation(): ReplayInterpolation = simConfig.interpolation

    fun setReplayCadence(profile: ReplayCadenceProfile) {
        if (_session.value.status == SessionStatus.PLAYING) {
            AppLogger.w(TAG, "Replay cadence change ignored while playing")
            return
        }
        val referenceStepMs = profile.referenceStepMs.coerceAtLeast(1L)
        val gpsStepMs = profile.gpsStepMs.coerceAtLeast(0L)
        if (simConfig.referenceStepMs == referenceStepMs && simConfig.gpsStepMs == gpsStepMs) return
        simConfig = simConfig.copy(
            referenceStepMs = referenceStepMs,
            gpsStepMs = gpsStepMs
        )
        sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
        AppLogger.i(TAG, "Replay cadence set referenceStepMs=$referenceStepMs gpsStepMs=$gpsStepMs")
    }

    fun setReplayBaroStepMs(stepMs: Long) {
        if (_session.value.status == SessionStatus.PLAYING) {
            AppLogger.w(TAG, "Replay baro step change ignored while playing")
            return
        }
        val clamped = stepMs.coerceAtLeast(1L)
        if (simConfig.baroStepMs == clamped) return
        simConfig = simConfig.copy(baroStepMs = clamped)
        sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
        AppLogger.i(TAG, "Replay baro step set baroStepMs=$clamped")
    }

    fun setReplayNoiseProfile(profile: ReplayNoiseProfile) {
        if (_session.value.status == SessionStatus.PLAYING) {
            AppLogger.w(TAG, "Replay noise profile change ignored while playing")
            return
        }
        val jitterMs = profile.jitterMs.coerceAtLeast(0L)
        val pressureSigma = profile.pressureNoiseSigmaHpa.coerceAtLeast(0.0)
        val gpsSigma = profile.gpsAltitudeNoiseSigmaM.coerceAtLeast(0.0)
        if (simConfig.pressureNoiseSigmaHpa == pressureSigma &&
            simConfig.gpsAltitudeNoiseSigmaM == gpsSigma &&
            simConfig.jitterMs == jitterMs
        ) return
        simConfig = simConfig.copy(
            pressureNoiseSigmaHpa = pressureSigma,
            gpsAltitudeNoiseSigmaM = gpsSigma,
            jitterMs = jitterMs
        )
        sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
        AppLogger.i(
            TAG,
            "Replay noise profile set pressureSigma=$pressureSigma gpsSigma=$gpsSigma jitterMs=$jitterMs"
        )
    }

    fun setReplayGpsAccuracyMeters(accuracyMeters: Float) {
        if (_session.value.status == SessionStatus.PLAYING) {
            AppLogger.w(TAG, "Replay GPS accuracy change ignored while playing")
            return
        }
        val clamped = accuracyMeters.coerceIn(MIN_GPS_ACCURACY_M, MAX_GPS_ACCURACY_M)
        if (simConfig.gpsAccuracyMeters == clamped) return
        simConfig = simConfig.copy(gpsAccuracyMeters = clamped)
        sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
        AppLogger.i(TAG, "Replay GPS accuracy set accuracyMeters=$clamped")
    }

    fun setReplayInterpolation(interpolation: ReplayInterpolation) {
        if (_session.value.status == SessionStatus.PLAYING) {
            AppLogger.w(TAG, "Replay interpolation change ignored while playing")
            return
        }
        if (simConfig.interpolation == interpolation) return
        simConfig = simConfig.copy(interpolation = interpolation)
        sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
        AppLogger.i(TAG, "Replay interpolation set to ${interpolation.name}")
    }

    fun setAutoStopAfterFinish(enabled: Boolean) {
        autoStopAfterFinish = enabled
    }

    fun getInterpolatedReplayHeadingDeg(timestampMs: Long): Double? {
        if (simConfig.interpolation != ReplayInterpolation.CATMULL_ROM_RUNTIME) return null
        val session = _session.value
        if (session.selection == null) return null
        val start = session.startTimestampMillis
        val end = start + session.durationMillis
        if (end <= start) return null
        val clamped = timestampMs.coerceIn(start, end)
        val interpolator = uiRuntimeInterpolator ?: return null
        val fix = synchronized(uiInterpolatorLock) {
            interpolator.interpolate(clamped)
        } ?: return null
        return fix.movement.bearingDeg.toDouble()
    }

    fun getInterpolatedReplayPose(timestampMs: Long): ReplayDisplayPose? {
        if (simConfig.interpolation != ReplayInterpolation.CATMULL_ROM_RUNTIME) return null
        val session = _session.value
        if (session.selection == null) return null
        val start = session.startTimestampMillis
        val end = start + session.durationMillis
        if (end <= start) return null
        val clamped = timestampMs.coerceIn(start, end)
        val interpolator = uiRuntimeInterpolator ?: return null
        val fix = synchronized(uiInterpolatorLock) {
            interpolator.interpolate(clamped)
        } ?: return null
        return ReplayDisplayPose(
            latitude = fix.point.latitude,
            longitude = fix.point.longitude,
            timestampMillis = fix.point.timestampMillis,
            bearingDeg = fix.movement.bearingDeg.toDouble(),
            speedMs = fix.movement.speedMs
        )
    }

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
                                pipeline.replayFusionRepository
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
        pipeline.replayFusionRepository?.stop()
        flightDataRepository.setActiveSource(FlightDataRepository.Source.LIVE)
        // Propagate a null sample in LIVE mode so UI/audio drop back to zero instead of
        // displaying the last replay value until live sensors tick again.
        flightDataRepository.update(null, FlightDataRepository.Source.LIVE)
        silenceReplayAudio("stop")
        pipeline.replayFusionRepository?.resetQnhToStandard()
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
                pipeline.replayFusionRepository?.stop() ?: return@runCatching
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
                        pipeline.replayFusionRepository,
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
                        pipeline.replayFusionRepository
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


    private fun updateProgress(timestamp: Long) {
        _session.update { state ->
            if (state.selection == null) state else
                state.copy(currentTimestampMillis = timestamp)
        }
        val s = _session.value
        val elapsed = s.currentTimestampMillis - s.startTimestampMillis
        val frac = s.progressFraction
        if (AppLogger.rateLimit(TAG, "replay_progress", 1_000L)) {
            AppLogger.i(TAG, "REPLY_PROGRESS elapsed=${elapsed}ms progress=${"%.3f".format(frac)} status=${s.status}")
        }
    }

    private suspend fun finishReplay() {
        points.lastOrNull()?.let { lastPoint ->
            emitFinishRampIfNeeded(
                lastPoint = lastPoint,
                session = _session.value,
                simConfig = simConfig,
                sampleEmitter = sampleEmitter,
                replayFusionRepository = pipeline.replayFusionRepository
            )
        }
        silenceReplayAudio("finish")
        // Clear replay sample before handing control back to live sensors; order matters because
        // FlightDataRepository gates by active source.
        flightDataRepository.clear()
        // Fully reset the replay fusion pipeline so averages/filters don't carry into the next run.
        pipeline.replayFusionRepository?.stop()
        _session.update { it.copy(status = SessionStatus.PAUSED) }
        flightDataRepository.setActiveSource(FlightDataRepository.Source.LIVE)
        // Push a null LIVE sample so UI/audio immediately drop to zero instead of waiting for the
        // next live sensor tick.
        flightDataRepository.update(null, FlightDataRepository.Source.LIVE)
        resumeSensors()
        // Mark the session as finished so the next play restarts from the beginning.
        currentIndex = points.size
        _events.emit(ReplayEvent.Completed(points.size))
        if (autoStopAfterFinish) {
            autoStopAfterFinish = false
            resetReplayAfterFinish()
        } else {
            resetReplayModeIfNeeded()
        }
    }

    private fun resetReplayAfterFinish() {
        // Clear replay selection + state so the app returns to live mode automatically.
        replaySensorSource.reset()
        replayAirspeedRepository.reset()
        flightDataRepository.clear()
        flightDataRepository.setActiveSource(FlightDataRepository.Source.LIVE)
        flightDataRepository.update(null, FlightDataRepository.Source.LIVE)
        pipeline.replayFusionRepository?.resetQnhToStandard()
        points = emptyList()
        currentIndex = 0
        _session.value = SessionState(speedMultiplier = _session.value.speedMultiplier)
        resetReplayModeIfNeeded()
    }

    private fun cancelReplayJob() {
        replayJob?.cancel()
        replayJob = null
    }

    private fun suspendSensors() {
        pipeline.suspendSensors()
    }

    private suspend fun resumeSensors() {
        pipeline.resumeSensors()
    }

    private fun silenceReplayAudio(reason: String) {
        val repo = pipeline.replayFusionRepository ?: return
        AppLogger.i(TAG, "REPLAY_AUDIO silence reason=$reason")
        repo.stop()
    }

    private fun resetReplayModeIfNeeded() {
        if (!resetModeAfterSession) return
        resetModeAfterSession = false
        val defaultMode = DEFAULT_SIM_CONFIG.mode
        if (simConfig.mode != defaultMode) {
            simConfig = simConfig.copy(mode = defaultMode)
            sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
            AppLogger.i(TAG, "Replay mode reset to ${defaultMode.name}")
        }
    }

    private fun resetReplayEmitterState(reason: String) {
        AppLogger.d(TAG, "REPLAY_RESET reason=$reason")
        sampleEmitter.reset()
        replaySensorSource.reset()
        runtimeInterpolator?.reset()
        runtimeTimestampMs = _session.value.startTimestampMillis
    }


    companion object {
        private const val TAG = "IgcReplayController"
        private const val MIN_FRAME_INTERVAL_MS = 1L  // allow sub-second replay cadence
        private const val MAX_SPEED = 20.0
        private const val MIN_GPS_ACCURACY_M = 1f
        private const val MAX_GPS_ACCURACY_M = 50f
        private const val ASSET_URI_PREFIX = "asset:///"
        private val DEFAULT_SIM_CONFIG = ReplaySimConfig()
    }

    private fun prepareSession(log: IgcLog, selection: Selection) {
        val prepared = prepareReplaySession(
            log = log,
            selection = selection,
            simConfig = simConfig,
            sampleEmitter = sampleEmitter,
            tag = TAG
        )
        cancelReplayJob()
        seekJob?.cancel()
        seekJob = null
        points = prepared.points
        currentIndex = 0
        runtimeInterpolator = if (simConfig.interpolation == ReplayInterpolation.CATMULL_ROM_RUNTIME) {
            ReplayRuntimeInterpolator(points)
        } else {
            null
        }
        uiRuntimeInterpolator = if (simConfig.interpolation == ReplayInterpolation.CATMULL_ROM_RUNTIME) {
            ReplayRuntimeInterpolator(points)
        } else {
            null
        }
        runtimeTimestampMs = prepared.startMillis
        suspendSensors()
        replaySensorSource.reset()

        flightDataRepository.setActiveSource(FlightDataRepository.Source.REPLAY)
        val repo = checkNotNull(pipeline.replayFusionRepository) { "Replay fusion pipeline not initialized" }
        repo.stop() // reset all smoothing/thermal state
        repo.setManualQnh(prepared.qnhHpa)
        _session.value = SessionState(
            selection = selection,
            status = SessionStatus.PAUSED,
            speedMultiplier = _session.value.speedMultiplier,
            startTimestampMillis = prepared.startMillis,
            currentTimestampMillis = prepared.startMillis,
            durationMillis = prepared.durationMillis,
            qnhHpa = prepared.qnhHpa
        )
        if (simConfig.interpolation == ReplayInterpolation.CATMULL_ROM_RUNTIME) {
            val interpolated = runtimeInterpolator?.interpolate(prepared.startMillis)
            val initialPoint = interpolated?.point ?: points.first()
            sampleEmitter.emitSample(
                initialPoint,
                null,
                prepared.qnhHpa,
                prepared.startMillis,
                pipeline.replayFusionRepository,
                interpolated?.movement
            )
        } else {
            sampleEmitter.emitSample(
                points.first(),
                null,
                prepared.qnhHpa,
                prepared.startMillis,
                pipeline.replayFusionRepository
            )
        }
    }

    private suspend fun playRuntimeInterpolation() {
        val interpolator = runtimeInterpolator ?: return
        val stepMs = simConfig.gpsStepMs.coerceAtLeast(1L)
        val sessionStart = _session.value.startTimestampMillis
        val sessionEnd = sessionStart + _session.value.durationMillis
        var previousPoint: IgcPoint? = null
        while (runtimeTimestampMs <= sessionEnd && coroutineContext.isActive) {
            val fix = interpolator.interpolate(runtimeTimestampMs) ?: break
            sampleEmitter.emitSample(
                fix.point,
                previousPoint,
                _session.value.qnhHpa,
                _session.value.startTimestampMillis,
                pipeline.replayFusionRepository,
                fix.movement
            )
            previousPoint = fix.point
            updateProgress(runtimeTimestampMs)
            if (AppLogger.rateLimit(TAG, "replay_frame", 1_000L)) {
                AppLogger.d(
                    TAG,
                    "REPLY_FRAME runtime ts=${runtimeTimestampMs} " +
                        "alt=${fix.point.pressureAltitude ?: fix.point.gpsAltitude} speed=${_session.value.speedMultiplier}"
                )
            }
            if (runtimeTimestampMs >= sessionEnd) {
                finishReplay()
                break
            }
            val speed = _session.value.speedMultiplier
            val delayMillis = (stepMs / speed).toLong().coerceAtLeast(1L)
            runtimeTimestampMs += stepMs
            delay(delayMillis)
        }
    }
}

