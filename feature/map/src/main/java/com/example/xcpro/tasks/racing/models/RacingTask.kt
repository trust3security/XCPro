package com.example.xcpro.tasks.racing.models

import com.example.xcpro.tasks.racing.RacingGeometryUtils
import java.time.LocalDateTime

/**
 * Simple data class for representing latitude/longitude coordinates in Racing tasks
 */
data class RacingLatLng(
    val latitude: Double,
    val longitude: Double
)

/**
 * Main Racing task data model.
 * This is completely autonomous and self-contained for the Racing module.
 * No dependencies on shared TaskManager or TaskWaypoint models.
 */
data class RacingTask(
    val id: String,
    val name: String,
    val start: RacingStartPoint,
    val turnpoints: List<RacingTurnpoint>,
    val finish: RacingFinishPoint,
    val taskDate: LocalDateTime? = null,
    val description: String = "",
    val createdBy: String = "Racing System",
    val windSpeed: Double? = null, // m/s
    val windDirection: Double? = null, // degrees
    val maxStartAltitude: Int? = null // meters MSL
) {
    init {
        require(id.isNotBlank()) { "Task ID cannot be blank" }
        require(name.isNotBlank()) { "Task name cannot be blank" }
        // Racing tasks can have zero turnpoints (start-finish only)
        require(turnpoints.size <= 15) { "Task cannot have more than 15 turnpoints" }
    }

    /**
     * Get total number of turnpoints in the task
     */
    fun getTurnpointCount(): Int = turnpoints.size

    /**
     * Get task summary string
     */
    fun getTaskSummary(): String {
        val turnpointText = when (turnpoints.size) {
            0 -> "Start-Finish only"
            1 -> "1 turnpoint"
            else -> "${turnpoints.size} turnpoints"
        }
        return "$name - $turnpointText"
    }

    /**
     * Get all waypoints in sequence (start + turnpoints + finish)
     */
    fun getAllWaypoints(): List<RacingLatLng> {
        val waypoints = mutableListOf<RacingLatLng>()
        waypoints.add(start.position)
        waypoints.addAll(turnpoints.map { it.position })
        waypoints.add(finish.position)
        return waypoints
    }

    /**
     * ✅ COMPATIBILITY: Get all waypoints as RacingWaypoint objects for legacy verification code
     * This enables complete separation while maintaining backward compatibility
     */
    val racingWaypoints: List<RacingWaypoint> get() {
        val waypoints = mutableListOf<RacingWaypoint>()

        // Add start waypoint
        waypoints.add(RacingWaypoint(
            id = "${id}_start",
            title = "START",
            subtitle = start.name,
            lat = start.position.latitude,
            lon = start.position.longitude,
            role = RacingWaypointRole.START,
            startPointType = convertToRacingStartPointType(start.type),
            gateWidth = start.getEffectiveRadius() / 1000.0 // Convert meters to km
        ))

        // Add turnpoint waypoints
        turnpoints.forEachIndexed { index, tp ->
            waypoints.add(RacingWaypoint(
                id = "${id}_tp${index + 1}",
                title = "TP${index + 1}",
                subtitle = tp.name,
                lat = tp.position.latitude,
                lon = tp.position.longitude,
                role = RacingWaypointRole.TURNPOINT,
                turnPointType = tp.type, // Direct use since we're using the same enum now
                gateWidth = (tp.getEffectiveRadius() ?: 5000.0) / 1000.0 // Convert meters to km, default 5km
            ))
        }

        // Add finish waypoint
        waypoints.add(RacingWaypoint(
            id = "${id}_finish",
            title = "FINISH",
            subtitle = finish.name,
            lat = finish.position.latitude,
            lon = finish.position.longitude,
            role = RacingWaypointRole.FINISH,
            finishPointType = convertToRacingFinishPointType(finish.type),
            gateWidth = finish.getEffectiveRadius() / 1000.0 // Convert meters to km
        ))

        return waypoints
    }

    // Helper functions to convert between enum types
    private fun convertToRacingStartPointType(type: RacingStartType): RacingStartPointType {
        return when (type) {
            RacingStartType.START_LINE -> RacingStartPointType.START_LINE
            RacingStartType.START_CYLINDER -> RacingStartPointType.START_CYLINDER
            RacingStartType.FAI_START_SECTOR -> RacingStartPointType.FAI_START_SECTOR
        }
    }

    private fun convertToRacingFinishPointType(type: RacingFinishType): RacingFinishPointType {
        return when (type) {
            RacingFinishType.FINISH_LINE -> RacingFinishPointType.FINISH_LINE
            RacingFinishType.FINISH_CYLINDER -> RacingFinishPointType.FINISH_CYLINDER
        }
    }

    /**
     * ✅ LEGACY COMPATIBILITY: Support for isComplete property
     */
    val isComplete: Boolean get() = turnpoints.isNotEmpty()

    /**
     * Get waypoint at specific index
     */
    fun getWaypoint(index: Int): RacingLatLng? {
        val allWaypoints = getAllWaypoints()
        return if (index in allWaypoints.indices) allWaypoints[index] else null
    }

    /**
     * Check if this is a valid FAI triangle task
     */
    fun isFAITriangle(): Boolean {
        // FAI triangle requires exactly 3 turnpoints
        if (turnpoints.size != 3) return false

        // Additional FAI triangle validation would go here
        // (28% rule, minimum distances, etc.)
        return true
    }

    /**
     * Validate basic task structure
     */
    fun validateBasicStructure(): List<String> {
        val errors = mutableListOf<String>()

        // Check for duplicate turnpoint names
        val turnpointNames = turnpoints.map { it.name }
        val duplicateNames = turnpointNames.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicateNames.isNotEmpty()) {
            errors.add("Duplicate turnpoint names: ${duplicateNames.joinToString(", ")}")
        }

        // Check for minimum distance between waypoints
        val allPositions = getAllWaypoints()
        for (i in 0 until allPositions.size - 1) {
            val distance = RacingGeometryUtils.haversineDistance(
                allPositions[i].latitude, allPositions[i].longitude,
                allPositions[i + 1].latitude, allPositions[i + 1].longitude
            ) * 1000.0 // Convert km to meters
            if (distance < 100) { // Less than 100 meters
                errors.add("Waypoints ${i} and ${i + 1} are too close together (< 100m)")
            }
        }

        return errors
    }
}

