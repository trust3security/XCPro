package com.trust3.xcpro.tasks.aat.models

import com.trust3.xcpro.tasks.aat.models.AATLatLng
import java.time.Duration
import java.time.LocalDateTime

/**
 * Main AAT (Area Assignment Task) data model.
 * This is completely autonomous and self-contained for the AAT module.
 */
data class AATTask(
    val id: String,
    val name: String,
    val minimumTaskTime: Duration,
    val start: AATStartPoint,
    val assignedAreas: List<AssignedArea>,
    val finish: AATFinishPoint,
    val maxStartAltitude: Int? = null, // meters MSL
    val taskDate: LocalDateTime? = null,
    val description: String = "",
    val createdBy: String = "AAT System"
) {
    init {
        require(id.isNotBlank()) { "Task ID cannot be blank" }
        require(name.isNotBlank()) { "Task name cannot be blank" }
        require(minimumTaskTime.toMinutes() > 0) { "Minimum task time must be positive" }
        require(assignedAreas.isNotEmpty()) { "Task must have at least one assigned area" }
        require(assignedAreas.size <= 10) { "Task cannot have more than 10 assigned areas" }
    }
    
    /**
     * Get total number of areas in the task
     */
    fun getAreaCount(): Int = assignedAreas.size
    
    /**
     * Get minimum task time in hours (formatted)
     */
    fun getMinimumTaskTimeFormatted(): String {
        val hours = minimumTaskTime.toHours()
        val minutes = minimumTaskTime.toMinutes() % 60
        return if (minutes == 0L) {
            "${hours}h"
        } else {
            "${hours}h ${minutes}m"
        }
    }
    
    /**
     * Get task summary string
     */
    fun getTaskSummary(): String {
        return "$name - ${getAreaCount()} areas - ${getMinimumTaskTimeFormatted()}"
    }
    
    /**
     * Get all waypoints in sequence (start + areas + finish)
     */
    fun getAllWaypoints(): List<AATLatLng> {
        val waypoints = mutableListOf<AATLatLng>()
        waypoints.add(start.position)
        waypoints.addAll(assignedAreas.map { it.centerPoint })
        waypoints.add(finish.position)
        return waypoints
    }
    
    /**
     * Validate basic task structure
     */
    fun validateBasicStructure(): List<String> {
        val errors = mutableListOf<String>()
        
        if (minimumTaskTime.toMinutes() < 30) {
            errors.add("Minimum task time should be at least 30 minutes")
        }
        
        if (minimumTaskTime.toHours() > 8) {
            errors.add("Minimum task time should not exceed 8 hours")
        }
        
        // Check for duplicate area names
        val areaNames = assignedAreas.map { it.name }
        val duplicateNames = areaNames.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicateNames.isNotEmpty()) {
            errors.add("Duplicate area names: ${duplicateNames.joinToString(", ")}")
        }
        
        return errors
    }
}

/**
 * Start point configuration for AAT tasks
 */
data class AATStartPoint(
    val position: AATLatLng,
    val type: AATStartType,
    val lineLength: Double? = null, // meters, required for LINE type
    val radius: Double? = null, // meters, required for CIRCLE type
    val sectorRadius: Double? = null, // meters, required for BGA_SECTOR type
    val elevation: Double? = null, // meters MSL
    val name: String = "Start"
) {
    init {
        when (type) {
            AATStartType.LINE -> {
                require(lineLength != null && lineLength > 0) { "Line start requires positive line length" }
            }
            AATStartType.CIRCLE -> {
                require(radius != null && radius > 0) { "Circle start requires positive radius" }
            }
            AATStartType.BGA_SECTOR -> {
                require(sectorRadius != null && sectorRadius > 0) { "BGA sector start requires positive sector radius" }
            }
        }
    }
}

/**
 * Finish point configuration for AAT tasks
 */
data class AATFinishPoint(
    val position: AATLatLng,
    val type: AATFinishType,
    val lineLength: Double? = null, // meters, required for LINE type
    val radius: Double? = null, // meters, required for CIRCLE type
    val elevation: Double? = null, // meters MSL
    val name: String = "Finish"
) {
    init {
        when (type) {
            AATFinishType.LINE -> {
                require(lineLength != null && lineLength > 0) { "Line finish requires positive line length" }
            }
            AATFinishType.CIRCLE -> {
                require(radius != null && radius > 0) { "Circle finish requires positive radius" }
            }
        }
    }
}

/**
 * AAT start types
 */
enum class AATStartType {
    LINE,           // Start line perpendicular to first leg
    CIRCLE,         // Start circle around start point
    BGA_SECTOR      // BGA Start Sector: 90-degree sector aligned with track to first turnpoint
}

/**
 * AAT finish types  
 */
enum class AATFinishType {
    LINE,   // Finish line perpendicular to last leg
    CIRCLE  // Finish circle around finish point
}

/**
 * AAT task status enumeration
 */
enum class AATTaskStatus {
    DRAFT,      // Task being created/edited
    READY,      // Task ready for use
    ACTIVE,     // Task currently in use
    COMPLETED,  // Task completed
    ARCHIVED    // Task archived
}

/**
 * Task validation result
 */
data class AATTaskValidation(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val taskDistance: AATTaskDistance? = null
) {
    companion object {
        fun valid(taskDistance: AATTaskDistance? = null) = 
            AATTaskValidation(true, taskDistance = taskDistance)
            
        fun invalid(vararg errors: String) = 
            AATTaskValidation(false, errors.toList())
            
        fun validWithWarnings(taskDistance: AATTaskDistance, vararg warnings: String) = 
            AATTaskValidation(true, emptyList(), warnings.toList(), taskDistance)
    }
}

/**
 * AAT task distance information
 */
data class AATTaskDistance(
    val minimumDistance: Double,    // meters
    val maximumDistance: Double,    // meters  
    val nominalDistance: Double     // meters (through area centers)
) {
    /**
     * Get minimum distance in kilometers
     */
    fun getMinimumDistanceKm(): Double = minimumDistance / 1000.0
    
    /**
     * Get maximum distance in kilometers
     */
    fun getMaximumDistanceKm(): Double = maximumDistance / 1000.0
    
    /**
     * Get nominal distance in kilometers
     */
    fun getNominalDistanceKm(): Double = nominalDistance / 1000.0
    
    /**
     * Get distance range as a formatted string
     */
    fun getDistanceRangeFormatted(): String {
        return "${String.format("%.1f", getMinimumDistanceKm())}-${String.format("%.1f", getMaximumDistanceKm())}km"
    }
}
