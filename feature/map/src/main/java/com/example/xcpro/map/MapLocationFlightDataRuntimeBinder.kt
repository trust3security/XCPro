package com.example.xcpro.map

import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.core.flight.RealTimeFlightData
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

internal class MapLocationFlightDataRuntimeBinder(
    private val liveFlightDataFlow: StateFlow<RealTimeFlightData?>,
    private val locationManager: MapLocationRuntimePort,
    private val orientationProvider: () -> OrientationData,
    private val shouldForwardReplayLocationUpdate: () -> Boolean
) {
    suspend fun collectLatestFlightData() {
        liveFlightDataFlow.collectLatest(::applyFlightData)
    }

    internal fun applyFlightData(liveData: RealTimeFlightData?) {
        if (liveData == null || !shouldForwardReplayLocationUpdate()) {
            return
        }
        locationManager.updateLocationFromReplayFrame(
            liveData.toReplayLocationFrame(),
            orientationProvider()
        )
    }
}
