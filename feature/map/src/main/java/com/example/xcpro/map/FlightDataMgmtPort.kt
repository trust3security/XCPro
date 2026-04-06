package com.example.xcpro.map

import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.dfcards.FlightDataViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Narrow map-owned contract for the flight-management route.
 * Keeps route callers out of concrete runtime/controller handles.
 */
interface FlightDataMgmtPort {
    val liveFlightDataFlow: StateFlow<RealTimeFlightData?>

    fun bindCards(flightViewModel: FlightDataViewModel)
}

internal fun createFlightDataMgmtPort(
    flightDataManager: FlightDataManager,
    bindFlightCards: (FlightDataViewModel) -> Unit
): FlightDataMgmtPort = object : FlightDataMgmtPort {
    override val liveFlightDataFlow: StateFlow<RealTimeFlightData?> =
        flightDataManager.liveFlightDataFlow

    override fun bindCards(flightViewModel: FlightDataViewModel) {
        bindFlightCards(flightViewModel)
    }
}
