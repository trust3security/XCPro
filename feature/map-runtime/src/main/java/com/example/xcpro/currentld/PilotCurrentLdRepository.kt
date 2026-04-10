package com.example.xcpro.currentld

import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.navigation.WaypointNavigationRepository
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

@ViewModelScoped
class PilotCurrentLdRepository constructor(
    flightDataFlow: Flow<com.example.xcpro.sensors.CompleteFlightData?>,
    windStateFlow: Flow<com.example.xcpro.weather.wind.model.WindState>,
    flightStateFlow: Flow<com.example.xcpro.sensors.domain.FlyingState>,
    waypointNavigationFlow: Flow<com.example.xcpro.navigation.WaypointNavigationSnapshot>,
    sinkProvider: StillAirSinkProvider
) {

    @Inject
    constructor(
        flightDataRepository: FlightDataRepository,
        windSensorFusionRepository: WindSensorFusionRepository,
        flightStateSource: FlightStateSource,
        waypointNavigationRepository: WaypointNavigationRepository,
        sinkProvider: StillAirSinkProvider
    ) : this(
        flightDataFlow = flightDataRepository.flightData,
        windStateFlow = windSensorFusionRepository.windState,
        flightStateFlow = flightStateSource.flightState,
        waypointNavigationFlow = waypointNavigationRepository.waypointNavigation,
        sinkProvider = sinkProvider
    )

    private val calculator = PilotCurrentLdCalculator(sinkProvider)

    val pilotCurrentLd: Flow<PilotCurrentLdSnapshot> = combine(
        flightDataFlow,
        windStateFlow,
        flightStateFlow,
        waypointNavigationFlow
    ) { completeData, windState, flightState, waypointNavigation ->
        PilotCurrentLdInput(
            completeData = completeData,
            windState = windState,
            flightState = flightState,
            waypointNavigation = waypointNavigation
        )
    }.scan(PilotCurrentLdState()) { state, input ->
        calculator.update(state, input)
    }.map { state ->
        state.snapshot
    }.distinctUntilChanged()
}
