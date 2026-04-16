package com.example.xcpro.map

import com.example.xcpro.common.orientation.OrientationController
import com.example.xcpro.core.flight.RealTimeFlightData
import com.example.xcpro.toOrientationFlightDataSnapshot
import kotlinx.coroutines.flow.collectLatest

internal class MapOrientationFlightDataRuntimeBinder(
    private val flightDataManager: FlightDataManager,
    private val orientationController: OrientationController
) {
    suspend fun collectLatestFlightData() {
        flightDataManager.liveFlightDataFlow.collectLatest(::applyFlightData)
    }

    internal fun applyFlightData(liveData: RealTimeFlightData?) {
        if (liveData == null) {
            return
        }
        orientationController.updateFromFlightData(liveData.toOrientationFlightDataSnapshot())
    }
}
