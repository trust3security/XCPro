package com.example.xcpro.tasks.aat.calculations

import com.example.xcpro.tasks.aat.models.AATLatLng

/**
 * Data classes for interactive distance calculations
 */
data class AATInteractiveTaskDistance(
    val totalDistance: Double, // km
    val segments: List<AATInteractiveDistanceSegment>,
    val calculationTime: Long = System.currentTimeMillis()
) {
    val isValid: Boolean get() = totalDistance > 0.0 && segments.isNotEmpty()
    val segmentCount: Int get() = segments.size

    fun getDistanceBreakdown(): String {
        val breakdown = StringBuilder()
        breakdown.append("Total: ${String.format("%.2f", totalDistance)} km\n")

        segments.forEachIndexed { index, segment ->
            breakdown.append("Leg ${index + 1}: ${String.format("%.2f", segment.distance)} km\n")
        }

        return breakdown.toString().trim()
    }
}

data class AATInteractiveDistanceSegment(
    val fromPoint: AATLatLng,
    val toPoint: AATLatLng,
    val distance: Double, // km
    val segmentType: AATInteractiveSegmentType,
    val fromWaypointIndex: Int,
    val toWaypointIndex: Int
)

enum class AATInteractiveSegmentType {
    START_TO_TURNPOINT,
    TURNPOINT_TO_TURNPOINT,
    TURNPOINT_TO_FINISH,
    START_TO_FINISH,
    CENTER_TO_TARGET
}