/**
 * Start point configuration for Racing tasks
 */
data class RacingStartPoint(
    val position: RacingLatLng,
    val type: RacingStartType,
    val lineLength: Double? = null, // meters, required for LINE type
    val cylinderRadius: Double? = null, // meters, required for CYLINDER type
    val sectorRadius: Double? = null, // meters, required for BGA_SECTOR type
    val elevation: Double? = null, // meters MSL
    val name: String = "Start"
) {
    init {
        when (type) {
            RacingStartType.START_LINE -> {
                require(lineLength != null && lineLength > 0) { "Line start requires positive line length" }
            }
            RacingStartType.START_CYLINDER -> {
                require(cylinderRadius != null && cylinderRadius > 0) { "Cylinder start requires positive radius" }
            }
            RacingStartType.FAI_START_SECTOR -> {
                require(sectorRadius != null && sectorRadius > 0) { "FAI start sector requires positive radius" }
            }
        }
    }

    /**
     * Get the effective radius for distance calculations
     */
    fun getEffectiveRadius(): Double {
        return when (type) {
            RacingStartType.START_LINE -> lineLength ?: 1000.0
            RacingStartType.START_CYLINDER -> cylinderRadius ?: 1000.0
            RacingStartType.FAI_START_SECTOR -> sectorRadius ?: 1000.0
        }
    }
}

/**
 * Turnpoint configuration for Racing tasks
 */
data class RacingTurnpoint(
    val position: RacingLatLng,
    val type: RacingTurnPointType, // Now uses the standardized enum with capital P
    val cylinderRadius: Double? = null, // meters, required for CYLINDER types
    val sectorAngle: Double? = null, // degrees, for sectors
    val sectorRadius: Double? = null, // meters, for finite sectors
    val elevation: Double? = null, // meters MSL
    val name: String,
    val code: String? = null // ICAO or other code
) {
    init {
        when (type) {
            RacingTurnPointType.TURN_POINT_CYLINDER -> {
                require(cylinderRadius != null && cylinderRadius > 0) { "Cylinder turnpoint requires positive radius" }
            }
            RacingTurnPointType.FAI_QUADRANT -> {
                // FAI quadrant has infinite radius, 90-degree sectors
                // No specific radius requirement
            }
            RacingTurnPointType.KEYHOLE -> {
                // Keyhole is 500m cylinder + 10km, 90-degree sector
                // Has default values, no requirement
            }
        }
    }

    /**
     * Get the effective radius for distance calculations
     */
    fun getEffectiveRadius(): Double? {
        return when (type) {
            RacingTurnPointType.TURN_POINT_CYLINDER -> cylinderRadius
            RacingTurnPointType.FAI_QUADRANT -> null // Infinite
            RacingTurnPointType.KEYHOLE -> 500.0 // Inner cylinder radius
        }
    }

    /**
     * Check if this turnpoint has an infinite observation zone
     */
    fun hasInfiniteRadius(): Boolean {
        return type in listOf(
            RacingTurnPointType.FAI_QUADRANT
        )
    }
}

