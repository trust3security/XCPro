package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.areas.AreaBoundaryCalculator
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATFinishPoint
import com.example.xcpro.tasks.aat.models.AATFinishType
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATResult
import com.example.xcpro.tasks.aat.models.AATStartPoint
import com.example.xcpro.tasks.aat.models.AATStartType
import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AreaGeometry
import com.example.xcpro.tasks.aat.models.AssignedArea
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class AATTaskPerformanceSupportUnitsTest {

    @Test
    fun calculateAreaAchievementsForResult_populatesDistanceFromCenterInMeters() {
        val center = AATLatLng(0.0, 0.0)
        val creditedFix = AATMathUtils.calculatePointAtBearing(center, 90.0, 1.0)
        val task = buildTask(center)
        val result = AATResult(
            taskId = task.id,
            actualDistance = 2000.0,
            elapsedTime = Duration.ofHours(1),
            scoringTime = Duration.ofHours(1),
            averageSpeedMs = 2.0,
            creditedFixes = listOf(creditedFix)
        )

        val achievements = calculateAreaAchievementsForResult(
            result = result,
            task = task,
            areaBoundaryCalculator = AreaBoundaryCalculator()
        )

        assertEquals(1, achievements.size)
        assertEquals(1000.0, achievements.first().distanceFromCenter ?: 0.0, 5.0)
    }

    private fun buildTask(areaCenter: AATLatLng): AATTask {
        val start = AATMathUtils.calculatePointAtBearing(areaCenter, 270.0, 1.0)
        val finish = AATMathUtils.calculatePointAtBearing(areaCenter, 90.0, 1.0)

        return AATTask(
            id = "aat-performance-units",
            name = "AAT Performance Units",
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
                    geometry = AreaGeometry.Circle(radius = 2000.0),
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
