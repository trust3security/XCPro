package com.trust3.xcpro.tasks.domain

import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.domain.logic.TaskProximityEvaluator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskProximityEvaluatorTest {

    private val evaluator = TaskProximityEvaluator()

    @Test
    fun `racing turnpoint outside entry buffer does not enter`() {
        val metersAway = 1_000.0
        val latOffset = metersAway / METERS_PER_LAT_DEGREE

        val decision = evaluator.evaluate(
            taskType = TaskType.RACING,
            waypointRole = WaypointRole.TURNPOINT,
            aircraftLat = latOffset,
            aircraftLon = 0.0,
            targetLat = 0.0,
            targetLon = 0.0
        )

        assertFalse(decision.hasEnteredObservationZone)
        assertFalse(decision.isCloseToTarget)
    }

    @Test
    fun `aat turnpoint enters zone but not close when inside area radius`() {
        val metersAway = 3_000.0
        val latOffset = metersAway / METERS_PER_LAT_DEGREE

        val decision = evaluator.evaluate(
            taskType = TaskType.AAT,
            waypointRole = WaypointRole.TURNPOINT,
            aircraftLat = latOffset,
            aircraftLon = 0.0,
            targetLat = 0.0,
            targetLon = 0.0
        )

        assertTrue(decision.hasEnteredObservationZone)
        assertFalse(decision.isCloseToTarget)
    }

    private companion object {
        const val METERS_PER_LAT_DEGREE = 111_320.0
    }
}
