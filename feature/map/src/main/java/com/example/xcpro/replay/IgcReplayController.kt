package com.example.xcpro.replay

import android.content.Context
import android.net.Uri
import android.util.Log
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
                        Log.d(
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
        Log.w(TAG, "REPLY_SCOPE inactive; rebuilding replay scope")
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
    private val simConfig = DEFAULT_SIM_CONFIG
    private val sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)

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
                Log.i(TAG, "REPLY_LOAD Loaded IGC file ${displayName ?: uri} with ${log.points.size} raw points (qnh=${log.metadata.qnhHpa})")
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
            Log.e(TAG, "Failed to load IGC file ${displayName ?: uri}", t)
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
                Log.i(TAG, "REPLY_LOAD Loaded IGC asset $assetPath with ${log.points.size} raw points (qnh=${log.metadata.qnhHpa})")
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
            Log.e(TAG, "Failed to load IGC asset $assetPath", t)
            _events.tryEmit(ReplayEvent.Failed(t))
            throw t
        }
    }

    fun play() {
        ensureReplayPipelineActive()
        Log.i(TAG, "REPLY_PLAY entry scopeActive=${scope.isActive} points=${points.size} jobActive=${replayJob != null}")
        scope.launch {
            Log.i(TAG, "REPLY_PLAY request status=${_session.value.status} points=${points.size} currentIndex=$currentIndex")
            if (points.isEmpty()) {
                Log.w(TAG, "Play requested but no points are loaded")
                return@launch
            }
            if (_session.value.status == SessionStatus.PLAYING) return@launch
            if (currentIndex >= points.size) {
                currentIndex = 0
            }
            flightDataRepository.setActiveSource(FlightDataRepository.Source.REPLAY)
            Log.d(TAG, "REPLY_PLAY start currentIndex=$currentIndex pts=${points.size}")
            suspendSensors()
            cancelReplayJob()
            replayJob = launch {
                Log.i(TAG, "REPLY_PLAY start idx=$currentIndex total=${points.size}")
                _session.update { it.copy(status = SessionStatus.PLAYING) }
                try {
                    while (currentIndex < points.size && isActive) {
                        val point = points[currentIndex]
                        val previous = points.getOrNull(currentIndex - 1)
                        sampleEmitter.emitSample(point, previous, _session.value.qnhHpa, _session.value.startTimestampMillis, replayFusionRepository)
                        updateProgress(point.timestampMillis)
                        Log.d(TAG, "REPLY_FRAME idx=$currentIndex ts=${point.timestampMillis} alt=${point.pressureAltitude ?: point.gpsAltitude} speed=${_session.value.speedMultiplier}")

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
                    Log.w(TAG, "REPLY_PLAY cancelled", c)
                    throw c
                } catch (t: Throwable) {
                    Log.e(TAG, "REPLY_PLAY error", t)
                    _events.tryEmit(ReplayEvent.Failed(t))
                } finally {
                    Log.i(TAG, "REPLY_PLAY end status=${_session.value.status} idx=$currentIndex")
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

    fun stop() {
        ensureScopeActive()
        scope.launch {
            Log.i(TAG, "REPLY_STOP request")
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
            Log.i(TAG, "REPLY_SEEK request progress=$progress pts=${pts.size}")
            if (pts.isEmpty()) {
                _events.tryEmit(ReplayEvent.Failed(IllegalStateException("No points loaded")))
                return@launch
            }
            flightDataRepository.setActiveSource(FlightDataRepository.Source.REPLAY)
            val clamped = progress.coerceIn(0f, 1f)
            val targetIndex = (clamped * (pts.size - 1)).toInt().coerceIn(0, pts.lastIndex)
            currentIndex = targetIndex
            val point = pts[targetIndex]
            val previous = pts.getOrNull(targetIndex - 1)
            Log.i(TAG, "REPLY_SEEK progress=$clamped index=$targetIndex ts=${point.timestampMillis}")

            // Update session immediately so UI reflects the new position even if play is paused
            _session.update { state ->
                if (state.selection == null) state else
                    state.copy(currentTimestampMillis = point.timestampMillis)
            }

            runCatching {
                // Seeking is a teleport: reset fusion state so thermal/circling/wind estimators don't
                // interpret the jump as a single "mega-sample" or carry stale altitude baselines.
                replayFusionRepository?.stop() ?: return@runCatching
                sampleEmitter.emitSample(point, previous, _session.value.qnhHpa, _session.value.startTimestampMillis, replayFusionRepository)
                updateProgress(point.timestampMillis)
                if (_session.value.status == SessionStatus.PLAYING) {
                    cancelReplayJob()
                    _session.update { it.copy(status = SessionStatus.PAUSED) }
                    play()
                }
            }.onFailure { t ->
                if (t !is CancellationException) {
                    Log.e(TAG, "Seek failed", t)
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
        Log.i(TAG, "REPLY_PROGRESS elapsed=${elapsed}ms progress=${"%.3f".format(frac)} status=${s.status}")
    }

    private suspend fun finishReplay() {
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
        _events.emit(ReplayEvent.Completed(points.size))
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

    private fun resumeSensors() {
        if (sensorsSuspended) {
            sensorsSuspended = false
            varioServiceManager.start()
        }
    }

    private fun silenceReplayAudio(reason: String) {
        val repo = replayFusionRepository ?: return
        Log.i(TAG, "REPLAY_AUDIO silence reason=$reason")
        repo.stop()
    }


    companion object {
        private const val TAG = "IgcReplayController"
        private const val MIN_FRAME_INTERVAL_MS = 1L  // allow sub-second replay cadence
        private const val MAX_SPEED = 20.0
        private const val ASSET_URI_PREFIX = "asset:///"
        private val DEFAULT_SIM_CONFIG = ReplaySimConfig()
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
        Log.i(TAG, "REPLY_SESSION selection=${selection.displayName ?: selection.uri} durationMs=$duration start=$start")

        sampleEmitter.emitSample(points.first(), null, qnh, start, replayFusionRepository)
    }
}

