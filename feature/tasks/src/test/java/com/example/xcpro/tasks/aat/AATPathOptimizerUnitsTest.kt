package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATFinishPoint
import com.example.xcpro.tasks.aat.models.AATFinishType
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATStartPoint
import com.example.xcpro.tasks.aat.models.AATStartType
import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AreaGeometry
import com.example.xcpro.tasks.aat.models.AssignedArea
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class AATPathOptimizerUnitsTest {

    @Test
    fun calculatePathDistance_returnsMeters() {
        val start = AATLatLng(0.0, 0.0)
        val oneKilometerEast = AATMathUtils.calculatePointAtBearing(start, 90.0, 1.0)

        val distanceMeters = AATPathOptimizerSupport.calculatePathDistanceMeters(
            listOf(start, oneKilometerEast)
        )

        assertEquals(1000.0, distanceMeters, 5.0)
    }

    @Test
    fun calculateRealTimeRecommendation_reportsMeters_whenAllAreasCompleted() {
        val task = buildTaskForPathTests()
        val optimizer = AATPathOptimizer()

        val recommendation = optimizer.calculateRealTimeRecommendation(
            task = task,
            currentPosition = task.start.position,
            elapsedTime = Duration.ZERO,
            groundSpeed = 90.0,
            areasCompleted = task.assignedAreas.size
        )

        assertEquals(2000.0, recommendation.distanceRemainingMeters, 10.0)
    }

    private fun buildTaskForPathTests(): AATTask {
        val start = AATLatLng(0.0, 0.0)
        val finish = AATMathUtils.calculatePointAtBearing(start, 90.0, 2.0)
        val areaCenter = AATMathUtils.calculatePointAtBearing(start, 90.0, 1.0)

        return AATTask(
            id = "path-units-task",
            name = "Path Units Task",
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
                    geometry = AreaGeometry.Circle(radius = 500.0),
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
