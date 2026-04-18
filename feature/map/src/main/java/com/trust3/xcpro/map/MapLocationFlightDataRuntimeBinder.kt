package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.OrientationData
import com.trust3.xcpro.core.flight.RealTimeFlightData
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
