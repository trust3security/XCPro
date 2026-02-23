package com.example.xcpro.tasks.aat.calculations

import com.example.xcpro.tasks.aat.models.AATFinishPoint
import com.example.xcpro.tasks.aat.models.AATFinishType
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATStartPoint
import com.example.xcpro.tasks.aat.models.AATStartType
import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AreaGeometry
import com.example.xcpro.tasks.aat.models.AssignedArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class AATDistanceCalculatorUnitsTest {

    private val calculator = AATDistanceCalculator()
    private data class NominalDistanceFixture(
        val start: AATLatLng,
        val bearingDeg: Double,
        val firstLegKm: Double,
        val secondLegKm: Double
    )

    @Test
    fun calculateNominalDistance_returnsMeters() {
        val task = buildTask()

        val nominalDistanceMeters = calculator.calculateNominalDistance(task)

        assertEquals(2000.0, nominalDistanceMeters, 20.0)
    }

    @Test
    fun calculateLegDistances_returnsMeters() {
        val task = buildTask()
        val waypoints = listOf(
            task.start.position,
            task.assignedAreas.first().centerPoint,
            task.finish.position
        )

        val legDistancesMeters = calculator.calculateLegDistances(task, waypoints)

        assertEquals(2, legDistancesMeters.size)
        assertEquals(1000.0, legDistancesMeters[0], 10.0)
        assertEquals(1000.0, legDistancesMeters[1], 10.0)
    }

    @Test
    fun calculateOptimalDistanceForMinimumTime_clampsInMetersRange() {
        val task = buildTask()

        val optimalDistanceMeters = calculator.calculateOptimalDistanceForMinimumTime(
            task = task,
            expectedAverageSpeedMs = 10000.0
        )

        assertTrue(optimalDistanceMeters > 1000.0)
    }

    @Test
    fun calculateNominalDistance_matchesFixtureMatrixAcrossLatitudes() {
        val fixtures = listOf(
            NominalDistanceFixture(
                start = AATLatLng(0.0, 0.0),
                bearingDeg = 90.0,
                firstLegKm = 1.0,
                secondLegKm = 1.0
            ),
            NominalDistanceFixture(
                start = AATLatLng(45.0, 7.0),
                bearingDeg = 45.0,
                firstLegKm = 1.5,
                secondLegKm = 2.0
            ),
            NominalDistanceFixture(
                start = AATLatLng(-33.0, 151.0),
                bearingDeg = 120.0,
                firstLegKm = 0.8,
                secondLegKm = 1.2
            )
        )

        fixtures.forEachIndexed { index, fixture ->
            val task = buildTask(
                start = fixture.start,
                bearingDeg = fixture.bearingDeg,
                firstLegKm = fixture.firstLegKm,
                secondLegKm = fixture.secondLegKm
            )

            val nominalDistanceMeters = calculator.calculateNominalDistance(task)
            val expectedMeters = (fixture.firstLegKm + fixture.secondLegKm) * 1000.0

            assertEquals(
                "Fixture index $index nominal distance should remain in meters",
                expectedMeters,
                nominalDistanceMeters,
                25.0
            )
        }
    }

    private fun buildTask(
        start: AATLatLng = AATLatLng(0.0, 0.0),
        bearingDeg: Double = 90.0,
        firstLegKm: Double = 1.0,
        secondLegKm: Double = 1.0
    ): AATTask {
        val areaCenter = AATMathUtils.calculatePointAtBearing(start, bearingDeg, firstLegKm)
        val finish = AATMathUtils.calculatePointAtBearing(areaCenter, bearingDeg, secondLegKm)

        return AATTask(
            id = "aat-distance-units",
            name = "AAT Distance Units",
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
                    geometry = AreaGeometry.Circle(radius = 100.0),
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
