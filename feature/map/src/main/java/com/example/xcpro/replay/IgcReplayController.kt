package com.example.xcpro.replay

import android.content.Context
import android.net.Uri
import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.replay.IgcParser.parse
import com.example.xcpro.sensors.FlightDataCalculator
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.weather.wind.data.WindRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
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
    @DefaultDispatcher dispatcher: CoroutineDispatcher
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

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var replayJob: Job? = null
    private var seekJob: Job? = null
    private var points: List<IgcPoint> = emptyList()
    private var currentIndex = 0
    private var sensorsSuspended = false

    private val replaySensorSource = ReplaySensorSource()
    private val replayFusionRepository: SensorFusionRepository = FlightDataCalculator(
        context = appContext,
        sensorDataSource = replaySensorSource,
        scope = scope,
        sinkProvider = sinkProvider,
        windStateFlow = windRepository.windState,
        enableAudio = true,
        isReplayMode = true
    )
    private var lastReplayHeadingDeg: Float? = null

    private val _session = MutableStateFlow(SessionState())
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _events = MutableSharedFlow<ReplayEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ReplayEvent> = _events.asSharedFlow()

    init {
        scope.launch {
            replayFusionRepository.flightDataFlow.collect { data ->
                if (_session.value.status != SessionStatus.IDLE) {
                    flightDataRepository.update(data)
                }
            }
        }
    }

    suspend fun loadFile(uri: Uri, displayName: String?) {
        withContext(scope.coroutineContext) {
            val log = loadLog(uri)
            prepareSession(
                log = log,
                selection = Selection(uri, displayName)
            )
        }
    }

    suspend fun loadAsset(assetPath: String, displayName: String? = null) {
        withContext(scope.coroutineContext) {
            val log = loadAssetLog(assetPath)
            val name = displayName ?: assetPath.substringAfterLast('/')
            val uri = Uri.parse("$ASSET_URI_PREFIX$assetPath")
            prepareSession(
                log = log,
                selection = Selection(uri, name)
            )
        }
    }

    fun play() {
        scope.launch {
            if (points.isEmpty()) return@launch
            if (_session.value.status == SessionStatus.PLAYING) return@launch
            if (currentIndex >= points.size) {
                currentIndex = 0
            }
            suspendSensors()
            cancelReplayJob()
            replayJob = launch {
                _session.update { it.copy(status = SessionStatus.PLAYING) }
                while (currentIndex < points.size) {
                    val point = points[currentIndex]
                    val previous = points.getOrNull(currentIndex - 1)
                    emitSample(point, previous, _session.value.qnhHpa)
                    updateProgress(point.timestampMillis)

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
                    try {
                        delay(delayMillis)
                    } catch (c: CancellationException) {
                        return@launch
                    }
                }
            }
        }
    }

    fun pause() {
        scope.launch {
            if (_session.value.status != SessionStatus.PLAYING) return@launch
            cancelReplayJob()
            _session.update { it.copy(status = SessionStatus.PAUSED) }
        }
    }

    fun stop() {
        scope.launch {
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
        seekJob?.cancel()
        seekJob = scope.launch {
            val pts = points
            if (pts.isEmpty()) return@launch
            val clamped = progress.coerceIn(0f, 1f)
            val targetIndex = (clamped * (pts.size - 1)).toInt().coerceIn(0, pts.lastIndex)
            currentIndex = targetIndex
            val point = pts[targetIndex]
            val previous = pts.getOrNull(targetIndex - 1)
            emitSample(point, previous, _session.value.qnhHpa)
            updateProgress(point.timestampMillis)
            if (_session.value.status == SessionStatus.PLAYING) {
                cancelReplayJob()
                play()
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
        private const val EARTH_RADIUS_M = 6_371_000.0
        private const val MIN_FRAME_INTERVAL_MS = 200L
        private const val INTERPOLATION_STEP_MS = 1_000L
        private const val DEFAULT_SPEED = 30.0
        private const val MAX_SPEED = 60.0
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
}

