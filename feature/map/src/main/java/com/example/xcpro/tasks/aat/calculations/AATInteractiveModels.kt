package com.example.xcpro.tasks.aat.calculations

import com.example.xcpro.tasks.aat.models.AATLatLng

/**
 * Data classes for interactive distance calculations
 */
data class AATInteractiveTaskDistance(
    val totalDistanceMeters: Double,
    val segments: List<AATInteractiveDistanceSegment>,
    val calculationTime: Long = 0L
) {
    private companion object {
        const val METERS_PER_KILOMETER = 1000.0
    }

    val isValid: Boolean get() = totalDistanceMeters > 0.0 && segments.isNotEmpty()
    val segmentCount: Int get() = segments.size

    fun getDistanceBreakdown(): String {
        val breakdown = StringBuilder()
        breakdown.append("Total: ${String.format("%.2f", totalDistanceMeters / METERS_PER_KILOMETER)} km\n")

        segments.forEachIndexed { index, segment ->
            breakdown.append("Leg ${index + 1}: ${String.format("%.2f", segment.distanceMeters / METERS_PER_KILOMETER)} km\n")
        }

        return breakdown.toString().trim()
    }
}

data class AATInteractiveDistanceSegment(
    val fromPoint: AATLatLng,
    val toPoint: AATLatLng,
    val distanceMeters: Double,
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
