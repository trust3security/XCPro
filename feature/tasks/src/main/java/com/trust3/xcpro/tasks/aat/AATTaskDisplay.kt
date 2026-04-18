package com.trust3.xcpro.tasks.aat

import com.trust3.xcpro.tasks.aat.areas.AreaBoundaryCalculator
import com.trust3.xcpro.tasks.aat.models.AATLatLng
import com.trust3.xcpro.tasks.aat.models.AATTask
import com.trust3.xcpro.tasks.aat.models.AreaGeometry

/**
 * Display utilities for AAT tasks on maps.
 * This class is completely autonomous and handles all visualization
 * without dependencies on other task modules.
 * 
 * Generates geometric shapes and styling information for map display.
 */
class AATTaskDisplay {
    
    private val areaBoundaryCalculator = AreaBoundaryCalculator()
    private val geometryBuilder = AATTaskDisplayGeometryBuilder()
    
    /**
     * Generate polygon coordinates for all assigned areas in a task.
     * 
     * @param task The AAT task
     * @param pointsPerArea Number of points to generate per area boundary
     * @return List of polygons, each containing coordinate points
     */
    fun generateAreaPolygons(task: AATTask, pointsPerArea: Int = 36): List<DisplayPolygon> {
        return task.assignedAreas.map { area ->
            val boundaryPoints = areaBoundaryCalculator.generateBoundaryPoints(area, pointsPerArea)
            DisplayPolygon(
                name = area.name,
                points = boundaryPoints,
                areaType = when (area.geometry) {
                    is AreaGeometry.Circle -> "circle"
                    is AreaGeometry.Sector -> "sector"
                },
                centerPoint = area.centerPoint
            )
        }
    }
    
    /**
     * Generate task path line through specified waypoints.
     * 
     * @param task The AAT task
     * @param waypoints Points to connect (if empty, uses area centers)
     * @return Line string for map display
     */
    fun generateTaskPath(
        task: AATTask, 
        waypoints: List<AATLatLng> = emptyList()
    ): DisplayLineString {
        val pathPoints = mutableListOf<AATLatLng>()
        
        // Add start point
        pathPoints.add(task.start.position)
        
        // Add waypoints or area centers
        if (waypoints.isNotEmpty() && waypoints.size >= task.assignedAreas.size) {
            pathPoints.addAll(waypoints.take(task.assignedAreas.size))
        } else {
            pathPoints.addAll(task.assignedAreas.map { it.centerPoint })
        }
        
        // Add finish point
        pathPoints.add(task.finish.position)
        
        return DisplayLineString(
            name = "${task.name} Path",
            points = pathPoints,
            pathType = "task_path"
        )
    }
    
    /**
     * Generate start/finish markers.
     * 
     * @param task The AAT task
     * @return List of start and finish markers
     */
    fun generateStartFinishMarkers(task: AATTask): List<DisplayMarker> {
        val markers = mutableListOf<DisplayMarker>()
        
        // Start marker
        markers.add(
            DisplayMarker(
                name = "Start",
                position = task.start.position,
                type = "aat_start",
                details = mapOf(
                    "startType" to task.start.type.name,
                    "lineLength" to (task.start.lineLength?.toString() ?: ""),
                    "radius" to (task.start.radius?.toString() ?: "")
                )
            )
        )
        
        // Finish marker
        markers.add(
            DisplayMarker(
                name = "Finish",
                position = task.finish.position,
                type = "aat_finish",
                details = mapOf(
                    "finishType" to task.finish.type.name,
                    "lineLength" to (task.finish.lineLength?.toString() ?: ""),
                    "radius" to (task.finish.radius?.toString() ?: "")
                )
            )
        )
        
        return markers
    }
    
    /**
     * Generate area center markers with labels.
     * 
     * @param task The AAT task
     * @return List of area center markers
     */
    fun generateAreaMarkers(task: AATTask): List<DisplayMarker> {
        return task.assignedAreas.mapIndexed { index, area ->
            DisplayMarker(
                name = area.name,
                position = area.centerPoint,
                type = "aat_area_center",
                details = mapOf(
                    "sequence" to (index + 1).toString(),
                    "areaType" to when (area.geometry) {
                        is AreaGeometry.Circle -> "Circle (${String.format("%.1f", area.geometry.radius / 1000)}km)"
                        is AreaGeometry.Sector -> "Sector (${String.format("%.1f", area.geometry.outerRadius / 1000)}km)"
                    },
                    "areaSizeKm2" to String.format("%.1f", area.getApproximateAreaSizeKm2())
                )
            )
        }
    }
    
