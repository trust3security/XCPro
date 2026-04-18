package com.trust3.xcpro.tasks.aat.interaction

import com.trust3.xcpro.tasks.aat.map.AATMovablePointManager
import com.trust3.xcpro.tasks.aat.models.AATWaypoint
import com.trust3.xcpro.tasks.aat.models.AATLatLng

/**
 * Stateless geometry validation for AAT edit interactions.
 * Delegates to AATMovablePointManager for area-aware clamping/validation.
 */
class AATEditGeometryValidator(
    private val movablePointManager: AATMovablePointManager = AATMovablePointManager()
) {

    /**
     * Clamp a candidate target point so it stays inside the waypoint's area.
     * Returns a waypoint copy with the clamped target point.
     */
    fun clampTarget(waypoint: AATWaypoint, candidate: AATLatLng): AATWaypoint {
        return movablePointManager.moveTargetPoint(
            waypoint,
            candidate.latitude,
            candidate.longitude
        )
    }

    /**
     * Check whether a candidate lies within the waypoint area.
     */
    fun isInsideArea(waypoint: AATWaypoint, candidate: AATLatLng): Boolean {
        return movablePointManager.isPointInsideArea(waypoint, candidate)
    }

    /**
     * Hit-test a tap inside the waypoint's area, regardless of shape.
     */
    fun hitTestArea(waypoint: AATWaypoint, tap: AATLatLng): Boolean {
        return movablePointManager.isPointInsideArea(waypoint, tap)
    }
}
