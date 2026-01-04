package com.example.xcpro.weather.wind.data

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.SensorDataSource
import com.example.xcpro.weather.wind.domain.CirclingWind
import com.example.xcpro.weather.wind.domain.CirclingWindSample
import com.example.xcpro.weather.wind.domain.WindEkfUseCase
import com.example.xcpro.weather.wind.domain.WindStore
import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.HeadingSample
import com.example.xcpro.weather.wind.model.PressureSample
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.weather.wind.model.WindVector
import com.example.xcpro.di.LiveSource
import com.example.xcpro.di.ReplaySource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Singleton
class WindSensorFusionRepository @Inject constructor(
    @LiveSource private val liveSensorSource: SensorDataSource,
    @ReplaySource private val replaySensorSource: SensorDataSource,
    private val flightDataRepository: FlightDataRepository,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val liveInputs = WindSensorInputAdapter(liveSensorSource, scope)
    private val replayInputs = WindSensorInputAdapter(replaySensorSource, scope)

    private val circlingWind = CirclingWind()
    private val windEkf = WindEkfUseCase()
    private val windStore = WindStore()

    private val _windState = MutableStateFlow(WindState())
    val windState: StateFlow<WindState> = _windState.asStateFlow()

    private var lastTrackRad: Double? = null
    private var lastTrackTimestamp: Long = 0L
    private var lastSampleTimestamp: Long = 0L
    private var circlingAccumulatorMs = 0.0
    private var isCircling = false
    private var lastCirclingTimestamp = Long.MIN_VALUE

    init {
        scope.launch {
            flightDataRepository.activeSource
                .collectLatest { source ->
                    resetFusionState()
                    val inputs = when (source) {
                        FlightDataRepository.Source.LIVE -> liveInputs
                        FlightDataRepository.Source.REPLAY -> replayInputs
                    }
                    inputs.gps.collectLatest { gps ->
                        if (gps == null) {
                            handleNoData()
                            return@collectLatest
                        }
                        processSample(
                            gps = gps,
                            pressure = inputs.pressure.value,
                            airspeed = inputs.airspeed.value,
                            heading = inputs.heading.value
                        )
                    }
                }
        }
    }

    private fun handleNoData() {
        resetFusionState()
    }

    private fun resetFusionState() {
        circlingWind.reset()
        windEkf.reset()
        windStore.reset()
        lastTrackRad = null
        lastTrackTimestamp = 0L
        lastSampleTimestamp = 0L
        circlingAccumulatorMs = 0.0
        isCircling = false
        lastCirclingTimestamp = Long.MIN_VALUE
        _windState.value = WindState()
    }

    private fun processSample(
        gps: GpsSample,
        pressure: PressureSample?,
        airspeed: AirspeedSample?,
        heading: HeadingSample?
    ) {
        val trackRad = gps.trackRad
        val turnRate = computeTurnRate(trackRad, gps.timestampMillis)
        val instantCircling = turnRate != null &&
            abs(turnRate) >= MIN_TURN_RATE &&
            gps.groundSpeedMs >= MIN_GROUND_SPEED
        val sustainedCircling = updateCirclingState(instantCircling, gps.timestampMillis)

        val estimatorCircling = sustainedCircling || instantCircling
        val circlingResult = circlingWind.addSample(
            CirclingWindSample(
                timestampMillis = gps.timestampMillis,
                trackRad = trackRad,
                groundSpeed = gps.groundSpeedMs,
                isCircling = estimatorCircling
            )
        )

        if (circlingResult != null) {
            lastCirclingTimestamp = circlingResult.timestampMillis
            val altitudeMeters = altitudeFromSample(gps, pressure)
            windStore.slotMeasurement(
                timestampMillis = circlingResult.timestampMillis,
                altitudeMeters = altitudeMeters,
                vector = circlingResult.windVector,
                quality = circlingResult.quality,
                source = WindSource.CIRCLING,
                clearExisting = true
            )
            publishWindState(
                vector = circlingResult.windVector,
                source = WindSource.CIRCLING,
                quality = circlingResult.quality,
                headingDeg = resolveHeadingDeg(gps, heading),
                timestamp = circlingResult.timestampMillis,
                currentTimeMillis = gps.timestampMillis
            )
            return
        }

        val ekfResult = windEkf.update(
            gps = gps,
            airspeed = airspeed?.takeIf { it.isValid },
            isCircling = estimatorCircling,
            turnRateRad = turnRate
        )
        val canUseEkf = lastCirclingTimestamp == Long.MIN_VALUE ||
            ekfResult?.timestampMillis?.minus(lastCirclingTimestamp) ?: Long.MAX_VALUE > CIRCLING_SUPPRESSION_MS

        if (ekfResult != null && canUseEkf) {
            val altitudeMeters = altitudeFromSample(gps, pressure)
            windStore.slotMeasurement(
                timestampMillis = ekfResult.timestampMillis,
                altitudeMeters = altitudeMeters,
                vector = ekfResult.windVector,
                quality = ekfResult.quality,
                source = WindSource.EKF
            )
            publishWindState(
                vector = ekfResult.windVector,
                source = WindSource.EKF,
                quality = ekfResult.quality,
                headingDeg = resolveHeadingDeg(gps, heading),
                timestamp = ekfResult.timestampMillis,
                currentTimeMillis = gps.timestampMillis
            )
            return
        }

        val altitudeMeters = altitudeFromSample(gps, pressure)
        val evaluated = windStore.evaluate(gps.timestampMillis, altitudeMeters)
        val existing = _windState.value
        when {
            evaluated != null -> {
                if (gps.timestampMillis - evaluated.timestampMillis <= STALE_MS) {
                    publishWindState(
                        vector = evaluated.vector,
                        source = evaluated.source,
                        quality = evaluated.quality,
                        headingDeg = resolveHeadingDeg(gps, heading),
                        timestamp = evaluated.timestampMillis,
                        currentTimeMillis = gps.timestampMillis
                    )
                } else {
                    markStale(existing, gps.timestampMillis)
                }
            }
            evaluated == null -> {
                markStale(existing, gps.timestampMillis)
            }
        }
    }

    private fun altitudeFromSample(gps: GpsSample, pressure: PressureSample?): Double {
        val pressureAlt = pressure?.pressureAltitudeMeters
        return if (pressureAlt != null && pressureAlt.isFinite() && pressureAlt != 0.0) {
            pressureAlt
        } else {
            gps.altitudeMeters
        }
    }

    private fun resolveHeadingDeg(gps: GpsSample, heading: HeadingSample?): Double {
        return when {
            heading?.isValid == true -> heading.headingDeg
            gps.trackDeg.isFinite() -> gps.trackDeg
            else -> 0.0
        }
    }

    private fun computeTurnRate(trackRad: Double, timestamp: Long): Double? {
        val previousTrack = lastTrackRad
        val previousTime = lastTrackTimestamp
        lastTrackRad = trackRad
        lastTrackTimestamp = timestamp
        if (previousTrack == null || previousTime == 0L) {
            return null
        }
        val dt = (timestamp - previousTime) / 1000.0
        if (dt <= 0.01) return null
        var delta = trackRad - previousTrack
        while (delta <= -PI) delta += 2 * PI
        while (delta > PI) delta -= 2 * PI
        return delta / dt
    }

    private fun updateCirclingState(instant: Boolean, timestamp: Long): Boolean {
        val previous = lastSampleTimestamp
        lastSampleTimestamp = timestamp
        val delta = if (previous == 0L) 0.0 else max(0L, timestamp - previous).toDouble()
        circlingAccumulatorMs = if (instant) {
            (circlingAccumulatorMs + delta).coerceAtMost(MAX_ACCUM_MS)
        } else {
            (circlingAccumulatorMs - delta).coerceAtLeast(0.0)
        }

        isCircling = if (isCircling) {
            circlingAccumulatorMs >= EXIT_THRESHOLD_MS
        } else {
            circlingAccumulatorMs >= ENTER_THRESHOLD_MS
        }
        return isCircling
    }

    private fun computeHeadAndCross(wind: WindVector, headingDeg: Double): Pair<Double, Double> {
        val headingRad = Math.toRadians(headingDeg)
        val sinH = sin(headingRad)
        val cosH = cos(headingRad)

        val dot = wind.east * sinH + wind.north * cosH
        val headwind = -dot

        val crosswind = wind.east * cosH - wind.north * sinH

        return headwind to crosswind
    }

    private fun publishWindState(
        vector: WindVector,
        source: WindSource,
        quality: Int,
        headingDeg: Double,
        timestamp: Long,
        currentTimeMillis: Long
    ) {
        val stale = currentTimeMillis - timestamp > STALE_MS
        if (stale) {
            _windState.value = WindState(
                lastUpdatedMillis = timestamp,
                stale = true
            )
            return
        }

        val (head, cross) = computeHeadAndCross(vector, headingDeg)
        _windState.value = WindState(
            vector = vector,
            source = source,
            quality = quality,
            headwind = head,
            crosswind = cross,
            lastUpdatedMillis = timestamp,
            stale = false
        )
    }

    private fun markStale(existing: WindState, currentTimeMillis: Long) {
        if (existing.lastUpdatedMillis <= 0L) return
        val age = currentTimeMillis - existing.lastUpdatedMillis
        if (age > STALE_MS) {
            _windState.value = WindState(
                lastUpdatedMillis = existing.lastUpdatedMillis,
                stale = true
            )
        }
    }

    companion object {
        private const val MIN_GROUND_SPEED = 8.0  // m/s
        private const val MIN_TURN_RATE = 0.15    // rad/s (~8.6 deg/s)
        private const val ENTER_THRESHOLD_MS = 3_500.0
        private const val EXIT_THRESHOLD_MS = 1_500.0
        private const val MAX_ACCUM_MS = 8_000.0
        private const val CIRCLING_SUPPRESSION_MS = 5_000L
        private const val STALE_MS = 3_600_000L // 1 hour
    }
}


