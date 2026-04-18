package com.trust3.xcpro.tasks.aat.validation

import com.trust3.xcpro.tasks.aat.calculations.AATMathUtils
import com.trust3.xcpro.tasks.aat.models.AATFinishPoint
import com.trust3.xcpro.tasks.aat.models.AATFinishType
import com.trust3.xcpro.tasks.aat.models.AATLatLng
import com.trust3.xcpro.tasks.aat.models.AATStartPoint
import com.trust3.xcpro.tasks.aat.models.AATStartType
import com.trust3.xcpro.tasks.aat.models.AATTask
import com.trust3.xcpro.tasks.aat.models.AreaGeometry
import com.trust3.xcpro.tasks.aat.models.AssignedArea
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class AATFlightPathValidatorUnitsTest {

    private val validator = AATFlightPathValidator()

    @Test
    fun validate_flagsStartLineDistance_usingMeters() {
        val task = buildValidationTask()
        val farStartPoint = AATMathUtils.calculatePointAtBearing(task.start.position, 90.0, 1.5)

        val validation = validator.validate(
            task = task,
            flightPath = listOf(
                farStartPoint,
                task.assignedAreas.first().centerPoint,
                task.finish.position
            ),
            elapsedTime = task.minimumTaskTime
        )

        assertTrue(
            "Expected start-line meter-threshold error",
            validation.errors.any { it.contains("Start too far from start line") }
        )
    }

    @Test
    fun validate_flagsFinishLineDistance_usingMeters() {
        val task = buildValidationTask()
        val farFinishPoint = AATMathUtils.calculatePointAtBearing(task.finish.position, 90.0, 1.5)

        val validation = validator.validate(
            task = task,
            flightPath = listOf(
                task.start.position,
                task.assignedAreas.first().centerPoint,
                farFinishPoint
            ),
            elapsedTime = task.minimumTaskTime
        )

        assertTrue(
            "Expected finish-line meter-threshold error",
            validation.errors.any { it.contains("Finish too far from finish line") }
        )
    }

    private fun buildValidationTask(): AATTask {
        val start = AATLatLng(0.0, 0.0)
        val finish = AATMathUtils.calculatePointAtBearing(start, 0.0, 3.0)
        val areaCenter = AATMathUtils.calculatePointAtBearing(start, 0.0, 1.5)

        return AATTask(
            id = "flight-path-units",
            name = "Flight Path Units",
            minimumTaskTime = Duration.ofHours(1),
            start = AATStartPoint(
                position = start,
                type = AATStartType.LINE,
                lineLength = 1000.0
            ),
            assignedAreas = listOf(
                AssignedArea(
                    name = "Area 1",
                    centerPoint = areaCenter,
                    geometry = AreaGeometry.Circle(radius = 1500.0),
                    sequence = 0
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
