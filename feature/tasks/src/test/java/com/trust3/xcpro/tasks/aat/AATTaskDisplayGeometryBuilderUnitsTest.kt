package com.trust3.xcpro.tasks.aat

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

class AATTaskDisplayGeometryBuilderUnitsTest {

    private val geometryBuilder = AATTaskDisplayGeometryBuilder()

    @Test
    fun generateStartGeometry_lineUsesMeters() {
        val task = buildTaskWithLineStart()
        val geometry = geometryBuilder.generateStartGeometry(task) as DisplayGeometry.Line

        val d1 = AATMathUtils.calculateDistanceMeters(task.start.position, geometry.points[0])
        val d2 = AATMathUtils.calculateDistanceMeters(task.start.position, geometry.points[1])

        assertTrue(d1 in 490.0..510.0)
        assertTrue(d2 in 490.0..510.0)
    }

    @Test
    fun generateStartGeometry_circleUsesMeters() {
        val task = buildTaskWithCircleStart()
        val geometry = geometryBuilder.generateStartGeometry(task) as DisplayGeometry.Polygon

        val boundaryDistanceMeters =
            AATMathUtils.calculateDistanceMeters(task.start.position, geometry.points.first())

        assertTrue(boundaryDistanceMeters in 990.0..1010.0)
    }

    private fun buildTaskWithLineStart(): AATTask {
        val start = AATLatLng(0.0, 0.0)
        val firstAreaCenter = AATMathUtils.calculatePointAtBearing(start, 0.0, 2.0)
        val finish = AATMathUtils.calculatePointAtBearing(firstAreaCenter, 0.0, 2.0)

        return AATTask(
            id = "aat-display-line",
            name = "AAT Display Line",
            minimumTaskTime = Duration.ofHours(1),
            start = AATStartPoint(
                position = start,
                type = AATStartType.LINE,
                lineLength = 1000.0
            ),
            assignedAreas = listOf(
                AssignedArea(
                    name = "Area 1",
                    centerPoint = firstAreaCenter,
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

    private fun buildTaskWithCircleStart(): AATTask {
        val start = AATLatLng(0.0, 0.0)
        val firstAreaCenter = AATMathUtils.calculatePointAtBearing(start, 90.0, 2.0)
        val finish = AATMathUtils.calculatePointAtBearing(firstAreaCenter, 90.0, 2.0)

        return AATTask(
            id = "aat-display-circle",
            name = "AAT Display Circle",
            minimumTaskTime = Duration.ofHours(1),
            start = AATStartPoint(
                position = start,
                type = AATStartType.CIRCLE,
                radius = 1000.0
            ),
            assignedAreas = listOf(
                AssignedArea(
                    name = "Area 1",
                    centerPoint = firstAreaCenter,
                    geometry = AreaGeometry.Circle(radius = 500.0),
                    sequence = 0
                )
            ),
            finish = AATFinishPoint(
                position = finish,
                type = AATFinishType.CIRCLE,
                radius = 1000.0
            )
        )
    }
}
