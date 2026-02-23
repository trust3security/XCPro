package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.areas.AreaBoundaryCalculator
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATFinishPoint
import com.example.xcpro.tasks.aat.models.AATFinishType
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATStartPoint
import com.example.xcpro.tasks.aat.models.AATStartType
import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AreaGeometry
import com.example.xcpro.tasks.aat.models.AssignedArea
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class AATTaskQuickValidationEngineUnitsTest {

    private val validationEngine = AATTaskQuickValidationEngine(AreaBoundaryCalculator())

    @Test
    fun validateFlight_flagsStartLineDistance_usingMeters() {
        val task = buildValidationTask()
        val farStartPoint = AATMathUtils.calculatePointAtBearing(task.start.position, 90.0, 1.5)

        val validation = validationEngine.validateFlight(
            task = task,
            flightPath = listOf(
                farStartPoint,
                task.assignedAreas.first().centerPoint,
                task.finish.position
            )
        )

        assertTrue(
            "Expected start-line distance error in meters",
            validation.errors.any { it.contains("Start point too far from start line") }
        )
    }

    @Test
    fun validateFlight_flagsFinishLineDistance_usingMeters() {
        val task = buildValidationTask()
        val farFinishPoint = AATMathUtils.calculatePointAtBearing(task.finish.position, 90.0, 1.5)

        val validation = validationEngine.validateFlight(
            task = task,
            flightPath = listOf(
                task.start.position,
                task.assignedAreas.first().centerPoint,
                farFinishPoint
            )
        )

        assertTrue(
            "Expected finish-line distance error in meters",
            validation.errors.any { it.contains("Finish point too far from finish line") }
        )
    }

    @Test
    fun validateTaskQuick_warnsSmallArea_usingSquaredUnitLabel() {
        val validation = validationEngine.validateTaskQuick(buildValidationTask(areaRadiusMeters = 1500.0))

        assertTrue(
            "Expected small-area warning with squared unit label",
            validation.warnings.any { it.contains("very small") && it.contains("km2") }
        )
    }

    @Test
    fun validateTaskQuick_warnsLargeArea_usingSquaredUnitLabel() {
        val validation = validationEngine.validateTaskQuick(buildValidationTask(areaRadiusMeters = 50_000.0))

        assertTrue(
            "Expected large-area warning with squared unit label",
            validation.warnings.any { it.contains("very large") && it.contains("km2") }
        )
    }

    private fun buildValidationTask(areaRadiusMeters: Double = 1500.0): AATTask {
        val start = AATLatLng(0.0, 0.0)
        val finish = AATMathUtils.calculatePointAtBearing(start, 0.0, 3.0)
        val areaCenter = AATMathUtils.calculatePointAtBearing(start, 0.0, 1.5)

        return AATTask(
            id = "quick-validation-units",
            name = "Quick Validation Units",
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
                    geometry = AreaGeometry.Circle(radius = areaRadiusMeters),
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
