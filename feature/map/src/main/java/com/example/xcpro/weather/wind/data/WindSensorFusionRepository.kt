package com.example.xcpro.weather.wind.data

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.di.LiveSource
import com.example.xcpro.di.ReplaySource
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.CirclingDetector
import com.example.xcpro.weather.wind.domain.CirclingWind
import com.example.xcpro.weather.wind.domain.CirclingWindSample
import com.example.xcpro.weather.wind.domain.WindCandidate
import com.example.xcpro.weather.wind.domain.WindEkfUseCase
import com.example.xcpro.weather.wind.domain.WindSelectionUseCase
import com.example.xcpro.weather.wind.domain.WindStore
import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GLoadSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.HeadingSample
import com.example.xcpro.weather.wind.model.PressureSample
import com.example.xcpro.weather.wind.model.WindOverride
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.weather.wind.model.WindVector
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
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
    private val flightStateSource: FlightStateSource,
    private val windOverrideSource: WindOverrideSource,
    private val windSelectionUseCase: WindSelectionUseCase,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) {

    private data class WindFusionInput(
        val gps: GpsSample?,
        val pressure: PressureSample?,
        val airspeed: AirspeedSample?,
        val heading: HeadingSample?,
        val gLoad: GLoadSample?,
        val manualWind: WindOverride?,
        val externalWind: WindOverride?
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val circlingDetector = CirclingDetector()
    private val circlingWind = CirclingWind()
    private val windEkf = WindEkfUseCase()
    private val windStore = WindStore()

    private val _windState = MutableStateFlow(WindState())
    val windState: StateFlow<WindState> = _windState.asStateFlow()

    private var lastCirclingClockMillis = Long.MIN_VALUE
    private var lastGpsClockMillis = Long.MIN_VALUE
    private var lastUpdatedClockMillis = Long.MIN_VALUE

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
                    val sensorInputs = combine(
                        inputs.gps,
                        inputs.pressure,
                        inputs.airspeed,
                        inputs.heading,
                        inputs.gLoad
                    ) { gps, pressure, airspeed, heading, gLoad ->
                        WindFusionInput(
                            gps = gps,
                            pressure = pressure,
                            airspeed = airspeed,
                            heading = heading,
                            gLoad = gLoad,
                            manualWind = null,
                            externalWind = null
                        )
                    }
                    combine(
                        sensorInputs,
                        windOverrideSource.manualWind,
                        windOverrideSource.externalWind
                    ) { input, manual, external ->
                        input.copy(
                            manualWind = manual,
                            externalWind = external
                        )
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
        circlingDetector.reset()
        circlingWind.reset()
        windEkf.reset()
        windStore.reset()
        lastCirclingClockMillis = Long.MIN_VALUE
        lastGpsClockMillis = Long.MIN_VALUE
        lastUpdatedClockMillis = Long.MIN_VALUE
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
        val gpsClockMillis = gps.clockMillis
        if (gpsClockMillis <= lastGpsClockMillis) {
            return
        }
        lastGpsClockMillis = gpsClockMillis
        val gpsWallMillis = gps.timestampMillis
        val trackRad = gps.trackRad
        val isFlying = flightStateSource.flightState.value.isFlying
        val circlingDecision = circlingDetector.update(
            trackDegrees = Math.toDegrees(trackRad),
            timestampMillis = gpsClockMillis,
            isFlying = isFlying
        )
        val isCircling = circlingDecision.isCircling
        val result = circlingWind.addSample(
            CirclingWindSample(
                clockMillis = gpsClockMillis,
                trackRad = trackRad,
                groundSpeed = gps.groundSpeedMs,
                isCircling = isCircling
            )
        )

        val altitudeMeters = altitudeFromSample(gps, input.pressure)
        val headingDeg = headingFromSample(gps, input.heading)

        if (result != null) {
            lastCirclingClockMillis = result.clockMillis
            windStore.slotMeasurement(
                clockMillis = result.clockMillis,
                timestampMillis = gpsWallMillis,
                altitudeMeters = altitudeMeters,
                vector = result.windVector,
                quality = result.quality,
                source = WindSource.CIRCLING
            )
        }

        val ekfResult = if (isFlying) {
            windEkf.update(
                gps = gps,
                airspeed = input.airspeed,
                isCircling = isCircling,
                turnRateRad = circlingDecision.turnRateRad,
                gLoad = input.gLoad
            )
        } else {
            windEkf.reset()
            null
        }
        val canUseEkf = lastCirclingClockMillis == Long.MIN_VALUE ||
            ((ekfResult?.clockMillis?.minus(lastCirclingClockMillis)) ?: Long.MAX_VALUE) > CIRCLING_SUPPRESSION_MS

        // AI-NOTE: EKF direct-use mirrors XCSoar behavior: publish the freshest straight-flight wind
        // when real airspeed is available, while still storing the measurement for history/weighting.
        // Override selection still applies (AUTO newer-than-manual -> EXTERNAL -> MANUAL).
        var ekfCandidateClockMillis: Long? = null
        val ekfCandidate = if (ekfResult != null && canUseEkf) {
            val boostedQuality = (ekfResult.quality + EKF_QUALITY_BONUS).coerceAtMost(MAX_MEASUREMENT_QUALITY)
            windStore.slotMeasurement(
                clockMillis = ekfResult.clockMillis,
                timestampMillis = gpsWallMillis,
                altitudeMeters = altitudeMeters,
                vector = ekfResult.windVector,
                quality = boostedQuality,
                source = WindSource.EKF
            )
            ekfCandidateClockMillis = ekfResult.clockMillis
            WindCandidate(
                vector = ekfResult.windVector,
                source = WindSource.EKF,
                quality = boostedQuality,
                timestampMillis = gpsWallMillis
            )
        } else {
            null
        }

        val evaluated = if (ekfCandidate == null) {
            windStore.evaluate(gpsClockMillis, altitudeMeters)
        } else {
            null
        }
        val storeCandidate = evaluated?.let {
            WindCandidate(
                vector = it.vector,
                source = it.source,
                quality = it.quality,
                timestampMillis = it.timestampMillis
            )
        }
        val autoCandidate = ekfCandidate ?: storeCandidate
        val autoClockMillis = ekfCandidateClockMillis ?: evaluated?.clockMillis

        val manualCandidate = input.manualWind?.let { it.toCandidate() }
        val externalCandidate = input.externalWind?.let { it.toCandidate() }

        val autoFresh = autoCandidate != null &&
            autoClockMillis != null &&
            gpsClockMillis >= autoClockMillis &&
            gpsClockMillis - autoClockMillis <= STALE_MS

        val selected = windSelectionUseCase.select(
            auto = autoCandidate?.takeIf { autoFresh },
            manual = manualCandidate,
            external = externalCandidate
        )

        if (selected != null) {
            lastUpdatedClockMillis = if (isAutoSource(selected.source)) {
                autoClockMillis ?: Long.MIN_VALUE
            } else {
                Long.MIN_VALUE
            }
            publishWindState(
                vector = selected.vector,
                source = selected.source,
                quality = selected.quality,
                headingDeg = headingDeg,
                timestamp = selected.timestampMillis,
                stale = false
            )
            return
        }

        val existing = _windState.value
        val age = if (existing.vector != null) {
            if (isAutoSource(existing.source)) {
                if (lastUpdatedClockMillis != Long.MIN_VALUE && gpsClockMillis >= lastUpdatedClockMillis) {
                    gpsClockMillis - lastUpdatedClockMillis
                } else {
                    Long.MAX_VALUE
                }
            } else if (existing.lastUpdatedMillis > 0) {
                (gpsWallMillis - existing.lastUpdatedMillis).coerceAtLeast(0L)
            } else {
                Long.MAX_VALUE
            }
        } else {
            Long.MAX_VALUE
        }
        if (age > STALE_MS) {
            _windState.value = WindState(stale = true)
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

    private fun WindOverride.toCandidate(): WindCandidate = WindCandidate(
        vector = vector,
        source = source,
        quality = quality,
        timestampMillis = timestampMillis
    )

    private fun isAutoSource(source: WindSource): Boolean =
        source == WindSource.CIRCLING || source == WindSource.EKF

    companion object {
        private const val EKF_QUALITY_BONUS = 1
        private const val MAX_MEASUREMENT_QUALITY = 5
        private const val CIRCLING_SUPPRESSION_MS = 5_000L
        private const val STALE_MS = 3_600_000L // 1 hour (soaring-scale glides between wind updates)
    }
}
