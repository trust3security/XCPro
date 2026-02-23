package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATFinishType
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATStartType
import com.example.xcpro.tasks.aat.models.AATTask

internal class AATTaskDisplayGeometryBuilder {
    fun generateStartGeometry(task: AATTask): DisplayGeometry? {
        return when (task.start.type) {
            AATStartType.LINE -> {
                task.start.lineLength?.let { length ->
                    generateLineGeometry(
                        center = task.start.position,
                        length = length,
                        bearing = calculatePerpendicularBearing(task)
                    )
                }
            }
            AATStartType.CIRCLE -> {
                task.start.radius?.let { radius ->
                    generateCircleGeometry(task.start.position, radius)
                }
            }
            AATStartType.BGA_SECTOR -> {
                task.start.sectorRadius?.let { radius ->
                    generateBGASectorGeometry(task.start.position, radius, task)
                }
            }
        }
    }

    fun generateFinishGeometry(task: AATTask): DisplayGeometry? {
        return when (task.finish.type) {
            AATFinishType.LINE -> {
                task.finish.lineLength?.let { length ->
                    generateLineGeometry(
                        center = task.finish.position,
                        length = length,
                        bearing = calculatePerpendicularBearing(task, isFinish = true)
                    )
                }
            }
            AATFinishType.CIRCLE -> {
                task.finish.radius?.let { radius ->
                    generateCircleGeometry(task.finish.position, radius)
                }
            }
        }
    }

    private fun calculatePerpendicularBearing(task: AATTask, isFinish: Boolean = false): Double {
        val bearing = if (isFinish && task.assignedAreas.isNotEmpty()) {
            AATMathUtils.calculateBearing(
                task.assignedAreas.last().centerPoint,
                task.finish.position
            )
        } else if (!isFinish && task.assignedAreas.isNotEmpty()) {
            AATMathUtils.calculateBearing(
                task.start.position,
                task.assignedAreas.first().centerPoint
            )
        } else {
            0.0
        }

        return (bearing + 90.0) % 360.0
    }

    private fun generateLineGeometry(
        center: AATLatLng,
        length: Double,
        bearing: Double
    ): DisplayGeometry {
        val halfLength = length / 2.0

        val point1 = AATMathUtils.calculatePointAtBearingMeters(
            center,
            bearing,
            halfLength
        )
        val point2 = AATMathUtils.calculatePointAtBearingMeters(
            center,
            (bearing + 180.0) % 360.0,
            halfLength
        )

        return DisplayGeometry.Line(
            points = listOf(point1, point2)
        )
    }

    private fun generateCircleGeometry(center: AATLatLng, radius: Double): DisplayGeometry {
        val points = mutableListOf<AATLatLng>()
        val numPoints = 36

        for (i in 0..numPoints) {
            val bearing = i * 360.0 / numPoints
            val point = AATMathUtils.calculatePointAtBearingMeters(
                center,
                bearing,
                radius
            )
            points.add(point)
        }

        return DisplayGeometry.Polygon(points = points)
    }

    private fun generateBGASectorGeometry(center: AATLatLng, radius: Double, task: AATTask): DisplayGeometry {
        val points = mutableListOf<AATLatLng>()

        val bisectorBearing = if (task.assignedAreas.isNotEmpty()) {
            AATMathUtils.calculateBearing(
                center,
                task.assignedAreas.first().centerPoint
            )
        } else {
            0.0
        }

        val startAngle = (bisectorBearing - 45.0 + 360.0) % 360.0
        val endAngle = (bisectorBearing + 45.0) % 360.0

        val numPoints = 32
        for (i in 0..numPoints) {
            val angle = startAngle + (endAngle - startAngle) * i / numPoints
            val point = AATMathUtils.calculatePointAtBearingMeters(
                center,
                angle,
                radius
            )
            points.add(point)
        }

        points.add(center)

        return DisplayGeometry.Polygon(points = points)
    }
}
