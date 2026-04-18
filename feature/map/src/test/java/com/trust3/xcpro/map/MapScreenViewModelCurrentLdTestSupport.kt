package com.trust3.xcpro.map

import com.trust3.xcpro.common.waypoint.WaypointData
import com.trust3.xcpro.common.waypoint.WaypointLoader
import com.trust3.xcpro.currentld.PilotCurrentLdRepository
import com.trust3.xcpro.glider.SpeedBoundsMs
import com.trust3.xcpro.glider.StillAirSinkProvider
import com.trust3.xcpro.navigation.WaypointNavigationRepository
import com.trust3.xcpro.sensors.FlightStateSource
import com.trust3.xcpro.sensors.domain.FlyingState
import com.trust3.xcpro.weather.wind.model.WindState
import com.trust3.xcpro.flightdata.FlightDataRepository
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
