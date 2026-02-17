package com.example.xcpro.tasks.core

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
            keyholeInnerRadius = 0.8,
            keyholeAngle = 110.0,
            faiQuadrantOuterRadius = 12.0
        )

        val decoded = RacingWaypointCustomParams.from(expected.toMap())

        assertEquals(expected, decoded)
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
}
