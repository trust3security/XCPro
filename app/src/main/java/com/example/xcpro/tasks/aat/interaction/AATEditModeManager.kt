package com.example.xcpro.tasks.aat.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.map.AATMovablePointManager
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import android.graphics.Color
import kotlin.math.*

/**
 * AAT Edit Mode Manager
 *
 * Manages interactive editing features for AAT tasks:
 * - Area tap detection
 * - Edit mode state
 * - Edit overlay rendering (highlighted areas)
 * - Target point dragging and updates
 *
 * REFACTORED FROM: AATTaskManager.kt (Stage 4 - Edit Mode Extraction)
 * DEPENDENCIES: SimpleAATTask, AATWaypoint, MapLibre
 */
class AATEditModeManager {

    // Edit mode state
    private var _isInEditMode by mutableStateOf(false)
    private var _editWaypointIndex by mutableStateOf(-1)

    val isInEditMode: Boolean get() = _isInEditMode
    val editWaypointIndex: Int? get() = if (_isInEditMode && _editWaypointIndex >= 0) _editWaypointIndex else null

    /**
     * Check if a map tap hit an AAT area
     *
     * Iterates through all task waypoints and checks if the tap location
     * falls within any assigned area radius. For keyhole areas, checks both
     * the inner cylinder and outer sector extension.
     *
     * @param task The current task
     * @param lat Tap latitude
     * @param lon Tap longitude
     * @return Pair of (waypointIndex, waypoint) if hit, null otherwise
     */
    fun checkAreaTap(task: SimpleAATTask, lat: Double, lon: Double): Pair<Int, AATWaypoint>? {
        if (task.waypoints.isEmpty()) {
            return null
        }

        task.waypoints.forEachIndexed { index, waypoint ->
            val distance = haversineDistance(lat, lon, waypoint.lat, waypoint.lon)

            // ✅ FIX: Check area based on shape type
            val isInArea = when (waypoint.assignedArea.shape) {
                com.example.xcpro.tasks.aat.models.AATAreaShape.CIRCLE -> {
                    // Simple circle check
                    val radiusKm = waypoint.assignedArea.radiusMeters / 1000.0
                    distance <= radiusKm
                }
                com.example.xcpro.tasks.aat.models.AATAreaShape.SECTOR -> {
                    // Sector or Keyhole: Check if in inner cylinder OR outer sector
                    val innerRadiusKm = waypoint.assignedArea.innerRadiusMeters / 1000.0
                    val outerRadiusKm = waypoint.assignedArea.outerRadiusMeters / 1000.0

                    if (innerRadiusKm > 0.0) {
                        // KEYHOLE: Check inner cylinder OR sector
                        if (distance <= innerRadiusKm) {
                            true // Inside inner cylinder (always valid)
                        } else if (distance <= outerRadiusKm) {
                            // Check if within sector angles
                            val bearing = calculateBearing(waypoint.lat, waypoint.lon, lat, lon)
                            isAngleInSector(bearing, waypoint.assignedArea.startAngleDegrees, waypoint.assignedArea.endAngleDegrees)
                        } else {
                            false
                        }
                    } else {
                        // SECTOR: Check if within outer radius AND sector angles
                        if (distance <= outerRadiusKm) {
                            val bearing = calculateBearing(waypoint.lat, waypoint.lon, lat, lon)
                            isAngleInSector(bearing, waypoint.assignedArea.startAngleDegrees, waypoint.assignedArea.endAngleDegrees)
                        } else {
                            false
                        }
                    }
                }
                com.example.xcpro.tasks.aat.models.AATAreaShape.LINE -> {
                    // Line check (for start/finish)
                    val halfWidth = (waypoint.assignedArea.lineWidthMeters / 1000.0) / 2.0
                    distance <= halfWidth
                }
            }

            if (isInArea) {
                println("🎯 AAT EDIT MODE: Tap detected in ${waypoint.title} area (${String.format("%.2f", distance)}km from center, shape: ${waypoint.assignedArea.shape})")
                return Pair(index, waypoint)
            }
        }

        return null
    }

