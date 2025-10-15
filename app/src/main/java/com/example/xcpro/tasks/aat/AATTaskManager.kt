package com.example.xcpro.tasks.aat

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import com.example.xcpro.SearchWaypoint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.expressions.Expression
import android.graphics.Color
import kotlin.math.*
import java.time.Duration
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// AAT-specific imports - NO cross-contamination with Racing/DHT
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.validation.AATValidationIntegration

// STAGE 7: Refactored module imports
import com.example.xcpro.tasks.aat.geometry.AATGeometryGenerator
import com.example.xcpro.tasks.aat.persistence.AATTaskFileIO
import com.example.xcpro.tasks.aat.navigation.AATNavigationManager
import com.example.xcpro.tasks.aat.interaction.AATEditModeManager
import com.example.xcpro.tasks.aat.validation.AATValidationBridge
import com.example.xcpro.tasks.aat.rendering.AATTaskRenderer
// STAGE 8: Waypoint management extraction
import com.example.xcpro.tasks.aat.waypoints.AATWaypointManager

/**
 * Simple AAT Task model for manager use (avoiding conflicts with complex models)
 */
data class SimpleAATTask(
    val id: String = "",
    val waypoints: List<AATWaypoint> = emptyList(),
    val minimumTime: Duration = Duration.ofHours(3),
    val maximumTime: Duration? = null
)

/**
 * AAT Task Manager - Completely independent AAT task management
 *
 * ZERO DEPENDENCIES on Racing or DHT modules - maintains complete separation
 * All AAT logic is self-contained within this manager
 */
