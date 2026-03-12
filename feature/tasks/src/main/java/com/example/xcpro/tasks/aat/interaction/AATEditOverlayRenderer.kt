package com.example.xcpro.tasks.aat.interaction

import android.util.Log
import com.example.xcpro.tasks.BuildConfig
import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.models.AATAreaShape
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Renders and clears the AAT edit overlay on MapLibre.
 *
 * AI-NOTE: Map style availability is asynchronous; keep retries here to avoid UI-level polling.
 */
internal class AATEditOverlayRenderer(
    private val geometry: AATEditGeometry = AATEditGeometry
) {

    fun plotEditOverlay(mapLibreMap: MapLibreMap, task: SimpleAATTask, waypointIndex: Int) {
        try {

            // Map style may not be ready yet; retry once it's loaded.
            if (mapLibreMap.style == null) {
                mapLibreMap.getStyle { style ->
                    plotEditOverlayWithStyle(style, task, waypointIndex)
                }
                return
            }

            plotEditOverlayWithStyle(mapLibreMap.style!!, task, waypointIndex)
        } catch (e: Exception) {
            logRenderFailure("plotEditOverlay", e)
        }
    }

    fun clearEditOverlay(mapLibreMap: MapLibreMap) {
        try {
            val style = mapLibreMap.style
            if (style == null) {
                return
            }

            clearEditOverlayInternal(style)
        } catch (e: Exception) {
            logRenderFailure("clearEditOverlay", e)
        }
    }

    private fun plotEditOverlayWithStyle(style: Style, task: SimpleAATTask, waypointIndex: Int) {
        try {
            if (waypointIndex >= task.waypoints.size) {
                return
            }

            val waypoint = task.waypoints[waypointIndex]

            val highlightedGeometry = when (waypoint.assignedArea.shape) {
                AATAreaShape.CIRCLE -> {
                    geometry.generateCircleCoordinatesMeters(
                        waypoint.lat,
                        waypoint.lon,
                        waypoint.assignedArea.radiusMeters
                    )
                }
                AATAreaShape.SECTOR -> {
                    val innerRadiusMeters = waypoint.assignedArea.innerRadiusMeters
                    val outerRadiusMeters = waypoint.assignedArea.outerRadiusMeters
                    val startAngle = waypoint.assignedArea.startAngleDegrees
                    val endAngle = waypoint.assignedArea.endAngleDegrees

                    if (innerRadiusMeters > 0.0) {
                    } else {
                    }

                    geometry.generateSectorCoordinatesMeters(
                        waypoint.lat, waypoint.lon,
                        innerRadiusMeters, outerRadiusMeters,
                        startAngle, endAngle
                    )
                }
                AATAreaShape.LINE -> {
                    geometry.generateCircleCoordinatesMeters(
                        waypoint.lat,
                        waypoint.lon,
                        waypoint.assignedArea.radiusMeters
                    )
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

            // Clear old overlay first to prevent stale highlights.
            clearEditOverlayInternal(style)

            style.addSource(GeoJsonSource("aat-edit-overlay", editOverlayGeoJson))

            style.addLayer(
                FillLayer("aat-edit-highlight", "aat-edit-overlay")
                    .withProperties(
                        PropertyFactory.fillColor("#FFD700"),
                        PropertyFactory.fillOpacity(0.2f),
                        PropertyFactory.fillOutlineColor("#FFD700")
                    )
            )

            style.addLayer(
                LineLayer("aat-edit-border", "aat-edit-overlay")
                    .withProperties(
                        PropertyFactory.lineColor("#FFD700"),
                        PropertyFactory.lineWidth(4f),
                        PropertyFactory.lineOpacity(0.9f)
                    )
            )

        } catch (e: Exception) {
            logRenderFailure("plotEditOverlayWithStyle", e)
        }
    }

    private fun clearEditOverlayInternal(style: Style) {
        try {
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
        }
    }

    private fun logRenderFailure(stage: String, throwable: Throwable) {
        if (!BuildConfig.DEBUG) {
            return
        }
        val reason = throwable.message ?: throwable.javaClass.simpleName
        Log.w(TAG, "$stage failed ($reason)")
    }

    private companion object {
        const val TAG = "AATEditOverlayRenderer"
    }
}
