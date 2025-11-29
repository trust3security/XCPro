package com.example.xcpro.replay

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.replay.IgcParser.parse
import com.example.xcpro.sensors.FlightDataCalculator
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.weather.wind.data.WindRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
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
        forwardJob?.cancel()
        forwardJob = scope.launch {
            replayFusionRepository.flightDataFlow.collect { data ->
                if (_session.value.status != SessionStatus.IDLE) {
                    flightDataRepository.update(data)
                }
            }
        }
    }

    private fun ensureScopeActive() {
        if (scope.isActive) return
        Log.w(TAG, "REPLAY_SCOPE inactive; rebuilding replay pipeline")
        scope = createScope()
        replayFusionRepository = createFusionRepository()
        startForwardingFlightData()
    }

    private var scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var forwardJob: Job? = null
    private var replayJob: Job? = null
    private var seekJob: Job? = null
    private var points: List<IgcPoint> = emptyList()
    private var currentIndex = 0
    private var sensorsSuspended = false

    private val replaySensorSource = ReplaySensorSource()
    private var replayFusionRepository: SensorFusionRepository = createFusionRepository()
    private var lastReplayHeadingDeg: Float? = null

    private val _session = MutableStateFlow(SessionState())
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _events = MutableSharedFlow<ReplayEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ReplayEvent> = _events.asSharedFlow()

    init {
        startForwardingFlightData()
    }

    suspend fun loadFile(uri: Uri, displayName: String?) {
        ensureScopeActive()
        withContext(scope.coroutineContext) {
            runCatching {
                val log = loadLog(uri)
                Log.i(TAG, "REPLY_LOAD Loaded IGC file ${displayName ?: uri} with ${log.points.size} raw points (qnh=${log.metadata.qnhHpa})")
                prepareSession(
                    log = log,
                    selection = Selection(uri, displayName)
                )
                // Auto-start playback to mirror IgcReplaySim behavior and avoid UI timing issues.
                play()
            }.onFailure { t ->
                if (t is CancellationException) return@withContext
                Log.e(TAG, "Failed to load IGC file ${displayName ?: uri}", t)
                _events.tryEmit(ReplayEvent.Failed(t))
            }
        }
    }

    suspend fun loadAsset(assetPath: String, displayName: String? = null) {
        ensureScopeActive()
        withContext(scope.coroutineContext) {
            runCatching {
                val log = loadAssetLog(assetPath)
                val name = displayName ?: assetPath.substringAfterLast('/')
                val uri = Uri.parse("$ASSET_URI_PREFIX$assetPath")
                Log.i(TAG, "REPLY_LOAD Loaded IGC asset $assetPath with ${log.points.size} raw points (qnh=${log.metadata.qnhHpa})")
                prepareSession(
                    log = log,
                    selection = Selection(uri, name)
                )
                play()
            }.onFailure { t ->
                if (t is CancellationException) return@withContext
                Log.e(TAG, "Failed to load IGC asset $assetPath", t)
                _events.tryEmit(ReplayEvent.Failed(t))
            }
        }
    }

    fun play() {
        ensureScopeActive()
        Log.i(TAG, "REPLAY_PLAY entry scopeActive=${scope.isActive} points=${points.size} jobActive=${replayJob != null}")
        scope.launch {
            Log.i(TAG, "REPLAY_PLAY request status=${_session.value.status} points=${points.size} currentIndex=$currentIndex")
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
                Log.i(TAG, "REPLAY_PLAY start idx=$currentIndex total=${points.size}")
                _session.update { it.copy(status = SessionStatus.PLAYING) }
                try {
                    while (currentIndex < points.size && isActive) {
                        val point = points[currentIndex]
                        val previous = points.getOrNull(currentIndex - 1)
                        emitSample(point, previous, _session.value.qnhHpa)
                        updateProgress(point.timestampMillis)
                        Log.d(TAG, "REPLAY_FRAME idx=$currentIndex ts=${point.timestampMillis} alt=${point.pressureAltitude ?: point.gpsAltitude} speed=${_session.value.speedMultiplier}")

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
                    Log.w(TAG, "REPLAY_PLAY cancelled", c)
                    throw c
                } catch (t: Throwable) {
                    Log.e(TAG, "REPLAY_PLAY error", t)
                    _events.tryEmit(ReplayEvent.Failed(t))
                } finally {
                    Log.i(TAG, "REPLAY_PLAY end status=${_session.value.status} idx=$currentIndex")
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
            Log.i(TAG, "REPLAY_STOP request")
            cancelReplayJob()
            seekJob?.cancel()
            seekJob = null
            replaySensorSource.reset()
            flightDataRepository.update(null)
            replayFusionRepository.resetQnhToStandard()
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
                emitSample(point, previous, _session.value.qnhHpa)
                updateProgress(point.timestampMillis)
                if (_session.value.status == SessionStatus.PLAYING) {
                    cancelReplayJob()
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
        val movement = groundVector(current, previous)
        val groundSpeed = movement.speedMs
        val trackDeg = resolveReplayHeading(movement)
        val gpsAltitude = current.gpsAltitude
        replaySensorSource.emitGps(
            latitude = current.latitude,
            longitude = current.longitude,
            altitude = gpsAltitude,
            speed = groundSpeed,
            bearing = trackDeg.toDouble(),
            accuracy = 5f,
            timestamp = current.timestampMillis
        )

        val pressureAltitude = current.pressureAltitude ?: gpsAltitude
        val pressureHPa = altitudeToPressure(pressureAltitude, qnhHpa)
        replaySensorSource.emitBaro(pressureHPa = pressureHPa, timestamp = current.timestampMillis)
        replaySensorSource.emitCompass(
            heading = trackDeg.toDouble(),
            accuracy = 3,
            timestamp = current.timestampMillis
        )
        val igcVario = verticalSpeed(current, previous)
        replayFusionRepository.updateReplayRealVario(igcVario)
    }

    private fun resolveReplayHeading(movement: MovementSnapshot): Float {
        val derivedHeading = movement.bearingDeg
        val previousHeading = lastReplayHeadingDeg
        val shouldReusePrevious = movement.distanceMeters < MIN_REPLAY_HEADING_DISTANCE_M ||
            movement.speedMs < MIN_REPLAY_HEADING_SPEED_MS

        val nextHeading = when {
            previousHeading == null -> derivedHeading
            shouldReusePrevious -> previousHeading
            else -> lerpHeading(previousHeading, derivedHeading, REPLAY_HEADING_SMOOTHING_ALPHA)
        }

        lastReplayHeadingDeg = nextHeading
        return nextHeading
    }

    private fun groundVector(current: IgcPoint, previous: IgcPoint?): MovementSnapshot {
        val prev = previous ?: current
        val distance = haversine(prev.latitude, prev.longitude, current.latitude, current.longitude)
        val dtSeconds = ((current.timestampMillis - prev.timestampMillis) / 1000.0).coerceAtLeast(1.0)
        val speed = distance / dtSeconds
        val bearing = bearing(prev.latitude, prev.longitude, current.latitude, current.longitude)
        val east = speed * sin(Math.toRadians(bearing))
        val north = speed * cos(Math.toRadians(bearing))
        return MovementSnapshot(
            speedMs = speed,
            distanceMeters = distance,
            east = east,
            north = north
        )
    }

    private fun verticalSpeed(current: IgcPoint, previous: IgcPoint?): Double {
        val prevAlt = previous?.pressureAltitude ?: current.pressureAltitude ?: current.gpsAltitude
        val prevTime = previous?.timestampMillis ?: current.timestampMillis
        val dtSeconds = ((current.timestampMillis - prevTime) / 1000.0).coerceAtLeast(1.0)
        val altitude = current.pressureAltitude ?: current.gpsAltitude
        return (altitude - prevAlt) / dtSeconds
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
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

    private data class MovementSnapshot(
        val speedMs: Double,
        val distanceMeters: Double,
        val east: Double,
        val north: Double
    ) {
        val bearingDeg: Float
            get() {
                val angle = (Math.toDegrees(atan2(east, north)) + 360.0) % 360.0
                return angle.toFloat()
            }
    }

    private fun lerpHeading(from: Float, to: Float, alpha: Float): Float {
        val diff = ((to - from + 540f) % 360f) - 180f
        val blended = (from + diff * alpha + 360f) % 360f
        return blended
    }

    companion object {
        private const val TAG = "IgcReplayController"
        private const val EARTH_RADIUS_M = 6_371_000.0
        private const val MIN_FRAME_INTERVAL_MS = 1_000L  // raw IGC cadence (~1 Hz)
        private const val INTERPOLATION_STEP_MS = 1_000L  // keep interpolation aligned to 1s gaps
        private const val DEFAULT_SPEED = 1.0
        private const val MAX_SPEED = 20.0
        private const val DEFAULT_QNH_HPA = 1013.3
        private const val SEA_LEVEL_TEMP_K = 288.15
        private const val LAPSE_RATE_K_PER_M = 0.0065
        private const val EXPONENT = 5.255
        private const val ASSET_URI_PREFIX = "asset:///"
        private const val MIN_REPLAY_HEADING_SPEED_MS = 1.0
        private const val MIN_REPLAY_HEADING_DISTANCE_M = 3.0
        private const val REPLAY_HEADING_SMOOTHING_ALPHA = 0.2f
    }

    private fun prepareSession(log: IgcLog, selection: Selection) {
        cancelReplayJob()
        seekJob?.cancel()
        seekJob = null
        val densified = densifyPoints(log.points)
        if (densified.isEmpty()) throw IllegalArgumentException("IGC file has no B records")
        points = densified
        currentIndex = 0
        suspendSensors()
        replaySensorSource.reset()
        val qnh = log.metadata.qnhHpa ?: DEFAULT_QNH_HPA
        val start = points.first().timestampMillis
        val duration = (points.last().timestampMillis - start).coerceAtLeast(1L)
        logSessionPrep(selection, points.size, start, points.last().timestampMillis, qnh)
        replayFusionRepository.stop() // reset all smoothing/thermal state
        replayFusionRepository.setManualQnh(qnh)
        _session.value = SessionState(
            selection = selection,
            status = SessionStatus.PAUSED,
            speedMultiplier = _session.value.speedMultiplier,
            startTimestampMillis = start,
            currentTimestampMillis = start,
            durationMillis = duration,
            qnhHpa = qnh
        )
        Log.i(TAG, "REPLAY_SESSION selection=${selection.displayName ?: selection.uri} durationMs=$duration start=$start")
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

    private fun densifyPoints(original: List<IgcPoint>): List<IgcPoint> {
        if (original.size < 2) return original
        val result = ArrayList<IgcPoint>(original.size)
        for (i in 0 until original.lastIndex) {
            val current = original[i]
            val next = original[i + 1]
            result += current
            val gap = next.timestampMillis - current.timestampMillis
            if (gap > INTERPOLATION_STEP_MS) {
                var timestamp = current.timestampMillis + INTERPOLATION_STEP_MS
                while (timestamp < next.timestampMillis) {
                    val fraction = ((timestamp - current.timestampMillis).toDouble() / gap.toDouble()).coerceIn(0.0, 1.0)
                    result += interpolatePoint(current, next, timestamp, fraction)
                    timestamp += INTERPOLATION_STEP_MS
                }
            }
        }
        result += original.last()
        return result
    }

    private fun interpolatePoint(
        start: IgcPoint,
        end: IgcPoint,
        timestamp: Long,
        fraction: Double
    ): IgcPoint {
        fun lerp(a: Double, b: Double): Double = a + (b - a) * fraction

        val pressureAltitude = when {
            start.pressureAltitude != null && end.pressureAltitude != null ->
                lerp(start.pressureAltitude, end.pressureAltitude)
            start.pressureAltitude != null -> start.pressureAltitude
            end.pressureAltitude != null -> end.pressureAltitude
            else -> null
        }

        return IgcPoint(
            timestampMillis = timestamp,
            latitude = lerp(start.latitude, end.latitude),
            longitude = lerp(start.longitude, end.longitude),
            gpsAltitude = lerp(start.gpsAltitude, end.gpsAltitude),
            pressureAltitude = pressureAltitude
        )
    }

    private fun altitudeToPressure(altitudeMeters: Double, qnhHpa: Double): Double {
        val ratio = 1 - (LAPSE_RATE_K_PER_M * altitudeMeters) / SEA_LEVEL_TEMP_K
        return qnhHpa * ratio.pow(EXPONENT)
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
