package com.example.xcpro.sensors

import android.content.Context
import com.example.dfcards.dfcards.calculations.SimpleAglCalculator
import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.di.LiveSource
import com.example.xcpro.di.ReplaySource
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.sensors.domain.FlyingStateDetector
import com.example.xcpro.weather.wind.data.AirspeedDataSource
import com.example.xcpro.weather.wind.model.AirspeedSample
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.pow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Singleton
class FlightStateRepository @Inject constructor(
    @ApplicationContext context: Context,
    @LiveSource private val liveSensors: SensorDataSource,
    @ReplaySource private val replaySensors: SensorDataSource,
    @LiveSource private val liveAirspeedSource: AirspeedDataSource,
    @ReplaySource private val replayAirspeedSource: AirspeedDataSource,
    private val flightDataRepository: FlightDataRepository,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : FlightStateSource {

    private data class FlightStateInput(
        val gps: GPSData?,
        val baro: BaroData?,
        val airspeed: AirspeedSample?,
        val aglMeters: Double?
    )

    private val scope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private val aglCalculator = SimpleAglCalculator(context)
    private val detector = FlyingStateDetector()

    private val _flightState = MutableStateFlow(FlyingState())
    override val flightState: StateFlow<FlyingState> = _flightState.asStateFlow()

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
                    val airspeedSource = if (source == FlightDataRepository.Source.REPLAY) {
                        replayAirspeedSource
                    } else {
                        liveAirspeedSource
                    }

                    val aglFlow = sensors.gpsFlow
                        .mapLatest { gps ->
                            if (gps == null) return@mapLatest null
                            val altitude = gps.altitude.value
                            if (!altitude.isFinite()) return@mapLatest null
                            aglCalculator.calculateAgl(
                                altitude = altitude,
                                lat = gps.position.latitude,
                                lon = gps.position.longitude,
                                speed = gps.speed.value
                            )
                        }
                        .flowOn(ioDispatcher)

                    combine(
                        sensors.gpsFlow,
                        sensors.baroFlow,
                        airspeedSource.airspeedFlow,
                        aglFlow
                    ) { gps, baro, airspeed, agl ->
                        FlightStateInput(
                            gps = gps,
                            baro = baro,
                            airspeed = airspeed,
                            aglMeters = agl
                        )
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
    }

    private fun processSample(input: FlightStateInput) {
        val gps = input.gps ?: run {
            detector.reset()
            _flightState.value = FlyingState()
            return
        }

        val airspeed = input.airspeed
        val airspeedReal = airspeed?.valid == true && airspeed.trueMs.isFinite()
        val altitudeMeters = resolveAltitudeMeters(gps, input.baro)

        val state = detector.update(
            timestampMillis = gps.timestamp,
            groundSpeedMs = gps.speed.value,
            trueAirspeedMs = airspeed?.trueMs,
            airspeedReal = airspeedReal,
            altitudeMeters = altitudeMeters,
            aglMeters = input.aglMeters
        )
        _flightState.value = state
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
    }
}