/**
 * Finish point configuration for Racing tasks
 */
data class RacingFinishPoint(
    val position: RacingLatLng,
    val type: RacingFinishType,
    val lineLength: Double? = null, // meters, required for LINE type
    val cylinderRadius: Double? = null, // meters, required for CYLINDER type
    val elevation: Double? = null, // meters MSL
    val name: String = "Finish"
) {
    init {
        when (type) {
            RacingFinishType.FINISH_LINE -> {
                require(lineLength != null && lineLength > 0) { "Line finish requires positive line length" }
            }
            RacingFinishType.FINISH_CYLINDER -> {
                require(cylinderRadius != null && cylinderRadius > 0) { "Cylinder finish requires positive radius" }
            }
        }
    }

    /**
     * Get the effective radius for distance calculations
     */
    fun getEffectiveRadius(): Double {
        return when (type) {
            RacingFinishType.FINISH_LINE -> lineLength ?: 1000.0
            RacingFinishType.FINISH_CYLINDER -> cylinderRadius ?: 1000.0
        }
    }
}

/**
 * Racing start types
 */
enum class RacingStartType {
    START_LINE,         // Start line perpendicular to first leg
    START_CYLINDER,     // Start cylinder around start point
    FAI_START_SECTOR    // FAI 90° start sector (D-shaped) facing away from first leg
}

/**
 * Racing finish types
 */
enum class RacingFinishType {
    FINISH_LINE,    // Finish line perpendicular to last leg
    FINISH_CYLINDER // Finish cylinder around finish point
}

/**
 * Racing task status enumeration
 */
enum class RacingTaskStatus {
    DRAFT,      // Task being created/edited
    READY,      // Task ready for use
    ACTIVE,     // Task currently in use
    COMPLETED,  // Task completed
    ARCHIVED    // Task archived
}

/**
 * Task validation result for racing tasks
 */
data class RacingTaskValidation(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val taskDistance: RacingTaskDistance? = null
) {
    companion object {
        fun valid(taskDistance: RacingTaskDistance? = null) =
            RacingTaskValidation(true, taskDistance = taskDistance)

        fun invalid(vararg errors: String) =
            RacingTaskValidation(false, errors.toList())

        fun validWithWarnings(taskDistance: RacingTaskDistance, vararg warnings: String) =
            RacingTaskValidation(true, emptyList(), warnings.toList(), taskDistance)
    }
}

/**
 * Racing task distance information
 */
data class RacingTaskDistance(
    val totalDistance: Double,           // meters - optimal FAI racing distance
    val startToFirstTurnpoint: Double,   // meters
    val turnpointDistances: List<Double>, // meters - distances between turnpoints
    val lastTurnpointToFinish: Double,   // meters
    val nominalDistance: Double          // meters - center-to-center distance
) {
    /**
     * Get total distance in kilometers
     */
    fun getTotalDistanceKm(): Double = totalDistance / 1000.0

    /**
     * Get nominal distance in kilometers
     */
    fun getNominalDistanceKm(): Double = nominalDistance / 1000.0

    /**
     * Get formatted total distance
     */
    fun getTotalDistanceFormatted(): String {
        return String.format("%.2f km", getTotalDistanceKm())
    }

    /**
     * Get all segment distances
     */
    fun getAllSegmentDistances(): List<Double> {
        val segments = mutableListOf<Double>()
        segments.add(startToFirstTurnpoint)
        segments.addAll(turnpointDistances)
        segments.add(lastTurnpointToFinish)
        return segments
    }
}

/**
 * Racing task result/performance data
 */
data class RacingTaskResult(
    val taskId: String,
    val pilotName: String,
    val startTime: LocalDateTime?,
    val finishTime: LocalDateTime?,
    val turnpointTimes: List<LocalDateTime>,
    val actualDistance: Double, // meters
    val averageSpeed: Double?, // km/h
    val isCompleted: Boolean,
    val penalties: List<RacingPenalty> = emptyList()
) {
    /**
     * Calculate task duration
     */
    fun getTaskDuration(): java.time.Duration? {
        return if (startTime != null && finishTime != null) {
            java.time.Duration.between(startTime, finishTime)
        } else null
    }

    /**
     * Get formatted duration
     */
    fun getDurationFormatted(): String {
        val duration = getTaskDuration() ?: return "N/A"
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}

/**
 * Racing penalty types
 */
data class RacingPenalty(
    val type: RacingPenaltyType,
    val points: Int,
    val description: String
)

enum class RacingPenaltyType {
    AIRSPACE_INFRINGEMENT,
    START_HEIGHT_EXCEEDED,
    MISSED_TURNPOINT,
    DANGEROUS_FLYING,
    OTHER
}
