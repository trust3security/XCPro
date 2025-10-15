# AAT (Assigned Area Task) Implementation Specification

## Project Context
- Android gliding app with existing Racing Task implementation
- AAT must be COMPLETELY SEPARATE from racing task
- No shared calculations or dependencies between task types
- Located at: /app/src/main/java/com/example/baseui1/tasks/aat/

## What is an AAT?
Assigned Area Task is a competition format where:
- Pilots fly through large areas (not fixed points)
- Pilot chooses optimal path within each area
- Must complete task in minimum time (2-4 hours typically)
- Winner has highest speed after minimum time
- Fundamentally different from racing tasks

## FAI Requirements (from SC3 Annex A Section 6.3.2 and 8.4.2)
- Minimum task time must be specified
- Areas achieved in sequence
- Speed = distance / MAX(elapsed_time, minimum_time)
- Finishers get same distance points
- Areas must be separated by at least 1km

## CRITICAL ARCHITECTURE REQUIREMENT
**AAT module must be 100% autonomous with ZERO dependencies on other modules**

## Required File Structure
/app/src/main/java/com/example/baseui1/tasks/
├── TaskManager.kt (coordinator only - NO calculations)
├── racing/ (existing - DO NOT MODIFY)
└── aat/ (NEW - COMPLETELY AUTONOMOUS)
├── AATTaskCalculator.kt
├── AATTaskDisplay.kt
├── AATTaskValidator.kt
├── AATPathOptimizer.kt
├── models/
│   ├── AATTask.kt
│   ├── AssignedArea.kt
│   └── AATResult.kt
├── calculations/
│   ├── AATDistanceCalculator.kt
│   ├── AATGeometry.kt
│   ├── AATMathUtils.kt
│   └── AATSpeedCalculator.kt
└── areas/
├── CircleAreaCalculator.kt
├── SectorAreaCalculator.kt
└── AreaBoundaryCalculator.kt
## Core Data Models
```kotlin
// AATTask.kt
data class AATTask(
    val id: String,
    val name: String,
    val minimumTaskTime: Duration,
    val start: AATStartPoint,
    val assignedAreas: List<AssignedArea>,
    val finish: AATFinishPoint,
    val maxStartAltitude: Int? = null
)

// AssignedArea.kt
data class AssignedArea(
    val name: String,
    val centerPoint: LatLng,
    val geometry: AreaGeometry
)

sealed class AreaGeometry {
    data class Circle(
        val radius: Double  // meters
    ) : AreaGeometry()
    
    data class Sector(
        val innerRadius: Double?,
        val outerRadius: Double,
        val startBearing: Double,
        val endBearing: Double
    ) : AreaGeometry()
}

// AATResult.kt
data class AATResult(
    val actualDistance: Double,
    val elapsedTime: Duration,
    val scoringTime: Duration,
    val averageSpeed: Double,
    val creditedFixes: List<LatLng>
)
// AATMathUtils.kt - Own implementation, no shared code
object AATMathUtils {
    const val AAT_EARTH_RADIUS_M = 6371000.0
    
    fun calculateDistance(from: LatLng, to: LatLng): Double {
        // AAT's own haversine implementation
    }
    
    fun calculateBearing(from: LatLng, to: LatLng): Double {
        // AAT's own bearing calculation
    }
    
    fun interpolatePosition(p1: LatLng, p2: LatLng, fraction: Double): LatLng {
        // AAT's own interpolation
    }
}

// AATDistanceCalculator.kt
class AATDistanceCalculator {
    fun calculateMinimumDistance(task: AATTask): Double {
        // Shortest path through nearest edge of each area
    }
    
    fun calculateMaximumDistance(task: AATTask): Double {
        // Longest path through farthest edge of each area
    }
    
    fun calculateNominalDistance(task: AATTask): Double {
        // Path through center of each area
    }
    
    fun calculateActualDistance(
        task: AATTask,
        creditedFixes: List<LatLng>
    ): Double {
        // Distance through pilot's chosen points
    }
}

// AATPathOptimizer.kt
class AATPathOptimizer {
    fun calculateOptimalPath(
        task: AATTask,
        currentPosition: LatLng,
        elapsedTime: Duration,
        expectedSpeed: Double
    ): List<LatLng> {
        // Calculate optimal points in each area
        // to finish just after minimum time
    }
}
// CircleAreaCalculator.kt
class CircleAreaCalculator {
    fun isInsideArea(point: LatLng, center: LatLng, radius: Double): Boolean
    fun nearestPointOnBoundary(from: LatLng, center: LatLng, radius: Double): LatLng
    fun farthestPointOnBoundary(from: LatLng, center: LatLng, radius: Double): LatLng
    fun calculateCreditedFix(track: List<LatLng>, area: AssignedArea): LatLng?
}

// SectorAreaCalculator.kt
class SectorAreaCalculator {
    fun isInsideArea(
        point: LatLng, 
        center: LatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): Boolean
    
    fun nearestPointOnBoundary(/* params */): LatLng
    fun farthestPointOnBoundary(/* params */): LatLng
    fun calculateCreditedFix(track: List<LatLng>, area: AssignedArea): LatLng?
}
// AATTaskValidator.kt
class AATTaskValidator {
    fun validateTask(task: AATTask): List<ValidationError> {
        // Check minimum task time > 0
        // Check areas separated by >= 1km
        // Check area geometries valid
        // Check start/finish valid
    }
    
    fun validateFlight(
        task: AATTask,
        flightPath: List<LatLng>
    ): AATFlightValidation {
        // Check all areas achieved
        // Check sequence correct
        // Check start/finish valid
    }
}
// AATTaskDisplay.kt
class AATTaskDisplay {
    fun generateAreaPolygons(task: AATTask): List<Polygon>
    fun generateTaskPath(task: AATTask, creditedFixes: List<LatLng>): LineString
    fun calculateDisplayColors(): DisplayColors
    fun generateAreaLabels(task: AATTask): List<MapLabel>
}
val testAAT = AATTask(
    id = "AAT_TEST_001",
    name = "3-Hour AAT Sydney",
    minimumTaskTime = Duration.ofHours(3),
    start = AATStartPoint(
        position = LatLng(-33.9470, 150.7710),
        type = StartType.LINE,
        lineLength = 5000.0
    ),
    assignedAreas = listOf(
        AssignedArea(
            "Area North",
            LatLng(-33.7240, 150.6330),
            Circle(radius = 20000.0)  // 20km radius
        ),
        AssignedArea(
            "Area East",
            LatLng(-33.5970, 151.0250),
            Sector(
                innerRadius = 5000.0,
                outerRadius = 30000.0,
                startBearing = 45.0,
                endBearing = 135.0
            )
        ),
        AssignedArea(
            "Area South",
            LatLng(-33.8850, 151.2080),
            Circle(radius = 15000.0)  // 15km radius
        )
    ),
    finish = AATFinishPoint(
        position = LatLng(-33.9470, 150.7710),
        type = FinishType.LINE,
        lineLength = 1000.0
    )
)
