package com.example.xcpro.replay

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.FlightDataCalculator
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.weather.wind.data.WindRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
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

@Singleton
class IgcReplayController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val flightDataRepository: FlightDataRepository,
    private val varioServiceManager: VarioServiceManager,
    private val sinkProvider: StillAirSinkProvider,
    private val windRepository: WindRepository,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {

    data class SessionState(
        val selection: Selection? = null,
        val status: SessionStatus = SessionStatus.IDLE,
        val speedMultiplier: Double = DEFAULT_SPEED,
        val startTimestampMillis: Long = 0L,
        val currentTimestampMillis: Long = 0L,
        val durationMillis: Long = 0L,
        val qnhHpa: Double = DEFAULT_QNH_HPA
    ) {
        val hasSelection: Boolean get() = selection != null
        val elapsedMillis: Long get() = (currentTimestampMillis - startTimestampMillis).coerceAtLeast(0L)
        val progressFraction: Float
            get() = if (durationMillis <= 0L) 0f else (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    }

    data class Selection(val uri: Uri, val displayName: String?)

    enum class SessionStatus { IDLE, PAUSED, PLAYING }

    sealed interface ReplayEvent {
        data class Completed(val samples: Int) : ReplayEvent
        data class Failed(val throwable: Throwable) : ReplayEvent
        object Cancelled : ReplayEvent
    }

    private fun createScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    private fun createFusionRepository(): SensorFusionRepository =
        FlightDataCalculator(
            context = appContext,
            sensorDataSource = replaySensorSource,
            scope = scope,
            sinkProvider = sinkProvider,
            windStateFlow = windRepository.windState,
            enableAudio = true,
            isReplayMode = true
        )

    private fun startForwardingFlightData() {
        val repo = replayFusionRepository ?: return
        forwardJob?.cancel()
        forwardJob = scope.launch {
            repo.flightDataFlow.collect { data ->
                if (_session.value.status != SessionStatus.IDLE) {
                    val now = System.currentTimeMillis()
                    if (now - lastForwardLogTime >= 1_000L) {
                        lastForwardLogTime = now
                        val gps = data?.gps
                        val verticalSpeed = data?.verticalSpeed?.value
                        val displayVario = data?.displayVario?.value
                        val xcSoarDisplayVario = data?.xcSoarDisplayVario?.value
                        val tc30 = data?.thermalAverage?.value
                        val tcAvg = data?.thermalAverageCircle?.value
                        val tAvg = data?.thermalAverageTotal?.value
                        Log.d(
                            TAG,
                            "REPLY_FORWARD gps=${gps?.latLng?.latitude},${gps?.latLng?.longitude} " +
                                "gs=${gps?.speed?.value} alt=${gps?.altitude?.value} " +
                                "v=${verticalSpeed} dv=${displayVario} xc=${xcSoarDisplayVario} " +
                                "tc30=${tc30} tcAvg=${tcAvg} tAvg=${tAvg} tValid=${data?.currentThermalValid} " +
                                "circling=${data?.isCircling} windQ=${data?.windQuality} wind=${data?.windSpeed?.value}"
                        )
                    }
                    flightDataRepository.update(data)
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

    private val replaySensorSource = ReplaySensorSource()
    private var replayFusionRepository: SensorFusionRepository? = null
    private var lastReplayHeadingDeg: Float? = null

    private val _session = MutableStateFlow(SessionState())
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _events = MutableSharedFlow<ReplayEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ReplayEvent> = _events.asSharedFlow()

    suspend fun loadFile(uri: Uri, displayName: String?) {
        ensureReplayPipelineActive()
        var failure: Throwable? = null
        withContext(scope.coroutineContext) {
            try {
                val log = loadLog(uri)
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
                val log = loadAssetLog(assetPath)
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
                        emitSample(point, previous, _session.value.qnhHpa)
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
            flightDataRepository.update(null)
            replayFusionRepository?.resetQnhToStandard()
            resumeSensors()
            points = emptyList()
            currentIndex = 0
            lastReplayHeadingDeg = null
            _session.value = SessionState(speedMultiplier = _session.value.speedMultiplier)
            _events.tryEmit(ReplayEvent.Cancelled)
        }
    }

    fun setSpeed(multiplier: Double) {
        val clamped = multiplier.coerceIn(1.0, MAX_SPEED)
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
                emitSample(point, previous, _session.value.qnhHpa)
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

    private fun emitSample(current: IgcPoint, previous: IgcPoint?, qnhHpa: Double) {
        val movement = IgcReplayMath.groundVector(current, previous)
        val groundSpeed = movement.speedMs
        val trackDeg = resolveReplayHeading(movement)
        val gpsAltitude = current.gpsAltitude

        val pressureAltitude = current.pressureAltitude ?: gpsAltitude
        val pressureHPa = IgcReplayMath.altitudeToPressure(pressureAltitude, qnhHpa)
        replaySensorSource.emitBaro(pressureHPa = pressureHPa, timestamp = current.timestampMillis)
        replaySensorSource.emitGps(
            latitude = current.latitude,
            longitude = current.longitude,
            altitude = gpsAltitude,
            speed = groundSpeed,
            bearing = trackDeg.toDouble(),
            accuracy = 5f,
            timestamp = current.timestampMillis
        )
        replaySensorSource.emitCompass(
            heading = trackDeg.toDouble(),
            accuracy = 3,
            timestamp = current.timestampMillis
        )
        val igcVario = IgcReplayMath.verticalSpeed(current, previous)
        replayFusionRepository?.updateReplayRealVario(igcVario)
    }

    private fun resolveReplayHeading(movement: MovementSnapshot): Float {
        val derivedHeading = movement.bearingDeg
        val previousHeading = lastReplayHeadingDeg
        val shouldReusePrevious = movement.distanceMeters < MIN_REPLAY_HEADING_DISTANCE_M ||
            movement.speedMs < MIN_REPLAY_HEADING_SPEED_MS

        val nextHeading = when {
            previousHeading == null -> derivedHeading
            shouldReusePrevious -> previousHeading
            // Preserve raw turn-rate so circling/wind detectors behave like live GPS.
            // Camera/icon smoothing happens downstream (MapPositionController bearing clamp).
            else -> derivedHeading
        }

        lastReplayHeadingDeg = nextHeading
        return nextHeading
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
        _session.update { it.copy(status = SessionStatus.PAUSED) }
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

    companion object {
        private const val TAG = "IgcReplayController"
        private const val MIN_FRAME_INTERVAL_MS = 1_000L  // raw IGC cadence (~1 Hz)
        private const val DEFAULT_SPEED = 1.0
        private const val MAX_SPEED = 20.0
        private const val DEFAULT_QNH_HPA = 1013.3
        private const val ASSET_URI_PREFIX = "asset:///"
        private const val MIN_REPLAY_HEADING_SPEED_MS = 1.0
        private const val MIN_REPLAY_HEADING_DISTANCE_M = 3.0
    }

    private fun prepareSession(log: IgcLog, selection: Selection) {
        val densified = IgcReplayMath.densifyPoints(log.points)
        if (densified.isEmpty()) throw IllegalArgumentException("IGC file has no B records")
        cancelReplayJob()
        seekJob?.cancel()
        seekJob = null
        points = densified
        currentIndex = 0
        suspendSensors()
        replaySensorSource.reset()
        val qnh = log.metadata.qnhHpa ?: DEFAULT_QNH_HPA
        val start = points.first().timestampMillis
        val duration = (points.last().timestampMillis - start).coerceAtLeast(1L)
        logSessionPrep(selection, points.size, start, points.last().timestampMillis, qnh)
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
        lastReplayHeadingDeg = null
        emitSample(points.first(), null, qnh)
    }

    private fun loadLog(fileUri: Uri): IgcLog =
        appContext.contentResolver.openInputStream(fileUri)?.use { stream ->
            IgcParser.parse(stream)
        } ?: throw IllegalArgumentException("Unable to open IGC file")

    private fun loadAssetLog(assetPath: String): IgcLog =
        appContext.assets.open(assetPath).use { stream ->
            IgcParser.parse(stream)
        }

    private fun logSessionPrep(
        selection: Selection,
        pointCount: Int,
        startMillis: Long,
        endMillis: Long,
        qnh: Double
    ) {
        val startIso = Instant.ofEpochMilli(startMillis).toString()
        val endIso = Instant.ofEpochMilli(endMillis).toString()
        val durationSec = (endMillis - startMillis) / 1000
        Log.i(
            TAG,
            "Prepared replay '${selection.displayName ?: selection.uri}' " +
                "points=$pointCount duration=${durationSec}s start=$startIso end=$endIso qnh=${"%.1f".format(qnh)}"
        )
    }
}
