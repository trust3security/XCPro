package com.example.xcpro.map.trail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnailTrailRenderPlannerTest {

    @Test
    fun plan_returnsNull_whenOff() {
        val planner = SnailTrailRenderPlanner(FakeMetersPerPixelProvider(5.0))

        val plan = planner.plan(
            SnailTrailRenderPlanner.Input(
                points = listOf(point(0.0, 0.0, 100L)),
                settings = TrailSettings(length = TrailLength.OFF),
                currentLocation = TrailGeoPoint(0.0, 0.0),
                currentTimeMillis = 100L,
                isCircling = false,
                currentZoom = 10f,
                isReplay = false,
                useRenderFrameSync = false,
                density = 2f
            )
        )

        assertNull(plan)
    }

    @Test
    fun plan_returnsNull_whenLocationInvalid() {
        val planner = SnailTrailRenderPlanner(FakeMetersPerPixelProvider(5.0))

        val plan = planner.plan(
            SnailTrailRenderPlanner.Input(
                points = listOf(point(0.0, 0.0, 100L)),
                settings = TrailSettings(),
                currentLocation = TrailGeoPoint(120.0, 0.0),
                currentTimeMillis = 100L,
                isCircling = false,
                currentZoom = 10f,
                isReplay = false,
                useRenderFrameSync = false,
                density = 2f
            )
        )

        assertNull(plan)
    }

    @Test
    fun plan_capsReplayMinDistance() {
        val planner = SnailTrailRenderPlanner(FakeMetersPerPixelProvider(1000.0))

        val plan = planner.plan(
            SnailTrailRenderPlanner.Input(
                points = listOf(point(0.0, 0.0, 100L), point(0.1, 0.1, 200L)),
                settings = TrailSettings(),
                currentLocation = TrailGeoPoint(0.0, 0.0),
                currentTimeMillis = 200L,
                isCircling = false,
                currentZoom = 10f,
                isReplay = true,
                useRenderFrameSync = false,
                density = 2f
            )
        )

        assertNotNull(plan)
        assertEquals(30.0, plan?.minDistanceMeters ?: -1.0, 0.0)
    }

    @Test
    fun plan_appendsLatestPointWhenDistanceFiltered() {
        val planner = SnailTrailRenderPlanner(FakeMetersPerPixelProvider(10000.0))
        val points = listOf(
            point(0.0, 0.0, 100L),
            point(0.0001, 0.0001, 200L)
        )

        val plan = planner.plan(
            SnailTrailRenderPlanner.Input(
                points = points,
                settings = TrailSettings(),
                currentLocation = TrailGeoPoint(0.0, 0.0),
                currentTimeMillis = 200L,
                isCircling = false,
                currentZoom = 10f,
                isReplay = false,
                useRenderFrameSync = false,
                density = 2f
            )
        )

        assertNotNull(plan)
        assertEquals(2, plan?.renderPoints?.size ?: 0)
        assertEquals(200L, plan?.renderPoints?.lastOrNull()?.timestampMillis ?: -1L)
    }

    @Test
    fun plan_useScaledLines_onlyWhenEnabledAndZoomedIn() {
        val baseInput = SnailTrailRenderPlanner.Input(
            points = listOf(point(0.0, 0.0, 100L), point(0.2, 0.2, 200L)),
            settings = TrailSettings(scalingEnabled = true),
            currentLocation = TrailGeoPoint(0.0, 0.0),
            currentTimeMillis = 200L,
            isCircling = false,
            currentZoom = 10f,
            isReplay = false,
            useRenderFrameSync = false,
            density = 2f
        )

        val planEnabled = SnailTrailRenderPlanner(FakeMetersPerPixelProvider(5000.0)).plan(baseInput)
        val planDisabled = SnailTrailRenderPlanner(FakeMetersPerPixelProvider(7000.0)).plan(baseInput)

        assertNotNull(planEnabled)
        assertNotNull(planDisabled)
        assertTrue(planEnabled?.styleCache?.useScaledLines == true)
        assertFalse(planDisabled?.styleCache?.useScaledLines == true)
    }

    private fun point(lat: Double, lon: Double, timeMillis: Long): TrailPoint = TrailPoint(
        latitude = lat,
        longitude = lon,
        timestampMillis = timeMillis,
        altitudeMeters = 500.0,
        varioMs = 1.0,
        driftFactor = 0.0,
        windSpeedMs = 0.0,
        windDirectionFromDeg = 0.0
    )

    private class FakeMetersPerPixelProvider(
        private val metersPerPixel: Double
    ) : MetersPerPixelProvider {
        override fun metersPerPixel(latitude: Double, zoom: Float): Double = metersPerPixel
    }
}
