package com.trust3.xcpro.tasks.racing

import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.racing.models.RacingStartPointType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingTaskStructureRulesTest {

    @Test
    fun validate_strictProfile_acceptsStartTwoTurnpointsAndFinish() {
        val validation = RacingTaskStructureRules.validate(
            task = taskOf(
                waypoint("start", WaypointRole.START),
                waypoint("tp1", WaypointRole.TURNPOINT),
                waypoint("tp2", WaypointRole.TURNPOINT),
                waypoint("finish", WaypointRole.FINISH)
            )
        )

        assertTrue(validation.isValid)
    }

    @Test
    fun validate_strictProfile_rejectsSingleInteriorTurnpoint() {
        val validation = RacingTaskStructureRules.validate(
            task = taskOf(
                waypoint("start", WaypointRole.START),
                waypoint("tp1", WaypointRole.TURNPOINT),
                waypoint("finish", WaypointRole.FINISH)
            )
        )

        assertFalse(validation.isValid)
        assertTrue(
            validation.errors.any {
                it is RacingTaskStructureRules.ValidationError.NotEnoughInteriorTurnpoints
            }
        )
    }

    @Test
    fun validate_strictProfile_rejectsStartAndFinishOrderDrift() {
        val validation = RacingTaskStructureRules.validate(
            task = taskOf(
                waypoint("tp0", WaypointRole.TURNPOINT),
                waypoint("start", WaypointRole.START),
                waypoint("tp1", WaypointRole.TURNPOINT),
                waypoint("finish", WaypointRole.FINISH)
            )
        )

        assertFalse(validation.isValid)
        assertTrue(
            validation.errors.any {
                it is RacingTaskStructureRules.ValidationError.StartNotFirst
            }
        )
    }

    @Test
    fun validate_extendedProfile_allowsStartFinishOnly() {
        val validation = RacingTaskStructureRules.validate(
            task = taskOf(
                waypoint("start", WaypointRole.START),
                waypoint("finish", WaypointRole.FINISH)
            ),
            profile = RacingTaskStructureRules.Profile.XC_PRO_EXTENDED
        )

        assertTrue(validation.isValid)
    }

    @Test
    fun validate_strictProfile_rejectsCylinderStartType() {
        val validation = RacingTaskStructureRules.validate(
            task = taskOf(
                waypoint("start", WaypointRole.START, startType = RacingStartPointType.START_CYLINDER),
                waypoint("tp1", WaypointRole.TURNPOINT),
                waypoint("tp2", WaypointRole.TURNPOINT),
                waypoint("finish", WaypointRole.FINISH)
            )
        )

        assertFalse(validation.isValid)
        assertTrue(
            validation.errors.any {
                it is RacingTaskStructureRules.ValidationError.ProhibitedStartType
            }
        )
    }

    @Test
    fun validate_extendedProfile_allowsCylinderStartType() {
        val validation = RacingTaskStructureRules.validate(
            task = taskOf(
                waypoint("start", WaypointRole.START, startType = RacingStartPointType.START_CYLINDER),
                waypoint("finish", WaypointRole.FINISH)
            ),
            profile = RacingTaskStructureRules.Profile.XC_PRO_EXTENDED
        )

        assertTrue(validation.isValid)
    }

    private fun taskOf(vararg waypoints: TaskWaypoint): Task =
        Task(id = "task", waypoints = waypoints.toList())

    private fun waypoint(
        id: String,
        role: WaypointRole,
        startType: RacingStartPointType? = null
    ): TaskWaypoint =
        TaskWaypoint(
            id = id,
            title = id,
            subtitle = "",
            lat = 0.0,
            lon = 0.0,
            role = role,
            customPointType = if (role == WaypointRole.START) {
                startType?.name ?: RacingStartPointType.START_LINE.name
            } else {
                null
            }
        )
}
