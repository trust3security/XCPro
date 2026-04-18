package com.trust3.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.LineString

class OgnSelectedThermalOverlayFeatureTest {

    @Test
    fun buildSelectedThermalOverlayFeatures_emitsLoopCasingArrowAndMarkers() {
        val latestPoint = OgnThermalPoint(latitude = -35.0000, longitude = 149.0100)
        val features = buildSelectedThermalOverlayFeatures(
            SelectedOgnThermalOverlayContext(
                hotspotId = "thermal-1",
                snailColorIndex = 11,
                hotspotPoint = OgnThermalPoint(latitude = -35.0000, longitude = 149.0000),
                highlightedSegments = listOf(
                    OgnGliderTrailSegment(
                        id = "seg-1",
                        sourceTargetId = "pilot-1",
                        sourceLabel = "pilot-1",
                        startLatitude = -35.0000,
                        startLongitude = 149.0000,
                        endLatitude = -34.9950,
                        endLongitude = 149.0050,
                        colorIndex = 11,
                        widthPx = 2f,
                        timestampMonoMs = 100L
                    )
                ),
                occupancyHullPoints = listOf(
                    OgnThermalPoint(latitude = -35.0000, longitude = 149.0000),
                    OgnThermalPoint(latitude = -34.9950, longitude = 149.0050),
                    latestPoint
                ),
                startPoint = OgnThermalPoint(latitude = -35.0000, longitude = 149.0000),
                latestPoint = latestPoint
            )
        )

        assertEquals(1, features.count { it.getStringProperty("kind") == "hull" })
        assertEquals(1, features.count { it.getStringProperty("kind") == "loop_casing" })
        assertEquals(1, features.count { it.getStringProperty("kind") == "loop" })
        assertEquals(1, features.count { it.getStringProperty("kind") == "drift" })
        assertEquals(1, features.count { it.getStringProperty("kind") == "drift_arrow" })
        assertEquals(2, features.count { it.getStringProperty("kind") == "marker" })
        assertTrue(
            features.any {
                it.getStringProperty("kind") == "marker" &&
                    it.getStringProperty("marker_kind") == "start"
            }
        )
        assertTrue(
            features.any {
                it.getStringProperty("kind") == "marker" &&
                    it.getStringProperty("marker_kind") == "latest"
            }
        )

        val arrow = features.first { it.getStringProperty("kind") == "drift_arrow" }
        val arrowCoordinates = (arrow.geometry() as LineString).coordinates()
        assertEquals(latestPoint.longitude, arrowCoordinates[1].longitude(), 1e-6)
        assertEquals(latestPoint.latitude, arrowCoordinates[1].latitude(), 1e-6)
    }

    @Test
    fun buildSelectedThermalOverlayFeatures_suppressesDriftFeaturesWithoutMeaningfulMovement() {
        val samePoint = OgnThermalPoint(latitude = -35.0000, longitude = 149.0000)
        val features = buildSelectedThermalOverlayFeatures(
            SelectedOgnThermalOverlayContext(
                hotspotId = "thermal-2",
                snailColorIndex = 9,
                hotspotPoint = samePoint,
                highlightedSegments = emptyList(),
                occupancyHullPoints = emptyList(),
                startPoint = samePoint,
                latestPoint = samePoint
            )
        )

        assertEquals(0, features.count { it.getStringProperty("kind") == "drift" })
        assertEquals(0, features.count { it.getStringProperty("kind") == "drift_arrow" })
        assertEquals(2, features.count { it.getStringProperty("kind") == "marker" })
    }
}
