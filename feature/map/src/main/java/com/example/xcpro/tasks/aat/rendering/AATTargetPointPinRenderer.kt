package com.example.xcpro.tasks.aat.rendering

import androidx.core.graphics.toColorInt
import com.example.xcpro.tasks.aat.models.AATWaypoint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

internal object AATTargetPointPinRenderer {

    fun plotTargetPointPins(
        style: Style,
        waypoints: List<AATWaypoint>,
        editModeWaypointIndex: Int?
    ) {
        try {
            val features = waypoints.mapIndexed { index, waypoint ->
                val isEditMode = editModeWaypointIndex == index
                """
                {
                    "type": "Feature",
                    "properties": {
                        "title": "${waypoint.title} Target Point",
                        "role": "${waypoint.role.name}",
                        "index": $index,
                        "draggable": true,
                        "type": "target_point",
                        "editMode": $isEditMode
                    },
                    "geometry": {
                        "type": "Point",
                        "coordinates": [${waypoint.targetPoint.longitude}, ${waypoint.targetPoint.latitude}]
                    }
                }
                """.trimIndent()
            }

            val geoJson = """
            {
                "type": "FeatureCollection",
                "features": [${features.joinToString(",")}]
            }
            """.trimIndent()

            if (style.getSource("aat-target-points") == null) {
                style.addSource(GeoJsonSource("aat-target-points", geoJson))
            } else {
                val existingSource = style.getSourceAs<GeoJsonSource>("aat-target-points")
                existingSource?.setGeoJson(geoJson)
            }

            try {
                if (style.getLayer("aat-target-points-layer") != null) {
                    style.removeLayer("aat-target-points-layer")
                }
            } catch (_: Exception) {
            }

            val colorExpression = Expression.switchCase(
                Expression.eq(Expression.get("role"), Expression.literal("START")),
                Expression.color("#388E3C".toColorInt()),
                Expression.eq(Expression.get("editMode"), Expression.literal(true)),
                Expression.color("#FF0000".toColorInt()),
                Expression.eq(Expression.get("role"), Expression.literal("FINISH")),
                Expression.color("#F44336".toColorInt()),
                Expression.color("#2196F3".toColorInt())
            )

            val radiusExpression = Expression.switchCase(
                Expression.eq(Expression.get("editMode"), Expression.literal(true)),
                Expression.literal(4f),
                Expression.literal(2f)
            )

            style.addLayer(
                CircleLayer("aat-target-points-layer", "aat-target-points")
                    .withProperties(
                        PropertyFactory.circleRadius(radiusExpression),
                        PropertyFactory.circleColor(colorExpression),
                        PropertyFactory.circleStrokeWidth(1f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleOpacity(0.9f)
                    )
            )
        } catch (_: Exception) {
        }
    }
}