    /**
     * Calculate bearing from point1 to point2 in degrees (0-360)
     */
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    /**
     * Check if an angle falls within a sector defined by start and end angles
     */
    private fun isAngleInSector(angle: Double, startAngle: Double, endAngle: Double): Boolean {
        val normalizedAngle = (angle + 360.0) % 360.0
        val normalizedStart = (startAngle + 360.0) % 360.0
        val normalizedEnd = (endAngle + 360.0) % 360.0

        return if (normalizedEnd >= normalizedStart) {
            // Normal case: sector doesn't cross 0°
            normalizedAngle >= normalizedStart && normalizedAngle <= normalizedEnd
        } else {
            // Sector crosses 0° (e.g., 350° to 10°)
            normalizedAngle >= normalizedStart || normalizedAngle <= normalizedEnd
        }
    }

    /**
     * Set edit mode for a specific waypoint
     *
     * Enables or disables edit mode for a waypoint, allowing the user
     * to interactively adjust the target point within the assigned area.
     *
     * @param waypointIndex The waypoint to edit (or -1 to disable)
     * @param enabled true to enable edit mode, false to disable
     */
    fun setEditMode(waypointIndex: Int, enabled: Boolean) {
        _isInEditMode = enabled
        _editWaypointIndex = if (enabled) waypointIndex else -1

        println("🎯 AAT EDIT MODE: Edit mode ${if (enabled) "enabled" else "disabled"} for waypoint $waypointIndex")
    }

    /**
     * Exit edit mode
     *
     * Convenience method to disable edit mode.
     */
    fun exitEditMode() {
        setEditMode(-1, false)
    }

    /**
     * Update target point position for interactive editing
     *
     * Moves a waypoint's target point to a new location, with automatic
     * boundary validation to keep it within the assigned area.
     *
     * @param task The current task
     * @param index The waypoint index to update
     * @param lat New target latitude
     * @param lon New target longitude
     * @return Updated waypoint with new target point
     */
    fun updateTargetPoint(task: SimpleAATTask, index: Int, lat: Double, lon: Double): AATWaypoint? {
        if (index >= task.waypoints.size) {
            println("❌ AAT EDIT MODE: Invalid waypoint index for target update: $index")
            return null
        }

        val waypoint = task.waypoints[index]
        val movablePointManager = AATMovablePointManager()

        // Update target point with boundary validation
        val updatedWaypoint = movablePointManager.moveTargetPoint(waypoint, lat, lon)

        println("🎯 AAT EDIT MODE: Target point updated for ${waypoint.title} - Lat: $lat, Lon: $lon")
        return updatedWaypoint
    }

    /**
     * Check if a map click hit a target point pin for drag handling
     *
     * Queries the map for features at the click location to determine
     * if a draggable target point pin was clicked.
     *
     * @param mapLibreMap The map instance
     * @param screenX Screen X coordinate of click
     * @param screenY Screen Y coordinate of click
     * @return Waypoint index if target point hit, null otherwise
     */
    fun checkTargetPointHit(mapLibreMap: MapLibreMap, screenX: Float, screenY: Float): Int? {
        try {
            val features = mapLibreMap.queryRenderedFeatures(
                android.graphics.PointF(screenX, screenY),
                "aat-target-points-layer"
            )

            if (features.isNotEmpty()) {
                val feature = features[0]
                val index = feature.getNumberProperty("index")?.toInt()
                println("🎯 AAT EDIT MODE: Target point hit detected at index $index")
                return index
            }
        } catch (e: Exception) {
            println("❌ AAT EDIT MODE: Error checking target point hit: ${e.message}")
        }
        return null
    }

