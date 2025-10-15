package com.example.xcpro.tasks.aat.models

import com.example.xcpro.tasks.aat.models.AATLatLng

/**
 * Represents an assigned area in an AAT (Area Assignment Task).
 * This model is completely autonomous and self-contained for the AAT module.
 */
data class AssignedArea(
    val name: String,
    val centerPoint: AATLatLng,
    val geometry: AreaGeometry,
    val sequence: Int = 0, // Order in the task (0-based)
    val description: String = ""
) {
    /**
     * Get a human-readable description of the area
     */
    fun getAreaDescription(): String {
        return when (geometry) {
            is AreaGeometry.Circle -> 
                "$name: Circle (${String.format("%.1f", geometry.radius / 1000)}km radius)"
            is AreaGeometry.Sector -> 
                "$name: Sector (${String.format("%.1f", geometry.innerRadius?.let { it / 1000 } ?: 0.0)}-${String.format("%.1f", geometry.outerRadius / 1000)}km, ${String.format("%.0f", geometry.startBearing)}°-${String.format("%.0f", geometry.endBearing)}°)"
        }
    }
    
    /**
     * Calculate the approximate area size in square kilometers
     */
    fun getApproximateAreaSizeKm2(): Double {
        return when (geometry) {
            is AreaGeometry.Circle -> {
                val radiusKm = geometry.radius / 1000.0
                kotlin.math.PI * radiusKm * radiusKm
            }
            is AreaGeometry.Sector -> {
                val outerRadiusKm = geometry.outerRadius / 1000.0
                val innerRadiusKm = geometry.innerRadius?.let { it / 1000.0 } ?: 0.0
                val sectorAngleRad = kotlin.math.abs(
                    (geometry.endBearing - geometry.startBearing) * kotlin.math.PI / 180.0
                )
                // Handle wrap-around case
                val adjustedAngle = if (sectorAngleRad > kotlin.math.PI) {
                    2.0 * kotlin.math.PI - sectorAngleRad
                } else {
                    sectorAngleRad
                }
                (adjustedAngle / (2.0 * kotlin.math.PI)) * kotlin.math.PI * 
                (outerRadiusKm * outerRadiusKm - innerRadiusKm * innerRadiusKm)
            }
        }
    }
}

/**
 * Sealed class representing different area geometries for AAT tasks
 */
sealed class AreaGeometry {
    
    /**
     * Circular area with center and radius
     */
    data class Circle(
        val radius: Double  // Radius in meters
    ) : AreaGeometry() {
        init {
            require(radius > 0) { "Circle radius must be positive" }
        }
    }
    
    /**
     * Sector area with inner radius, outer radius, and angular bounds
     */
    data class Sector(
        val innerRadius: Double?, // Inner radius in meters (null for full sector)
        val outerRadius: Double,  // Outer radius in meters
        val startBearing: Double, // Start bearing in degrees (0-360, 0 = north)
        val endBearing: Double    // End bearing in degrees (0-360, 0 = north)
    ) : AreaGeometry() {
        init {
            require(outerRadius > 0) { "Outer radius must be positive" }
            require(innerRadius == null || innerRadius >= 0) { "Inner radius must be non-negative" }
            require(innerRadius == null || innerRadius < outerRadius) { "Inner radius must be less than outer radius" }
            require(startBearing >= 0 && startBearing < 360) { "Start bearing must be in range [0, 360)" }
            require(endBearing >= 0 && endBearing < 360) { "End bearing must be in range [0, 360)" }
        }
        
        /**
         * Get the angular span of the sector in degrees
         */
        fun getAngularSpan(): Double {
            val span = if (endBearing >= startBearing) {
                endBearing - startBearing
            } else {
                360 - startBearing + endBearing
            }
            return span
        }
        
        /**
         * Check if the sector wraps around 0 degrees (north)
         */
        fun wrapsAroundNorth(): Boolean {
            return endBearing < startBearing
        }
    }
}

/**
 * Validation result for assigned areas
 */
data class AreaValidation(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    companion object {
        fun valid() = AreaValidation(true)
        fun invalid(vararg errors: String) = AreaValidation(false, errors.toList())
        fun validWithWarnings(vararg warnings: String) = AreaValidation(true, emptyList(), warnings.toList())
    }
}