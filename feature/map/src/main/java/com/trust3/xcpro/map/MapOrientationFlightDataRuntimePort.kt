package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.OrientationController
import com.trust3.xcpro.common.orientation.OrientationFlightDataSnapshot

/**
 * Narrow map-owned write seam for forwarding orientation flight-data snapshots.
 */
internal interface MapOrientationFlightDataRuntimePort {
    fun updateFromFlightData(snapshot: OrientationFlightDataSnapshot)
}

internal fun createMapOrientationFlightDataRuntimePort(
    orientationController: OrientationController
): MapOrientationFlightDataRuntimePort = object : MapOrientationFlightDataRuntimePort {
    override fun updateFromFlightData(snapshot: OrientationFlightDataSnapshot) {
        orientationController.updateFromFlightData(snapshot)
    }
}