class AATTaskManager(val context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("aat_task_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // STAGE 7: Refactored modules - delegate operations to specialized classes
    private val geometryGenerator = AATGeometryGenerator()
    private val fileIO = context?.let { AATTaskFileIO(it) }
    private val navigationManager = AATNavigationManager()
    private val editModeManager = AATEditModeManager()
    private val validationBridge = AATValidationBridge()
    private val renderer = AATTaskRenderer(geometryGenerator)
    // STAGE 8: Waypoint management module
    private val waypointManager = AATWaypointManager()
    // STAGE 9: Point type configuration, validation, and file operations wrappers
    private val pointTypeConfigurator = AATPointTypeConfigurator()
    private val validationWrapper = AATTaskValidationWrapper(validationBridge)
    private val fileOperationsWrapper = AATFileOperationsWrapper(fileIO)

    // AAT-specific calculator - completely autonomous
    private val aatTaskCalculator = AATTaskCalculator()

    // AAT task state - keep _currentLeg for compatibility with 13 references
    internal var _currentAATTask by mutableStateOf(SimpleAATTask())
    internal var _currentLeg by mutableStateOf(0)

    // Public properties
    val currentAATTask: SimpleAATTask get() = _currentAATTask
    val currentLeg: Int get() = _currentLeg

    /** Navigate to previous leg - STAGE 7: Delegate to NavigationManager */
    fun goToPreviousLeg() {
        navigationManager.goToPreviousLeg(_currentAATTask)
        _currentLeg = navigationManager.currentLeg
    }

    /** Navigate to next leg - STAGE 7: Delegate to NavigationManager */
    fun advanceToNextLeg() {
        navigationManager.advanceToNextLeg(_currentAATTask)
        _currentLeg = navigationManager.currentLeg
    }

    /** Initialize AAT task - STAGE 8: Delegate to AATWaypointManager */
    fun initializeAATTask(waypoints: List<SearchWaypoint>) {
        _currentAATTask = waypointManager.initializeTask(waypoints)
        saveAATTask()
    }

    /** Initialize from generic waypoints - STAGE 8: Delegate to AATWaypointManager */
    fun initializeFromGenericWaypoints(genericWaypoints: List<com.example.xcpro.tasks.TaskWaypoint>) {
        _currentAATTask = waypointManager.initializeFromGenericWaypoints(genericWaypoints)
        _currentLeg = 0
        saveAATTask()
    }

    /**
     * Calculate AAT task distance - FIXED: Uses optimal cylinder edge path
     * STAGE 8: Updated to use geometryGenerator module
     */
    fun calculateAATDistance(): Double {
        if (_currentAATTask.waypoints.size < 2) return 0.0

        // FIXED: Calculate distance using same path as visual display
        val pathPoints = geometryGenerator.calculateOptimalAATPath(_currentAATTask.waypoints)

        var totalDistance = 0.0
        for (i in 0 until pathPoints.size - 1) {
            val from = pathPoints[i] // [lon, lat]
            val to = pathPoints[i + 1] // [lon, lat]
            totalDistance += AATMathUtils.calculateDistanceKm(from[1], from[0], to[1], to[0]) // Convert back to lat, lon
        }
        return totalDistance
    }

    /** Add waypoint - STAGE 8: Delegate to AATWaypointManager */
    fun addAATWaypoint(searchWaypoint: SearchWaypoint) {
        _currentAATTask = waypointManager.addWaypoint(_currentAATTask, searchWaypoint)
        saveAATTask()
    }

    /** Remove waypoint - STAGE 8: Delegate to AATWaypointManager */
    fun removeAATWaypoint(index: Int) {
        val (updatedTask, newCurrentLeg) = waypointManager.removeWaypoint(_currentAATTask, _currentLeg, index)
        _currentAATTask = updatedTask
        _currentLeg = newCurrentLeg
        saveAATTask()
    }

    /** Update AAT area - STAGE 8: Delegate to AATWaypointManager */
    fun updateAATArea(index: Int, newArea: AATAssignedArea) {
        _currentAATTask = waypointManager.updateArea(_currentAATTask, index, newArea)
        saveAATTask()
    }

    /** Update task times - STAGE 8: Delegate to AATWaypointManager */
    fun updateAATTimes(minTime: Duration, maxTime: Duration?) {
        _currentAATTask = waypointManager.updateTimes(_currentAATTask, minTime, maxTime)
        saveAATTask()
    }

    /**
     * Update AAT waypoint point types
     * STAGE 9: Delegate to AATPointTypeConfigurator
     */
    fun updateAATWaypointPointType(
        index: Int,
        startType: com.example.xcpro.tasks.aat.models.AATStartPointType?,
        finishType: com.example.xcpro.tasks.aat.models.AATFinishPointType?,
        turnType: com.example.xcpro.tasks.aat.models.AATTurnPointType?,
        gateWidth: Double?,
        keyholeInnerRadius: Double?,
        keyholeAngle: Double?,
        sectorOuterRadius: Double?
    ) {
        val currentWaypoints = _currentAATTask.waypoints.toMutableList()
        if (index in currentWaypoints.indices) {
            val currentWaypoint = currentWaypoints[index]

            // Delegate complex geometry configuration to configurator
            val updatedWaypoint = pointTypeConfigurator.updateWaypointPointType(
                waypoint = currentWaypoint,
                allWaypoints = currentWaypoints,
                waypointIndex = index,
                startType = startType,
                finishType = finishType,
                turnType = turnType,
                gateWidth = gateWidth,
                keyholeInnerRadius = keyholeInnerRadius,
                keyholeAngle = keyholeAngle,
                sectorOuterRadius = sectorOuterRadius
            )

            currentWaypoints[index] = updatedWaypoint
            _currentAATTask = _currentAATTask.copy(waypoints = currentWaypoints)
            saveAATTask()
        } else {
            println("❌ AAT: Invalid waypoint index $index for point type update (valid range: 0-${currentWaypoints.size-1})")
        }
    }

    /** Plot AAT on map - STAGE 7: Delegate to AATTaskRenderer */
    fun plotAATOnMap(map: MapLibreMap?) {
        renderer.plotTaskOnMap(map, _currentAATTask, editModeManager.editWaypointIndex)
    }

    /** Clear AAT from map - STAGE 7: Delegate to AATTaskRenderer */
    fun clearAATFromMap(map: MapLibreMap?) {
        renderer.clearTaskFromMap(map)
    }

    // STAGE 7 CLEANUP: Old plotting/geometry functions removed (543 lines of dead code)
    // - clearAATLayers, plotAATWaypoints, plotAATAreas, plotAATTaskLine, plotAATTargetPointPins
    // - generateCircleCoordinates, convertToAATTask, calculateOptimalAATPath
    // - calculateDestinationPoint, calculateBearing, haversineDistance
    // - generateStartLine, generateFinishLine
    // All functionality now delegated to: AATTaskRenderer, AATGeometryGenerator, AATValidationBridge

    /** Clear AAT task */
    fun clearAATTask() {
        _currentAATTask = SimpleAATTask()
        _currentLeg = 0
        saveAATTask()
    }

    /** Reorder waypoints - STAGE 8: Delegate to AATWaypointManager */
    fun reorderAATWaypoints(fromIndex: Int, toIndex: Int) {
        _currentAATTask = waypointManager.reorderWaypoints(_currentAATTask, fromIndex, toIndex)
        saveAATTask()
    }

    /** Replace waypoint - STAGE 8: Delegate to AATWaypointManager */
    fun replaceAATWaypoint(index: Int, newWaypoint: SearchWaypoint) {
        _currentAATTask = waypointManager.replaceWaypoint(_currentAATTask, index, newWaypoint)
        saveAATTask()
    }


    /** Save to preferences - STAGE 9: Delegate to AATFileOperationsWrapper */
    fun saveAATTask() {
        fileOperationsWrapper.saveToPreferences(_currentAATTask)
    }

    /** Load from preferences - STAGE 9: Delegate to AATFileOperationsWrapper */
    fun loadAATTask(): SimpleAATTask? {
        return fileOperationsWrapper.loadFromPreferences()?.also { task ->
            _currentAATTask = task
        }
    }

    /**
     * Get AAT task summary
     */
    fun getAATTaskSummary(): String {
        val waypoints = _currentAATTask.waypoints.size
        val minTime = _currentAATTask.minimumTime.toHours()
        val maxTime = _currentAATTask.maximumTime?.toHours()

        return buildString {
            append("AAT Task: $waypoints waypoints")
            append(", Min: ${minTime}h")
            if (maxTime != null) {
                append(", Max: ${maxTime}h")
            }
        }
    }

    /**
     * Calculate AAT task distance (placeholder for now)
     */
    fun calculateAATTaskDistance(): Double {
        // FIXED: Use same optimal path calculation as visual display
        return calculateAATDistance()
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Use AAT's own math utilities for consistency
        return AATMathUtils.calculateDistanceKm(lat1, lon1, lat2, lon2)
    }

    /**
     * Calculate segment distance for routing from TaskManagerCoordinator
     * AAT-specific implementation using AATMathUtils
     *
     * @return Distance in kilometers
     */
    fun calculateSegmentDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return AATMathUtils.calculateDistanceKm(lat1, lon1, lat2, lon2)
    }

    /**
     * Calculate distance from GPS to target point of current waypoint
     *
     * CRITICAL: Uses TARGET POINT position for accurate AAT navigation!
     * Distance updates in real-time as pilot flies toward assigned area.
     *
     * @param gpsLat Current GPS latitude
     * @param gpsLon Current GPS longitude
     * @return Distance in km to current waypoint's target point, or null if no waypoint active
     */
    fun calculateDistanceToCurrentTargetPoint(gpsLat: Double, gpsLon: Double): Double? {
        return aatTaskCalculator.calculateDistanceToTargetPoint(
            gpsLat = gpsLat,
            gpsLon = gpsLon,
            waypointIndex = _currentLeg,
            waypoints = _currentAATTask.waypoints
        )
    }

    /**
     * File operations for AAT tasks
     * STAGE 9: Delegate to AATFileOperationsWrapper
     */
    fun getSavedAATTasks(context: Context): List<String> {
        return fileOperationsWrapper.getSavedTaskFiles()
    }

    fun saveAATTask(context: Context, taskName: String): Boolean {
        return fileOperationsWrapper.saveTaskToFile(_currentAATTask, taskName)
    }

    fun loadAATTaskFromFile(context: Context, taskName: String): Boolean {
        return fileOperationsWrapper.loadTaskFromFile(taskName)?.let { task ->
            _currentAATTask = task
            _currentLeg = 0
            saveAATTask()
            true
        } ?: false
    }

    fun deleteAATTask(context: Context, taskName: String): Boolean {
        return fileOperationsWrapper.deleteTaskFile(taskName)
    }

    // STAGE 7: CUP conversion functions moved to AATTaskFileIO module
    // STAGE 9: validateAndAdjustTargetPoint removed - target points now reset to center on type change

    /** Validate task (basic) - STAGE 9: Delegate to AATTaskValidationWrapper */
    fun isAATTaskValid(): Boolean = validationWrapper.isTaskValid(_currentAATTask)

    /** Validate task (comprehensive) - STAGE 9: Delegate to AATTaskValidationWrapper */
    fun validateAATTask(): com.example.xcpro.tasks.aat.validation.ValidationUIResult =
        validationWrapper.validateTask(_currentAATTask)

    /** Check competition readiness - STAGE 9: Delegate to AATTaskValidationWrapper */
    fun isCompetitionReady(): Boolean = validationWrapper.isCompetitionReady(_currentAATTask)

    /** Get task grade - STAGE 9: Delegate to AATTaskValidationWrapper */
    fun getTaskGrade(): String = validationWrapper.getTaskGrade(_currentAATTask)

    /** Get validation summary - STAGE 9: Delegate to AATTaskValidationWrapper */
    fun getValidationSummary(): com.example.xcpro.tasks.aat.validation.TaskValidationSummary =
        validationWrapper.getValidationSummary(_currentAATTask)

    /** Validate for competition - STAGE 9: Delegate to AATTaskValidationWrapper */
    fun validateForCompetition(competitionClass: String): com.example.xcpro.tasks.aat.validation.CompetitionValidationResult =
        validationWrapper.validateForCompetition(_currentAATTask, competitionClass)

    /** Get improvement suggestions - STAGE 9: Delegate to AATTaskValidationWrapper */
    fun getTaskImprovementSuggestions(): List<String> =
        validationWrapper.getTaskImprovementSuggestions(_currentAATTask)

    // STAGE 8: convertToAATTask() removed - validation uses AATValidationBridge which has its own conversion

    // STAGE 8: calculateOptimalAATPath() removed - use geometryGenerator.calculateOptimalPath() instead

    // STAGE 8: calculateDestinationPoint() removed - use geometryGenerator.calculateDestinationPoint() instead

    // STAGE 8: calculateBearing() removed - use geometryGenerator.calculateBearing() instead

    // STAGE 8: haversineDistance() removed - use AATMathUtils.calculateDistanceKm() instead

    // STAGE 8: generateStartLine() removed - use geometryGenerator.generateStartLine() instead

    // STAGE 8: generateFinishLine() removed - use geometryGenerator.generateFinishLine() instead

    // ==================== INTERACTIVE FEATURES ====================
    // STAGE 7: Edit mode state delegated to AATEditModeManager

    /** Check area tap - STAGE 7: Delegate to AATEditModeManager */
    fun checkAreaTap(lat: Double, lon: Double): Pair<Int, Any>? =
        editModeManager.checkAreaTap(_currentAATTask, lat, lon)

    /** Set edit mode - STAGE 7: Delegate to AATEditModeManager */
    fun setEditMode(waypointIndex: Int, enabled: Boolean) =
        editModeManager.setEditMode(waypointIndex, enabled)

    /** Check if in edit mode - STAGE 7: Delegate to AATEditModeManager */
    fun isInEditMode(): Boolean = editModeManager.isInEditMode

    /** Get edit waypoint index - STAGE 7: Delegate to AATEditModeManager */
    fun getEditWaypointIndex(): Int? = editModeManager.editWaypointIndex

    /** Plot edit overlay - STAGE 7: Delegate to AATEditModeManager */
    fun plotAATEditOverlay(mapLibreMap: MapLibreMap, waypointIndex: Int) =
        editModeManager.plotEditOverlay(mapLibreMap, _currentAATTask, waypointIndex)

    /** Clear edit overlay - STAGE 7: Delegate to AATEditModeManager */
    fun clearAATEditOverlay(mapLibreMap: MapLibreMap) =
        editModeManager.clearEditOverlay(mapLibreMap)

    /** Update target point - STAGE 7: Delegate to AATEditModeManager */
    fun updateTargetPoint(index: Int, lat: Double, lon: Double) {
        editModeManager.updateTargetPoint(_currentAATTask, index, lat, lon)?.let { updatedWaypoint ->
            val updatedWaypoints = _currentAATTask.waypoints.toMutableList()
            updatedWaypoints[index] = updatedWaypoint
            _currentAATTask = _currentAATTask.copy(waypoints = updatedWaypoints)
        }
    }

    /** Check target point hit - STAGE 7: Delegate to AATEditModeManager */
    fun checkTargetPointHit(mapLibreMap: MapLibreMap, screenX: Float, screenY: Float): Int? =
        editModeManager.checkTargetPointHit(mapLibreMap, screenX, screenY)

    // STAGE 9: calculateAngleBisector() and calculateTurnDirection() removed
    // Now in AATPointTypeConfigurator for geometry configuration
}

// AAT-specific enums moved to models/AATWaypoint.kt to avoid duplication