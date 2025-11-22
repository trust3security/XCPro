package com.example.xcpro.tasks.domain

import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.logic.TaskValidator
import com.example.xcpro.tasks.domain.model.AnnularSectorOZ
import com.example.xcpro.tasks.domain.model.CylinderOZ
import com.example.xcpro.tasks.domain.model.GeoPoint
import com.example.xcpro.tasks.domain.model.LineOZ
import com.example.xcpro.tasks.domain.model.TaskPointDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskValidatorTest {

    private val validator = TaskValidator()

    private fun startPoint() = TaskPointDef(
        id = "s1",
        name = "Start",
        role = WaypointRole.START,
        location = GeoPoint(51.0, -1.0),
        zone = LineOZ()
    )

    private fun finishPoint() = TaskPointDef(
        id = "f1",
        name = "Finish",
        role = WaypointRole.FINISH,
        location = GeoPoint(51.5, -1.2),
        zone = CylinderOZ(radiusMeters = 500.0)
    )

    private fun aatTurnPoint() = TaskPointDef(
        id = "t1",
        name = "Turn 1",
        role = WaypointRole.TURNPOINT,
        location = GeoPoint(51.2, -1.1),
        zone = AnnularSectorOZ(innerRadiusMeters = 500.0, outerRadiusMeters = 5000.0, angleDeg = 90.0),
        allowsTarget = true
    )

    @Test
    fun `racing task validates with start and finish`() {
        val result = validator.validate(
            TaskType.RACING,
            listOf(startPoint(), finishPoint())
        )

        assertTrue(result is TaskValidator.ValidationResult.Valid)
        val summary = (result as TaskValidator.ValidationResult.Valid).summary
        assertEquals(2, summary.pointCount)
    }

    @Test
    fun `aat task requires target capable point`() {
        val result = validator.validate(
            TaskType.AAT,
            listOf(startPoint(), finishPoint())
        )

        assertTrue(result is TaskValidator.ValidationResult.Invalid)
        val errors = (result as TaskValidator.ValidationResult.Invalid).errors
        assertTrue(errors.any { it is TaskValidator.ValidationError.AATRequiresAdjustablePoint })
    }

    @Test
    fun `aat task passes with annular sector turnpoint`() {
        val result = validator.validate(
            TaskType.AAT,
            listOf(startPoint(), aatTurnPoint(), finishPoint())
        )

        assertTrue(result is TaskValidator.ValidationResult.Valid)
    }

    @Test
    fun `too many points fails`() {
        val many = buildList {
            add(startPoint())
            repeat(20) { idx ->
                add(
                    TaskPointDef(
                        id = "t$idx",
                        name = "Turn $idx",
                        role = WaypointRole.TURNPOINT,
                        location = GeoPoint(50.0 + idx, -1.0),
                        zone = CylinderOZ(radiusMeters = 300.0)
                    )
                )
            }
            add(finishPoint())
        }

        val result = validator.validate(TaskType.AAT, many)
        assertTrue(result is TaskValidator.ValidationResult.Invalid)
    }
}