    /**
     * Generate start line or circle display geometry.
     * 
     * @param task The AAT task
     * @return Display geometry for start
     */
    fun generateStartGeometry(task: AATTask): DisplayGeometry? {
        return geometryBuilder.generateStartGeometry(task)
    }
    
    /**
     * Generate finish line or circle display geometry.
     * 
     * @param task The AAT task
     * @return Display geometry for finish
     */
    fun generateFinishGeometry(task: AATTask): DisplayGeometry? {
        return geometryBuilder.generateFinishGeometry(task)
    }
    
    /**
     * Calculate display colors and styling for AAT elements.
     * 
     * @return Display colors configuration
     */
    fun calculateDisplayColors(): DisplayColors {
        return DisplayColors(
            areaFill = "#388E3C33",      // Semi-transparent green (darker)
            areaBorder = "#388E3C",      // Green border (darker)
            taskPath = "#2196F3",        // Blue path
            startMarker = "#388E3C",     // Green start (darker)
            finishMarker = "#F44336",    // Red finish
            areaCenter = "#FF9800",      // Orange centers
            startFinishLine = "#9C27B0", // Purple lines/circles
            completedArea = "#81C78433", // Light green for completed
            activeArea = "#FFC10733",    // Light orange for active
            nextArea = "#E91E6333"       // Light red for next
        )
    }
    
    /**
     * Generate area labels for map display.
     * 
     * @param task The AAT task
     * @return List of map labels
     */
    fun generateAreaLabels(task: AATTask): List<MapLabel> {
        return task.assignedAreas.mapIndexed { index, area ->
            MapLabel(
                text = "${index + 1}. ${area.name}",
                position = area.centerPoint,
                type = "aat_area_label",
                priority = index + 1
            )
        }
    }
    
    /**
     * Generate optimal path visualization with different strategies.
     * 
     * @param task The AAT task
     * @param strategies List of strategy values (0.0 = min, 1.0 = max)
     * @return List of path visualizations
     */
    fun generateStrategyPaths(
        task: AATTask,
        strategies: List<Double> = listOf(0.0, 0.5, 1.0)
    ): List<DisplayLineString> {
        val pathOptimizer = AATPathOptimizer()
        
        return strategies.map { strategy ->
            val waypoints = pathOptimizer.calculateRecommendedPoints(
                task, task.start.position, strategy
            )
            
            val pathPoints = mutableListOf<AATLatLng>()
            pathPoints.add(task.start.position)
            pathPoints.addAll(waypoints)
            pathPoints.add(task.finish.position)
            
            DisplayLineString(
                name = when (strategy) {
                    0.0 -> "Minimum Distance Path"
                    1.0 -> "Maximum Distance Path"
                    else -> "Strategy ${String.format("%.1f", strategy)} Path"
                },
                points = pathPoints,
                pathType = "strategy_path_$strategy"
            )
        }
    }
    
    /**
     * Generate flight track visualization.
     * 
     * @param flightPath List of track points
     * @param task Associated task (for context)
     * @return Flight track display
     */
    fun generateFlightTrack(
        flightPath: List<AATLatLng>,
        task: AATTask? = null
    ): DisplayLineString {
        return DisplayLineString(
            name = "Flight Track",
            points = flightPath,
            pathType = "flight_track"
        )
    }
    
    /**
     * Generate credited fix markers.
     * 
     * @param creditedFixes List of credited fix points
     * @param task Associated task
     * @return List of credited fix markers
     */
    fun generateCreditedFixMarkers(
        creditedFixes: List<AATLatLng>,
        task: AATTask
    ): List<DisplayMarker> {
        return creditedFixes.mapIndexed { index, fix ->
            val areaName = if (index < task.assignedAreas.size) {
                task.assignedAreas[index].name
            } else {
                "Area ${index + 1}"
            }
            
            DisplayMarker(
                name = "Fix: $areaName",
                position = fix,
                type = "aat_credited_fix",
                details = mapOf(
                    "areaIndex" to index.toString(),
                    "areaName" to areaName
                )
            )
        }
    }
}
