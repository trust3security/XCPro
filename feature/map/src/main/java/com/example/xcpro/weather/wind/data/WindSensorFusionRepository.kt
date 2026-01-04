package com.example.xcpro.weather.wind.data

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.di.LiveSource
import com.example.xcpro.di.ReplaySource
import com.example.xcpro.flightdata.FlightDataRepository
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Singleton
class WindSensorFusionRepository @Inject constructor(
    @LiveSource private val liveInputs: WindSensorInputs,
    @ReplaySource private val replayInputs: WindSensorInputs,
    private val flightDataRepository: FlightDataRepository,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) {

    private data class WindFusionInput(
        val gps: GpsSample?,
        val pressure: PressureSample?,
        val airspeed: AirspeedSample?,
        val heading: HeadingSample?
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

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
                .onEach { resetForSourceSwitch() }
                .flatMapLatest { source ->
                    val inputs = if (source == FlightDataRepository.Source.REPLAY) {
                        replayInputs
                    } else {
                        liveInputs
                    }
                    combine(
                        inputs.gps,
                        inputs.pressure,
                        inputs.airspeed,
                        inputs.heading
                    ) { gps, pressure, airspeed, heading ->
                        WindFusionInput(gps, pressure, airspeed, heading)
                    }
                }
                .collect { input ->
                    if (input.gps == null) {
                        handleNoData()
                    } else {
                        processSample(input)
                    }
                }
        }
    }

    private fun resetForSourceSwitch() {
        circlingWind.reset()
        windEkf.reset()
        windStore.reset()
        lastTrackRad = null
        lastTrackTimestamp = 0L
        lastSampleTimestamp = 0L
        isCircling = false
        circlingAccumulatorMs = 0.0
        lastCirclingTimestamp = Long.MIN_VALUE
        _windState.value = WindState()
    }

    private fun handleNoData() {
        resetForSourceSwitch()
    }

    private fun processSample(input: WindFusionInput) {
        val gps = input.gps ?: return
        if (!gps.trackRad.isFinite() || !gps.groundSpeedMs.isFinite()) {
            return
        }
        val trackRad = gps.trackRad
        val turnRate = computeTurnRate(trackRad, gps.timestampMillis)
        val instantCircling = turnRate != null &&
            abs(turnRate) >= MIN_TURN_RATE &&
            gps.groundSpeedMs >= MIN_GROUND_SPEED
        val sustainedCircling = updateCirclingState(instantCircling, gps.timestampMillis)

        val estimatorCircling = sustainedCircling || instantCircling
        val result = circlingWind.addSample(
            CirclingWindSample(
                timestampMillis = gps.timestampMillis,
                trackRad = trackRad,
                groundSpeed = gps.groundSpeedMs,
                isCircling = estimatorCircling
            )
        )

        val altitudeMeters = altitudeFromSample(gps, input.pressure)
        val headingDeg = headingFromSample(gps, input.heading)

        if (result != null) {
            lastCirclingTimestamp = result.timestampMillis
            windStore.slotMeasurement(
                timestampMillis = result.timestampMillis,
                altitudeMeters = altitudeMeters,
                vector = result.windVector,
                quality = result.quality,
                source = WindSource.CIRCLING,
                clearExisting = true
            )
            publishWindState(
                vector = result.windVector,
                source = WindSource.CIRCLING,
                quality = result.quality,
                headingDeg = headingDeg,
                timestamp = result.timestampMillis,
                stale = false
            )
            return
        }

        val ekfResult = windEkf.update(
            gps = gps,
            airspeed = input.airspeed,
            isCircling = estimatorCircling,
            turnRateRad = turnRate
        )
        val canUseEkf = lastCirclingTimestamp == Long.MIN_VALUE ||
            ekfResult?.timestampMillis?.minus(lastCirclingTimestamp) ?: Long.MAX_VALUE > CIRCLING_SUPPRESSION_MS

        if (ekfResult != null && canUseEkf) {
            val boostedQuality = (ekfResult.quality + EKF_QUALITY_BONUS).coerceAtMost(MAX_MEASUREMENT_QUALITY)
            windStore.slotMeasurement(
                timestampMillis = ekfResult.timestampMillis,
                altitudeMeters = altitudeMeters,
                vector = ekfResult.windVector,
                quality = boostedQuality,
                source = WindSource.EKF
            )
        }

        val evaluated = windStore.evaluate(gps.timestampMillis, altitudeMeters)
        val existing = _windState.value
        when {
            evaluated != null && gps.timestampMillis - evaluated.timestampMillis <= STALE_MS -> publishWindState(
                vector = evaluated.vector,
                source = evaluated.source,
                quality = evaluated.quality,
                headingDeg = headingDeg,
                timestamp = evaluated.timestampMillis,
                stale = false
            )
            evaluated == null -> {
                val age = if (existing.vector != null && existing.lastUpdatedMillis > 0) {
                    gps.timestampMillis - existing.lastUpdatedMillis
                } else {
                    Long.MAX_VALUE
                }
                if (age > STALE_MS) {
                    _windState.value = WindState(stale = true)
                }
            }
            else -> _windState.value = WindState(stale = true)
        }
    }

    private fun altitudeFromSample(gps: GpsSample, pressure: PressureSample?): Double {
        return when {
            pressure != null && pressure.altitudeMeters.isFinite() -> pressure.altitudeMeters
            gps.altitudeMeters.isFinite() -> gps.altitudeMeters
            else -> 0.0
        }
    }

    private fun headingFromSample(gps: GpsSample, heading: HeadingSample?): Double {
        return heading?.headingDeg
            ?: Math.toDegrees(gps.trackRad).takeIf { it.isFinite() }
            ?: 0.0
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
        stale: Boolean
    ) {
        val (head, cross) = computeHeadAndCross(vector, headingDeg)
        _windState.value = WindState(
            vector = vector,
            source = source,
            quality = quality,
            headwind = head,
            crosswind = cross,
            lastUpdatedMillis = timestamp,
            stale = stale
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
        private const val STALE_MS = 3_600_000L // 1 hour (soaring-scale glides between wind updates)
    }
}
