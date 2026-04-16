package com.example.xcpro.map

import com.example.xcpro.core.flight.RealTimeFlightData
import com.example.xcpro.toOrientationFlightDataSnapshot
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

internal class MapOrientationFlightDataRuntimeBinder(
    private val liveFlightDataFlow: StateFlow<RealTimeFlightData?>,
    private val orientationRuntimePort: MapOrientationFlightDataRuntimePort
) {
    suspend fun collectLatestFlightData() {
        liveFlightDataFlow.collectLatest(::applyFlightData)
    }

    internal fun applyFlightData(liveData: RealTimeFlightData?) {
        if (liveData == null) {
            return
        }
        orientationRuntimePort.updateFromFlightData(liveData.toOrientationFlightDataSnapshot())
    }
}
