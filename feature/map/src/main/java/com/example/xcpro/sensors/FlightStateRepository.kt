package com.example.xcpro.sensors

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.di.LiveSource
import com.example.xcpro.di.ReplaySource
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.core.time.Clock
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.sensors.domain.FlyingStateDetector
import com.example.xcpro.weather.wind.data.AirspeedDataSource
import com.example.xcpro.weather.wind.model.AirspeedSample
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
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
class FlightStateRepository @Inject constructor(
    @LiveSource private val liveSensors: SensorDataSource,
    @ReplaySource private val replaySensors: SensorDataSource,
    @ReplaySource private val replayAirspeedSource: AirspeedDataSource,
    private val flightDataRepository: FlightDataRepository,
    private val clock: Clock,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
) : FlightStateSource {

    private data class FlightStateInput(
        val gps: GPSData?,
        val baro: BaroData?,
        val airspeed: AirspeedSample?,
        val flightData: CompleteFlightData?,
        val useStabilizedAirspeed: Boolean,
        val aglMeters: Double?
    )

    private val scope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private val detector = FlyingStateDetector()

    private val _flightState = MutableStateFlow(FlyingState())
    override val flightState: StateFlow<FlyingState> = _flightState.asStateFlow()
    private var lastGpsSample: GPSData? = null
    private var lastGpsSeenMonoMs: Long = 0L

    init {
        scope.launch {
            flightDataRepository.activeSource
                .onEach { resetForSourceSwitch() }
                .flatMapLatest { source ->
                    val sensors = if (source == FlightDataRepository.Source.REPLAY) {
                        replaySensors
                    } else {
                        liveSensors
                    }
                    if (source == FlightDataRepository.Source.REPLAY) {
                        combine(
                            sensors.gpsFlow,
                            sensors.baroFlow,
                            replayAirspeedSource.airspeedFlow,
                            flightDataRepository.flightData
                        ) { gps, baro, airspeed, flightData ->
                            val agl = extractFreshAglMeters(flightData)
                            FlightStateInput(
                                gps = gps,
                                baro = baro,
                                airspeed = airspeed,
                                flightData = flightData,
                                useStabilizedAirspeed = false,
                                aglMeters = agl
                            )
                        }
                    } else {
                        combine(
                            sensors.gpsFlow,
                            sensors.baroFlow,
                            flightDataRepository.flightData
                        ) { gps, baro, flightData ->
                            val agl = extractFreshAglMeters(flightData)
                            FlightStateInput(
                                gps = gps,
                                baro = baro,
                                airspeed = null,
                                flightData = flightData,
                                useStabilizedAirspeed = true,
                                aglMeters = agl
                            )
                        }
                    }
                }
                .collect { input ->
                    processSample(input)
                }
        }
    }

    private fun resetForSourceSwitch() {
        detector.reset()
        _flightState.value = FlyingState()
        lastGpsSample = null
        lastGpsSeenMonoMs = 0L
    }

    private fun processSample(input: FlightStateInput) {
        val nowMonoMs = clock.nowMonoMs()
        val gps = input.gps ?: fallbackGpsForState(
            nowMonoMs = nowMonoMs,
            source = flightDataRepository.activeSource.value
        )

        if (gps == null) {
            detector.reset()
            _flightState.value = FlyingState()
            lastGpsSample = null
            lastGpsSeenMonoMs = 0L
            return
        }

        val liveFlightData = input.flightData
        val (trueAirspeedMs, airspeedReal) = if (input.useStabilizedAirspeed) {
            val stabilizedTrue = liveFlightData?.trueAirspeed?.value?.takeIf { it.isFinite() }
            val sourceLabel = liveFlightData?.airspeedSource.orEmpty()
            val sourceReal = liveFlightData?.tasValid == true && isTrustedAirspeedSource(sourceLabel)
            stabilizedTrue to (sourceReal && stabilizedTrue != null)
        } else {
            val sample = input.airspeed
            val sampleTrue = sample?.trueMs
            sampleTrue to (sample?.valid == true && sampleTrue?.isFinite() == true)
        }
        val altitudeMeters = resolveAltitudeMeters(gps, input.baro)

        val state = detector.update(
            timestampMillis = gps.timeForCalculationsMillis,
            groundSpeedMs = gps.speed.value,
            trueAirspeedMs = trueAirspeedMs,
            airspeedReal = airspeedReal,
            altitudeMeters = altitudeMeters,
            aglMeters = input.aglMeters
        )
        if (input.gps != null) {
            lastGpsSample = gps
            lastGpsSeenMonoMs = nowMonoMs
        }
        _flightState.value = state
    }

    private fun fallbackGpsForState(
        nowMonoMs: Long,
        source: FlightDataRepository.Source
    ): GPSData? {
        // AI-NOTE: Keep brief live GPS dropouts from collapsing an active
        // flight into neutral state; replay remains explicit and deterministic.
        if (source != FlightDataRepository.Source.LIVE) return null
        val staleMs = nowMonoMs - lastGpsSeenMonoMs
        if (lastGpsSample == null || staleMs < 0L || staleMs > GPS_STATE_GRACE_MS) {
            return null
        }
        return lastGpsSample
    }

    private fun extractFreshAglMeters(flightData: CompleteFlightData?): Double? {
        val agl = flightData?.agl?.value?.takeIf { it.isFinite() } ?: return null
        val updatedAtMonoMs = flightData.aglTimestampMonoMs
        if (updatedAtMonoMs <= 0L) return null
        val ageMs = clock.nowMonoMs() - updatedAtMonoMs
        if (ageMs < 0L || ageMs > AGL_STALE_AFTER_MS) return null
        return agl
    }

    private fun resolveAltitudeMeters(gps: GPSData, baro: BaroData?): Double? {
        val baroAltitude = baro?.let { pressureToAltitudeMeters(it.pressureHPa.value) }
            ?.takeIf { it.isFinite() }
        val gpsAltitude = gps.altitude.value.takeIf { it.isFinite() }
        return baroAltitude ?: gpsAltitude
    }

    private fun pressureToAltitudeMeters(pressureHpa: Double): Double {
        if (!pressureHpa.isFinite() || pressureHpa <= 0.0) return Double.NaN
        return 44330.0 * (1.0 - (pressureHpa / SEA_LEVEL_PRESSURE_HPA).pow(0.1903))
    }

    private companion object {
        private const val SEA_LEVEL_PRESSURE_HPA = 1013.25
        private const val AGL_STALE_AFTER_MS = 15_000L
        private const val GPS_STATE_GRACE_MS = 20_000L
    }

    private fun isTrustedAirspeedSource(sourceLabel: String): Boolean {
        return sourceLabel.equals("WIND", ignoreCase = true) ||
            sourceLabel.equals("SENSOR", ignoreCase = true)
    }
}
