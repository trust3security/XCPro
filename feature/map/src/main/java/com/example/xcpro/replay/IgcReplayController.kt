package com.example.xcpro.replay

import android.content.Context
import android.net.Uri
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.FlightDataCalculator
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.SensorFusionRepository
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
import kotlinx.coroutines.SupervisorJob
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


    private fun createScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    private fun createFusionRepository(): SensorFusionRepository =
        FlightDataCalculator(
            context = appContext,
            sensorDataSource = replaySensorSource,
            scope = scope,
            sinkProvider = sinkProvider,
            windStateFlow = windRepository.windState,
            flightStateSource = flightStateSource,
            enableAudio = true,
            isReplayMode = true
        )

    private fun startForwardingFlightData() {
        val repo = replayFusionRepository ?: return
        forwardJob?.cancel()
        forwardJob = scope.launch {
            repo.flightDataFlow.collect { data ->
                if (_session.value.status == SessionStatus.PLAYING) {
                    val now = System.currentTimeMillis()
                    if (now - lastForwardLogTime >= 1_000L) {
                        lastForwardLogTime = now
                        val windState = windRepository.windState.value
                        val windSpeed = windState.vector?.speed
                        val windQuality = windState.quality
                        val gps = data?.gps
                        val verticalSpeed = data?.verticalSpeed?.value
                        val displayVario = data?.displayVario?.value
                        val xcSoarDisplayVario = data?.xcSoarDisplayVario?.value
                        val tc30 = data?.thermalAverage?.value
                        val tcAvg = data?.thermalAverageCircle?.value
                        val tAvg = data?.thermalAverageTotal?.value
                        AppLogger.d(
                            TAG,
                            "REPLAY_FORWARD gps=${gps?.position?.latitude},${gps?.position?.longitude} " +
                            "gs=${gps?.speed?.value} alt=${gps?.altitude?.value} " +
                            "v=${verticalSpeed} dv=${displayVario} xc=${xcSoarDisplayVario} " +
                            "valid=${data?.varioValid} src=${data?.varioSource} te=${data?.teAltitude?.value} " +
                            "tc30=${tc30} tcAvg=${tcAvg} tAvg=${tAvg} tValid=${data?.currentThermalValid} " +
                            "circling=${data?.isCircling} windQ=${windQuality} wind=${windSpeed}"
                        )
                    }
                    flightDataRepository.update(data, FlightDataRepository.Source.REPLAY)
                }
            }
        }
    }

    private fun ensureScopeActive() {
        if (scope.isActive) return
        AppLogger.w(TAG, "REPLY_SCOPE inactive; rebuilding replay scope")
        scope = createScope()
        forwardJob = null
        replayJob = null
        seekJob = null
        replayFusionRepository = null
    }

    private fun ensureReplayPipelineActive() {
        ensureScopeActive()
        if (replayFusionRepository == null) {
            replayFusionRepository = createFusionRepository()
        }
        if (forwardJob?.isActive != true) {
            startForwardingFlightData()
        }
    }

    private var scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var forwardJob: Job? = null
    private var replayJob: Job? = null
    private var seekJob: Job? = null
    private var points: List<IgcPoint> = emptyList()
    private var currentIndex = 0
    private var sensorsSuspended = false
    private var lastForwardLogTime = 0L

    private var replayFusionRepository: SensorFusionRepository? = null
    private var simConfig = DEFAULT_SIM_CONFIG
    private var sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
    private var resetModeAfterSession = false
    private var autoStopAfterFinish = false

    private val _session = MutableStateFlow(SessionState())
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _events = MutableSharedFlow<ReplayEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ReplayEvent> = _events.asSharedFlow()

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

    fun setAutoStopAfterFinish(enabled: Boolean) {
        autoStopAfterFinish = enabled
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
                    while (currentIndex < points.size && isActive) {
                        val point = points[currentIndex]
                        val previous = points.getOrNull(currentIndex - 1)
                        sampleEmitter.emitSample(point, previous, _session.value.qnhHpa, _session.value.startTimestampMillis, replayFusionRepository)
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
            AppLogger.i(TAG, "REPLY_SEEK progress=$clamped index=$targetIndex ts=${point.timestampMillis}")

            // Update session immediately so UI reflects the new position even if play is paused
            _session.update { state ->
                if (state.selection == null) state else
                    state.copy(currentTimestampMillis = point.timestampMillis)
            }

            runCatching {
                // Seeking is a teleport: reset fusion state so thermal/circling/wind estimators don't
                // interpret the jump as a single "mega-sample" or carry stale altitude baselines.
                replayFusionRepository?.stop() ?: return@runCatching
                if (previousIndex >= pts.size || targetIndex < previousIndex) {
                    resetReplayEmitterState("seek")
                }
                sampleEmitter.emitSample(point, previous, _session.value.qnhHpa, _session.value.startTimestampMillis, replayFusionRepository)
                updateProgress(point.timestampMillis)
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
            emitFinishRampIfNeeded(lastPoint)
        }
        silenceReplayAudio("finish")
        // Clear replay sample before handing control back to live sensors; order matters because
        // FlightDataRepository gates by active source.
        flightDataRepository.clear()
        // Fully reset the replay fusion pipeline so averages/filters don't carry into the next run.
        replayFusionRepository?.stop()
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
        replayFusionRepository?.resetQnhToStandard()
        points = emptyList()
        currentIndex = 0
        _session.value = SessionState(speedMultiplier = _session.value.speedMultiplier)
        resetReplayModeIfNeeded()
    }

    private suspend fun emitFinishRampIfNeeded(lastPoint: IgcPoint) {
        val repo = replayFusionRepository ?: return
        val lastDisplay = repo.flightDataFlow.value?.displayVario?.value
        if (lastDisplay == null || !lastDisplay.isFinite()) return
        val lastDisplayKts = lastDisplay * MPS_TO_KTS
        val absDisplayKts = abs(lastDisplayKts)
        if (absDisplayKts < FINISH_RAMP_MIN_START_KTS || absDisplayKts > FINISH_RAMP_MAX_START_KTS) return

        val stepSimMs = simConfig.baroStepMs.coerceAtLeast(1L)
        val samplesPerStep = (FINISH_RAMP_STEP_DURATION_MS / stepSimMs).coerceAtLeast(1L)
        val delayMs = (stepSimMs / _session.value.speedMultiplier).toLong().coerceAtLeast(1L)
        val sign = if (lastDisplayKts >= 0.0) 1.0 else -1.0
        val rampSteps = FINISH_RAMP_STEPS_KTS.dropWhile { it > absDisplayKts + 1e-6 }
        if (rampSteps.isEmpty()) return

        var prev = lastPoint
        var timestamp = lastPoint.timestampMillis
        for (stepKts in rampSteps) {
            val stepMs = stepKts * KTS_TO_MPS * sign
            repeat(samplesPerStep.toInt()) {
                timestamp += stepSimMs
                repo.updateReplayRealVario(stepMs, timestamp)
                val rampPoint = lastPoint.copy(timestampMillis = timestamp)
                sampleEmitter.emitSample(
                    current = rampPoint,
                    previous = prev,
                    qnhHpa = _session.value.qnhHpa,
                    startTimestampMillis = _session.value.startTimestampMillis,
                    replayFusionRepository = replayFusionRepository
                )
                prev = rampPoint
                delay(delayMs)
            }
        }
        repo.updateReplayRealVario(null, timestamp)
    }

    private fun cancelReplayJob() {
        replayJob?.cancel()
        replayJob = null
    }

    private fun suspendSensors() {
        if (!sensorsSuspended) {
            sensorsSuspended = true
            varioServiceManager.stop()
        }
    }

    private suspend fun resumeSensors() {
        if (sensorsSuspended) {
            sensorsSuspended = false
            varioServiceManager.start()
        }
    }

    private fun silenceReplayAudio(reason: String) {
        val repo = replayFusionRepository ?: return
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
    }


    companion object {
        private const val TAG = "IgcReplayController"
        private const val MIN_FRAME_INTERVAL_MS = 1L  // allow sub-second replay cadence
        private const val MAX_SPEED = 20.0
        private const val ASSET_URI_PREFIX = "asset:///"
        private val DEFAULT_SIM_CONFIG = ReplaySimConfig()
        private const val FINISH_RAMP_STEP_DURATION_MS = 350L
        private const val FINISH_RAMP_MIN_START_KTS = 0.1   // ignore near-zero
        private const val FINISH_RAMP_MAX_START_KTS = 2.0   // only taper gentle end values
        private val FINISH_RAMP_STEPS_KTS = listOf(0.7, 0.4, 0.3, 0.2, 0.1, 0.0)
        private const val MPS_TO_KTS = 1.943844
        private const val KTS_TO_MPS = 0.514444
    }

    private fun prepareSession(log: IgcLog, selection: Selection) {
        sampleEmitter.reset()
        val densified = when (simConfig.mode) {
            ReplayMode.REALTIME_SIM -> IgcReplayMath.densifyPoints(
                original = log.points,
                stepMs = simConfig.baroStepMs,
                jitterMs = simConfig.jitterMs,
                random = sampleEmitter.random
            )
            ReplayMode.REFERENCE -> IgcReplayMath.densifyPoints(log.points)
        }
        if (densified.isEmpty()) throw IllegalArgumentException("IGC file has no B records")
        cancelReplayJob()
        seekJob?.cancel()
        seekJob = null
        points = densified
        currentIndex = 0
        suspendSensors()
        replaySensorSource.reset()

        flightDataRepository.setActiveSource(FlightDataRepository.Source.REPLAY)
        val qnh = log.metadata.qnhHpa ?: DEFAULT_QNH_HPA
        val start = points.first().timestampMillis
        val duration = (points.last().timestampMillis - start).coerceAtLeast(1L)
        logReplaySessionPrep(
            selection = selection,
            pointCount = points.size,
            startMillis = start,
            endMillis = points.last().timestampMillis,
            qnh = qnh,
            tag = TAG
        )
        val repo = checkNotNull(replayFusionRepository) { "Replay fusion pipeline not initialized" }
        repo.stop() // reset all smoothing/thermal state
        repo.setManualQnh(qnh)
        _session.value = SessionState(
            selection = selection,
            status = SessionStatus.PAUSED,
            speedMultiplier = _session.value.speedMultiplier,
            startTimestampMillis = start,
            currentTimestampMillis = start,
            durationMillis = duration,
            qnhHpa = qnh
        )
        AppLogger.i(TAG, "REPLY_SESSION selection=${selection.displayName ?: selection.uri} durationMs=$duration start=$start")

        sampleEmitter.emitSample(points.first(), null, qnh, start, replayFusionRepository)
    }
}

