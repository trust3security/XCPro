package com.example.xcpro.map

import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.common.waypoint.WaypointLoader
import com.example.xcpro.currentld.PilotCurrentLdRepository
import com.example.xcpro.glider.SpeedBoundsMs
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.navigation.WaypointNavigationRepository
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.flightdata.FlightDataRepository
import kotlinx.coroutines.flow.Flow

internal class SuccessfulWaypointLoader(
    private val waypoints: List<WaypointData>
) : WaypointLoader {
    override suspend fun load(): List<WaypointData> = waypoints
}

internal class FailingWaypointLoader(
    private val throwable: Throwable
) : WaypointLoader {
    override suspend fun load(): List<WaypointData> = throw throwable
}

internal fun testStillAirSinkProvider(): StillAirSinkProvider = object : StillAirSinkProvider {
    override fun sinkAtSpeed(airspeedMs: Double): Double {
        val centered = airspeedMs - 17.0
        return 0.55 + (centered * centered * 0.01)
    }

    override fun iasBoundsMs(): SpeedBoundsMs = SpeedBoundsMs(minMs = 12.0, maxMs = 25.0)
}

internal fun createPilotCurrentLdRepositoryForTest(
    flightDataRepository: FlightDataRepository,
    windStateFlow: Flow<WindState>,
    flightStateFlow: Flow<FlyingState>,
    waypointNavigationRepository: WaypointNavigationRepository,
    sinkProvider: StillAirSinkProvider
): PilotCurrentLdRepository = PilotCurrentLdRepository(
    flightDataFlow = flightDataRepository.flightData,
    windStateFlow = windStateFlow,
    flightStateFlow = flightStateFlow,
    waypointNavigationFlow = waypointNavigationRepository.waypointNavigation,
    sinkProvider = sinkProvider
)
