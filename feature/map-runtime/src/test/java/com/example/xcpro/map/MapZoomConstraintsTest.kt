package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapZoomConstraintsTest {

    @Test
    fun minScaleBarMeters_allowsCloserZoomFloor() {
        assertEquals(50.0, MapZoomConstraints.MIN_SCALE_BAR_METERS, 0.0)
    }

    @Test
    fun maxZoomForMinScaleMeters_allowsCloserZoomWhenScaleFloorIsSmaller() {
        val widthPx = 1080
        val currentZoom = 14.0
        val distancePerPixel = 0.8

        val previousFloorZoom = MapZoomConstraints.maxZoomForMinScaleMeters(
            widthPx = widthPx,
            currentZoom = currentZoom,
            distancePerPixel = distancePerPixel,
            minScaleMeters = 200.0
        ) ?: error("expected zoom")
        val closerFloorZoom = MapZoomConstraints.maxZoomForMinScaleMeters(
            widthPx = widthPx,
            currentZoom = currentZoom,
            distancePerPixel = distancePerPixel,
            minScaleMeters = MapZoomConstraints.MIN_SCALE_BAR_METERS
        ) ?: error("expected zoom")

        assertTrue(closerFloorZoom > previousFloorZoom)
    }
}
