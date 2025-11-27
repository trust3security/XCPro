package com.example.xcpro.weather.wind.data

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.weather.wind.domain.CirclingWind
import com.example.xcpro.weather.wind.domain.CirclingWindSample
import com.example.xcpro.weather.wind.domain.WindEkfGlue
import com.example.xcpro.weather.wind.domain.WindStore
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindVector
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
import kotlinx.coroutines.launch

@Singleton
class WindRepository @Inject constructor(
    private val flightDataRepository: FlightDataRepository,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val circlingWind = CirclingWind()
    private val windEkf = WindEkfGlue()
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
            flightDataRepository.flightData.collectLatest { data ->
                if (data == null) {
                    handleNoData()
                } else {
                    processSample(data)
                }
            }
        }
    }

    private fun handleNoData() {
        circlingWind.reset()
        windEkf.reset()
        windStore.reset()
        lastTrackRad = null
        lastTrackTimestamp = 0L
        lastSampleTimestamp = 0L
        isCircling = false
        circlingAccumulatorMs = 0.0
        _windState.value = WindState()
    }

    private fun processSample(data: CompleteFlightData) {
        val gps = data.gps ?: return
        val trackRad = Math.toRadians(gps.bearing)
        val turnRate = computeTurnRate(trackRad, gps.timestamp)
        val instantCircling = turnRate != null &&
            abs(turnRate) >= MIN_TURN_RATE &&
            gps.speed.value >= MIN_GROUND_SPEED
        val sustainedCircling = updateCirclingState(instantCircling, gps.timestamp)

        val estimatorCircling = sustainedCircling || instantCircling
        val result = circlingWind.addSample(
            CirclingWindSample(
                timestampMillis = gps.timestamp,
                trackRad = trackRad,
                groundSpeed = gps.speed.value,
                isCircling = estimatorCircling
            )
        )

        if (result != null) {
            lastCirclingTimestamp = result.timestampMillis
            windStore.slotMeasurement(
                timestampMillis = result.timestampMillis,
                altitudeMeters = altitudeFromSample(data),
                vector = result.windVector,
                quality = result.quality,
                source = WindSource.CIRCLING,
                clearExisting = true
            )
            publishWindState(
                vector = result.windVector,
                source = WindSource.CIRCLING,
                quality = result.quality,
                headingDeg = data.effectiveHeading,
                timestamp = result.timestampMillis
            )
            return
        }

        val ekfResult = windEkf.update(data, estimatorCircling, turnRate)
        val canUseEkf = lastCirclingTimestamp == Long.MIN_VALUE ||
            ekfResult?.timestampMillis?.minus(lastCirclingTimestamp) ?: Long.MAX_VALUE > CIRCLING_SUPPRESSION_MS

        if (ekfResult != null && canUseEkf) {
            val boostedQuality = (ekfResult.quality + EKF_QUALITY_BONUS).coerceAtMost(MAX_MEASUREMENT_QUALITY)
            windStore.slotMeasurement(
                timestampMillis = ekfResult.timestampMillis,
                altitudeMeters = altitudeFromSample(data),
                vector = ekfResult.windVector,
                quality = boostedQuality,
                source = WindSource.EKF
            )
        }

        val evaluated = windStore.evaluate(data.timestamp, data.baroAltitude.value)
        if (evaluated != null && data.timestamp - evaluated.timestampMillis <= STALE_MS) {
            publishWindState(
                vector = evaluated.vector,
                source = evaluated.source,
                quality = evaluated.quality,
                headingDeg = data.effectiveHeading,
                timestamp = evaluated.timestampMillis
            )
        } else if (evaluated == null || data.timestamp - (evaluated?.timestampMillis ?: 0L) > STALE_MS) {
            _windState.value = WindState()
        }
    }

    private fun altitudeFromSample(data: CompleteFlightData): Double {
        return if (data.baroAltitude.value.isFinite() && data.baroAltitude.value != 0.0) {
            data.baroAltitude.value
        } else {
            data.gps?.altitude?.value ?: 0.0
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
        timestamp: Long
    ) {
        val (head, cross) = computeHeadAndCross(vector, headingDeg)
        _windState.value = WindState(
            vector = vector,
            source = source,
            quality = quality,
            headwind = head,
            crosswind = cross,
            lastUpdatedMillis = timestamp
        )
    }

    companion object {
        private const val MIN_GROUND_SPEED = 8.0  // m/s
        private const val MIN_TURN_RATE = 0.15    // rad/s (~8.6 deg/s)
        private const val ENTER_THRESHOLD_MS = 3_500.0
        private const val EXIT_THRESHOLD_MS = 1_500.0
        private const val MAX_ACCUM_MS = 8_000.0
        private const val EKF_QUALITY_BONUS = 1
        private const val MAX_MEASUREMENT_QUALITY = 5
        private const val CIRCLING_SUPPRESSION_MS = 5_000L
        private const val STALE_MS = 120_000L
    }
}