    /**
     * Plot AAT edit overlay on map
     *
     * Shows highlighted area and draggable target point for the waypoint
     * currently being edited.
     *
     * @param mapLibreMap The map instance
     * @param task The current task
     * @param waypointIndex The waypoint to highlight
     */
    fun plotEditOverlay(mapLibreMap: MapLibreMap, task: SimpleAATTask, waypointIndex: Int) {
        try {
            println("🔍 AAT EDIT MODE: plotEditOverlay called for waypoint $waypointIndex")

            // CRITICAL: Wait for map style to be fully loaded
            if (mapLibreMap.style == null) {
                println("❌ AAT EDIT MODE: Map style is null - cannot plot edit overlay yet")
                // Retry after map style is loaded
                mapLibreMap.getStyle { style ->
                    println("🔄 AAT EDIT MODE: Map style loaded - retrying edit overlay plot")
                    plotEditOverlayWithStyle(style, task, waypointIndex)
                }
                return
            }

            plotEditOverlayWithStyle(mapLibreMap.style!!, task, waypointIndex)
        } catch (e: Exception) {
            println("❌ AAT EDIT MODE: Error in plotEditOverlay: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Internal method to plot edit overlay with guaranteed style availability
     */
    private fun plotEditOverlayWithStyle(style: Style, task: SimpleAATTask, waypointIndex: Int) {
        try {
            if (waypointIndex >= task.waypoints.size) {
                println("❌ AAT EDIT MODE: Invalid waypoint index for edit overlay: $waypointIndex")
                return
            }

            val waypoint = task.waypoints[waypointIndex]
            println("🎯 AAT EDIT MODE: Highlighting ${waypoint.title} at index $waypointIndex")
            println("🔍 AAT EDIT MODE: Waypoint shape: ${waypoint.assignedArea.shape}")

            // ✅ FIX: Create highlighted area overlay based on actual geometry type
            val highlightedGeometry = when (waypoint.assignedArea.shape) {
                com.example.xcpro.tasks.aat.models.AATAreaShape.CIRCLE -> {
                    // Circle/Cylinder
                    val radiusKm = waypoint.assignedArea.radiusMeters / 1000.0
                    println("🔍 AAT EDIT MODE: Generating CIRCLE highlight (${radiusKm}km)")
                    generateCircleCoordinates(waypoint.lat, waypoint.lon, radiusKm)
                }
                com.example.xcpro.tasks.aat.models.AATAreaShape.SECTOR -> {
                    // Sector or Keyhole
                    val innerRadiusKm = waypoint.assignedArea.innerRadiusMeters / 1000.0
                    val outerRadiusKm = waypoint.assignedArea.outerRadiusMeters / 1000.0
                    val startAngle = waypoint.assignedArea.startAngleDegrees
                    val endAngle = waypoint.assignedArea.endAngleDegrees

                    if (innerRadiusKm > 0.0) {
                        println("🔍 AAT EDIT MODE: Generating KEYHOLE highlight (inner:${innerRadiusKm}km, outer:${outerRadiusKm}km, ${startAngle}°-${endAngle}°)")
                    } else {
                        println("🔍 AAT EDIT MODE: Generating SECTOR highlight (${outerRadiusKm}km, ${startAngle}°-${endAngle}°)")
                    }

                    generateSectorCoordinates(
                        waypoint.lat, waypoint.lon,
                        innerRadiusKm, outerRadiusKm,
                        startAngle, endAngle
                    )
                }
                com.example.xcpro.tasks.aat.models.AATAreaShape.LINE -> {
                    // Line (shouldn't happen for turnpoints, but handle gracefully)
                    val radiusKm = waypoint.assignedArea.radiusMeters / 1000.0
                    println("🔍 AAT EDIT MODE: Generating CIRCLE highlight for LINE type (${radiusKm}km)")
                    generateCircleCoordinates(waypoint.lat, waypoint.lon, radiusKm)
                }
            }

            val editOverlayGeoJson = """
            {
                "type": "FeatureCollection",
                "features": [
                    {
                        "type": "Feature",
                        "properties": {
                            "title": "${waypoint.title} - EDIT MODE",
                            "type": "aat_edit_highlight",
                            "shape": "${waypoint.assignedArea.shape}"
                        },
                        "geometry": {
                            "type": "Polygon",
                            "coordinates": [[${highlightedGeometry.map { "[${it[0]}, ${it[1]}]" }.joinToString(", ")}]]
                        }
                    }
                ]
            }
            """.trimIndent()

            // CRITICAL: Clear old overlay first to prevent stale highlights
            clearEditOverlayInternal(style)

            // Add edit overlay source
            println("🔧 AAT EDIT MODE: Adding edit overlay source")
            style.addSource(GeoJsonSource("aat-edit-overlay", editOverlayGeoJson))

            // Add highlight fill layer
            println("🔧 AAT EDIT MODE: Adding highlight fill layer")
            style.addLayer(
                FillLayer("aat-edit-highlight", "aat-edit-overlay")
                    .withProperties(
                        PropertyFactory.fillColor("#FFD700"), // Gold highlight
                        PropertyFactory.fillOpacity(0.2f), // 50% more transparent so green shows through better
                        PropertyFactory.fillOutlineColor("#FFD700")
                    )
            )

            // Add highlight border layer
            println("🔧 AAT EDIT MODE: Adding highlight border layer")
            style.addLayer(
                LineLayer("aat-edit-border", "aat-edit-overlay")
                    .withProperties(
                        PropertyFactory.lineColor("#FFD700"),
                        PropertyFactory.lineWidth(4f), // Thicker border for visibility
                        PropertyFactory.lineOpacity(0.9f)
                    )
            )

            println("✅ AAT EDIT MODE: Edit overlay SUCCESSFULLY plotted for ${waypoint.title}")
        } catch (e: Exception) {
            println("❌ AAT EDIT MODE: Error in plotEditOverlayWithStyle: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Clear AAT edit overlay from map
     *
     * Removes the edit mode highlighting when exiting edit mode.
     *
     * @param mapLibreMap The map instance
     */
    fun clearEditOverlay(mapLibreMap: MapLibreMap) {
        try {
            println("🔍 AAT EDIT MODE: clearEditOverlay called")
            val style = mapLibreMap.style
            if (style == null) {
                println("⚠️ AAT EDIT MODE: Map style is null - cannot clear overlay")
                return
            }

            clearEditOverlayInternal(style)
            println("✅ AAT EDIT MODE: Edit overlay cleared successfully")
        } catch (e: Exception) {
            println("❌ AAT EDIT MODE: Error clearing edit overlay: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Internal method to clear edit overlay without fetching style
     */
    private fun clearEditOverlayInternal(style: Style) {
        try {
            // Remove edit overlay layers and source
            if (style.getLayer("aat-edit-highlight") != null) {
                style.removeLayer("aat-edit-highlight")
            }
            if (style.getLayer("aat-edit-border") != null) {
                style.removeLayer("aat-edit-border")
            }
            if (style.getSource("aat-edit-overlay") != null) {
                style.removeSource("aat-edit-overlay")
            }
        } catch (e: Exception) {
            println("⚠️ AAT EDIT MODE: Error clearing edit overlay: ${e.message}")
        }
    }

    // ==================== Geometry Helper (Private) ====================

    /**
     * Generate circle coordinates for edit overlay
     *
     * Simple circle generation for highlighting the editing area.
     * Note: This is a simplified version. In production, should use AATGeometryGenerator.
     */
    private fun generateCircleCoordinates(centerLat: Double, centerLon: Double, radiusKm: Double, points: Int = 64): List<List<Double>> {
        val earthRadius = 6371.0 // km
        val coords = mutableListOf<List<Double>>()

        for (i in 0 until points) {
            val angle = 2 * PI * i / points
            val lat = centerLat + (radiusKm / earthRadius) * (180 / PI) * cos(angle)
            val lon = centerLon + (radiusKm / earthRadius) * (180 / PI) * sin(angle) / cos(centerLat * PI / 180)
            coords.add(listOf(lon, lat)) // GeoJSON format: [longitude, latitude]
        }

        // Close the polygon
        if (coords.isNotEmpty()) {
            coords.add(coords[0])
        }

        return coords
    }

    /**
     * ✅ NEW: Generate sector/keyhole coordinates for edit overlay
     * Copied from AATTaskRenderer for consistency
     */
    private fun generateSectorCoordinates(
        centerLat: Double,
        centerLon: Double,
        innerRadiusKm: Double,
        outerRadiusKm: Double,
        startBearingDeg: Double,
        endBearingDeg: Double
    ): List<List<Double>> {
        val coords = mutableListOf<List<Double>>()

        if (innerRadiusKm > 0.0) {
            // ✅ KEYHOLE: Cylinder + sector extension (true keyhole shape 🔑)
            val cylinderPoints = 72
            val sectorPoints = 45

            // Step 1: Draw cylinder outline (the part NOT covered by sector)
            // Start from sector end, go around to sector start
            val startDrawAngle = endBearingDeg
            val endDrawAngle = startBearingDeg + 360.0

            for (i in 0..cylinderPoints) {
                val angleProgress = i.toDouble() / cylinderPoints
                val currentAngle = startDrawAngle + angleProgress * (endDrawAngle - startDrawAngle)
                val normalizedAngle = currentAngle % 360.0

                val point = calculateDestinationPoint(centerLat, centerLon, normalizedAngle, innerRadiusKm)
                coords.add(listOf(point.second, point.first))
            }

            // Step 2: Connect to sector outer boundary at start angle
            val sectorOuterStart = calculateDestinationPoint(centerLat, centerLon, startBearingDeg, outerRadiusKm)
            coords.add(listOf(sectorOuterStart.second, sectorOuterStart.first))

            // Step 3: Draw the sector outer arc
            for (i in 1 until sectorPoints) {
                val angleProgress = i.toDouble() / sectorPoints
                val angle = startBearingDeg + angleProgress * (endBearingDeg - startBearingDeg)
                val point = calculateDestinationPoint(centerLat, centerLon, angle, outerRadiusKm)
                coords.add(listOf(point.second, point.first))
            }

            // Step 4: Connect to sector outer boundary at end angle
            val sectorOuterEnd = calculateDestinationPoint(centerLat, centerLon, endBearingDeg, outerRadiusKm)
            coords.add(listOf(sectorOuterEnd.second, sectorOuterEnd.first))

            // Step 5: Connect back to cylinder edge at sector end angle (closes the keyhole)
            val cylinderSectorEnd = calculateDestinationPoint(centerLat, centerLon, endBearingDeg, innerRadiusKm)
            coords.add(listOf(cylinderSectorEnd.second, cylinderSectorEnd.first))

        } else {
            // ✅ SECTOR: No inner radius - standard sector from center
            val numPoints = 32

            // Calculate sector span
            val sectorSpan = if (endBearingDeg >= startBearingDeg) {
                endBearingDeg - startBearingDeg
            } else {
                360.0 - startBearingDeg + endBearingDeg
            }

            // Start from center
            coords.add(listOf(centerLon, centerLat))

            // Generate outer arc
            for (i in 0..numPoints) {
                val fraction = i.toDouble() / numPoints
                val bearing = if (endBearingDeg >= startBearingDeg) {
                    startBearingDeg + fraction * (endBearingDeg - startBearingDeg)
                } else {
                    (startBearingDeg + fraction * sectorSpan) % 360.0
                }
                val point = calculateDestinationPoint(centerLat, centerLon, bearing, outerRadiusKm)
                coords.add(listOf(point.second, point.first))
            }

            // Connect back to center
            coords.add(listOf(centerLon, centerLat))
        }

        // Close the polygon
        if (coords.isNotEmpty()) {
            coords.add(coords[0])
        }

        return coords
    }

    /**
     * ✅ NEW: Calculate destination point at given bearing and distance
     * Uses great-circle calculation for accuracy
     */
    private fun calculateDestinationPoint(
        centerLat: Double,
        centerLon: Double,
        bearingDeg: Double,
        distanceKm: Double
    ): Pair<Double, Double> {
        val earthRadiusKm = 6371.0
        val latRad = Math.toRadians(centerLat)
        val lonRad = Math.toRadians(centerLon)
        val bearingRad = Math.toRadians(bearingDeg)

        val newLatRad = asin(
            sin(latRad) * cos(distanceKm / earthRadiusKm) +
            cos(latRad) * sin(distanceKm / earthRadiusKm) * cos(bearingRad)
        )

        val newLonRad = lonRad + atan2(
            sin(bearingRad) * sin(distanceKm / earthRadiusKm) * cos(latRad),
            cos(distanceKm / earthRadiusKm) - sin(latRad) * sin(newLatRad)
        )

        return Pair(Math.toDegrees(newLatRad), Math.toDegrees(newLonRad))
    }

    /**
     * Calculate haversine distance between two points
     *
     * Used for area tap detection.
     */
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}
