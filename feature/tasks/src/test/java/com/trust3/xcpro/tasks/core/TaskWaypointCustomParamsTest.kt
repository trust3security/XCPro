package com.trust3.xcpro.tasks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskWaypointCustomParamsTest {

    @Test
    fun `aat task time apply removes maximum key when null`() {
        val destination = mutableMapOf<String, Any>(
            TaskWaypointParamKeys.AAT_MAXIMUM_TIME_SECONDS to 7200.0
        )

        AATTaskTimeCustomParams(
            minimumTimeSeconds = 3600.0,
            maximumTimeSeconds = null
        ).applyTo(destination)

        assertEquals(3600.0, destination[TaskWaypointParamKeys.AAT_MINIMUM_TIME_SECONDS] as Double, 0.0)
        assertFalse(destination.containsKey(TaskWaypointParamKeys.AAT_MAXIMUM_TIME_SECONDS))
    }

    @Test
    fun `aat waypoint params from uses fallbacks and derives customization`() {
        val parsed = AATWaypointCustomParams.from(
            source = emptyMap(),
            fallbackLat = 45.0,
            fallbackLon = 7.0,
            fallbackRadiusMeters = 3000.0
        )

        assertEquals(3000.0, parsed.radiusMeters, 0.0)
        assertEquals(3000.0, parsed.outerRadiusMeters, 0.0)
        assertEquals(45.0, parsed.targetLat, 0.0)
        assertEquals(7.0, parsed.targetLon, 0.0)
        assertFalse(parsed.isTargetPointCustomized)
    }

    @Test
    fun `aat waypoint params from honors explicit customization flag`() {
        val parsed = AATWaypointCustomParams.from(
            source = mapOf(
                TaskWaypointParamKeys.TARGET_LAT to 45.2,
                TaskWaypointParamKeys.TARGET_LON to 7.2,
                TaskWaypointParamKeys.IS_TARGET_POINT_CUSTOMIZED to false
            ),
            fallbackLat = 45.0,
            fallbackLon = 7.0,
            fallbackRadiusMeters = 3000.0
        )

        assertEquals(45.2, parsed.targetLat, 0.0)
        assertEquals(7.2, parsed.targetLon, 0.0)
        assertFalse(parsed.isTargetPointCustomized)
    }

    @Test
    fun `racing waypoint params round-trip through map`() {
        val expected = RacingWaypointCustomParams(
            keyholeInnerRadiusMeters = 800.0,
            keyholeAngle = 110.0,
            faiQuadrantOuterRadiusMeters = 12_000.0
        )
        val encoded = expected.toMap()
        val decoded = RacingWaypointCustomParams.from(encoded)

        assertFalse(encoded.containsKey(TaskWaypointParamKeys.LEGACY_KEYHOLE_INNER_RADIUS_KM))
        assertFalse(encoded.containsKey(TaskWaypointParamKeys.LEGACY_FAI_QUADRANT_OUTER_RADIUS_KM))
        assertEquals(expected, decoded)
    }

    @Test
    fun `racing waypoint params parse legacy km keys`() {
        val parsed = RacingWaypointCustomParams.from(
            mapOf(
                TaskWaypointParamKeys.LEGACY_KEYHOLE_INNER_RADIUS_KM to 0.5,
                TaskWaypointParamKeys.KEYHOLE_ANGLE to 90.0,
                TaskWaypointParamKeys.LEGACY_FAI_QUADRANT_OUTER_RADIUS_KM to 10.0
            )
        )

        assertEquals(500.0, parsed.keyholeInnerRadiusMeters, 0.0)
        assertEquals(10_000.0, parsed.faiQuadrantOuterRadiusMeters, 0.0)
    }

    @Test
    fun `racing waypoint params prefer meter keys over legacy km keys when both are present`() {
        val parsed = RacingWaypointCustomParams.from(
            mapOf(
                TaskWaypointParamKeys.KEYHOLE_INNER_RADIUS_METERS to 900.0,
                TaskWaypointParamKeys.FAI_QUADRANT_OUTER_RADIUS_METERS to 11_000.0,
                TaskWaypointParamKeys.LEGACY_KEYHOLE_INNER_RADIUS_KM to 0.5,
                TaskWaypointParamKeys.LEGACY_FAI_QUADRANT_OUTER_RADIUS_KM to 10.0,
                TaskWaypointParamKeys.KEYHOLE_ANGLE to 95.0
            )
        )

        assertEquals(900.0, parsed.keyholeInnerRadiusMeters, 0.0)
        assertEquals(11_000.0, parsed.faiQuadrantOuterRadiusMeters, 0.0)
        assertEquals(95.0, parsed.keyholeAngle, 0.0)
    }

    @Test
    fun `target state params parse with fallback values`() {
        val parsed = TargetStateCustomParams.from(
            source = emptyMap(),
            fallbackTargetParam = 0.65,
            fallbackTargetLocked = true
        )
        assertEquals(0.65, parsed.targetParam, 0.0)
        assertTrue(parsed.targetLocked)
        assertNull(parsed.targetLat)
        assertNull(parsed.targetLon)
    }

    @Test
    fun `persisted oz params parse and effective radius`() {
        val parsed = PersistedOzParams.from(
            mapOf(
                TaskWaypointParamKeys.OUTER_RADIUS_METERS to 5100.0,
                TaskWaypointParamKeys.INNER_RADIUS_METERS to 1200.0,
                TaskWaypointParamKeys.OZ_ANGLE_DEG to 95.0
            )
        )

        assertEquals(5100.0, parsed.effectiveRadiusMeters() ?: Double.NaN, 0.0)
        assertEquals(1200.0, parsed.innerRadiusMeters ?: Double.NaN, 0.0)
        assertEquals(95.0, parsed.angleDeg ?: Double.NaN, 0.0)
    }

    @Test
    fun `persisted oz params toMap emits only non null values`() {
        val map = PersistedOzParams(
            radiusMeters = 3000.0,
            angleDeg = 90.0
        ).toMap()

        assertEquals(3000.0, map[TaskWaypointParamKeys.RADIUS_METERS] ?: Double.NaN, 0.0)
        assertEquals(90.0, map[TaskWaypointParamKeys.OZ_ANGLE_DEG] ?: Double.NaN, 0.0)
        assertFalse(map.containsKey(TaskWaypointParamKeys.INNER_RADIUS_METERS))
        assertFalse(map.containsKey(TaskWaypointParamKeys.OZ_LENGTH_METERS))
    }

    @Test
    fun `racing start params round trip preserves gate tolerance and pev fields`() {
        val expected = RacingStartCustomParams(
            gateOpenTimeMillis = 10_000L,
            gateCloseTimeMillis = 20_000L,
            toleranceMeters = 500.0,
            preStartAltitudeMeters = 1500.0,
            altitudeReference = RacingAltitudeReference.QNH,
            directionOverrideDegrees = 145.0,
            maxStartAltitudeMeters = 2200.0,
            maxStartGroundspeedMs = 55.0,
            pev = RacingPevCustomParams(
                enabled = true,
                waitTimeMinutes = 5,
                startWindowMinutes = 6,
                maxPressesPerLaunch = 3,
                dedupeSeconds = 30L,
                minIntervalMinutes = 10,
                pressTimestampsMillis = listOf(1_000L, 5_000L)
            )
        )
        val destination = mutableMapOf<String, Any>()
        expected.applyTo(destination)
        val decoded = RacingStartCustomParams.from(destination)

        assertEquals(expected, decoded)
    }

    @Test
    fun `racing start params default tolerance when invalid value provided`() {
        val parsed = RacingStartCustomParams.from(
            mapOf(
                TaskWaypointParamKeys.START_TOLERANCE_METERS to -1.0
            )
        )

        assertEquals(500.0, parsed.toleranceMeters, 0.0)
    }

    @Test
    fun `racing finish params round trip preserves finish policy fields`() {
        val expected = RacingFinishCustomParams(
            closeTimeMillis = 30_000L,
            minAltitudeMeters = 900.0,
            altitudeReference = RacingAltitudeReference.QNH,
            directionOverrideDegrees = 180.0,
            allowStraightInBelowMinAltitude = true,
            requireLandWithoutDelay = true,
            landWithoutDelayWindowSeconds = 480L,
            landingSpeedThresholdMs = 4.0,
            landingHoldSeconds = 25L,
            contestBoundaryRadiusMeters = 2_000.0,
            stopPlusFiveEnabled = true,
            stopPlusFiveMinutes = 5L
        )
        val destination = mutableMapOf<String, Any>()
        expected.applyTo(destination)
        val decoded = RacingFinishCustomParams.from(destination)

        assertEquals(expected, decoded)
    }

    @Test
    fun `racing finish params clamp invalid values to safe defaults`() {
        val parsed = RacingFinishCustomParams.from(
            mapOf(
                TaskWaypointParamKeys.FINISH_LAND_WITHOUT_DELAY_WINDOW_SECONDS to -1L,
                TaskWaypointParamKeys.FINISH_LANDING_SPEED_THRESHOLD_MS to 0.0,
                TaskWaypointParamKeys.FINISH_LANDING_HOLD_SECONDS to 1L,
                TaskWaypointParamKeys.FINISH_STOP_PLUS_FIVE_MINUTES to 0L
            )
        )

        assertEquals(30L, parsed.landWithoutDelayWindowSeconds)
        assertEquals(0.5, parsed.landingSpeedThresholdMs, 0.0)
        assertEquals(5L, parsed.landingHoldSeconds)
        assertEquals(1L, parsed.stopPlusFiveMinutes)
    }
}
