package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATFinishPoint
import com.example.xcpro.tasks.aat.models.AATFinishType
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATStartPoint
import com.example.xcpro.tasks.aat.models.AATStartType
import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AreaGeometry
import com.example.xcpro.tasks.aat.models.AssignedArea
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class AATTaskSafetyValidatorUnitsTest {

    private val validator = AATTaskSafetyValidator()

    @Test
    fun validate_doesNotFlagAreasVeryClose_whenSeparationIsTenKilometers() {
        val task = buildTaskWithAreaSeparationKilometers(10.0)

        val issues = validator.validate(task)

        assertFalse(issues.any { it.ruleId == "AREAS_VERY_CLOSE" })
    }

    @Test
    fun validate_flagsAreasVeryClose_whenSeparationIsFourKilometers() {
        val task = buildTaskWithAreaSeparationKilometers(4.0)

        val issues = validator.validate(task)

        assertTrue(issues.any { it.ruleId == "AREAS_VERY_CLOSE" })
    }

    private fun buildTaskWithAreaSeparationKilometers(separationKm: Double): AATTask {
        val start = AATLatLng(0.0, 0.0)
        val finish = AATMathUtils.calculatePointAtBearing(start, 0.0, 2.0)
        val area2Center = AATMathUtils.calculatePointAtBearing(start, 90.0, separationKm)

        return AATTask(
            id = "safety-units-$separationKm",
            name = "Safety Units $separationKm",
            minimumTaskTime = Duration.ofHours(1),
            start = AATStartPoint(
                position = start,
                type = AATStartType.LINE,
                lineLength = 1000.0
            ),
            assignedAreas = listOf(
                AssignedArea(
                    name = "Area 1",
                    centerPoint = start,
                    geometry = AreaGeometry.Circle(radius = 500.0),
                    sequence = 0
                ),
                AssignedArea(
                    name = "Area 2",
                    centerPoint = area2Center,
                    geometry = AreaGeometry.Circle(radius = 500.0),
                    sequence = 1
                )
            ),
            finish = AATFinishPoint(
                position = finish,
                type = AATFinishType.LINE,
                lineLength = 1000.0
            )
        )
    }
}
