package com.example.xcpro.map.replay

import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.RacingTaskStructureRules
import com.example.xcpro.tasks.racing.toCoreTask
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import kotlin.math.ceil
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingReplayLogBuilderTest {

    @Test
    fun build_targetSpeedOverrideChangesDurationUsingKmhToMsConversion() {
        val stepMillis = 1_000L
        val builder = RacingReplayLogBuilder(
            stepMillis = stepMillis,
            targetSpeedKmh = 36.0,
            validationProfile = RacingTaskStructureRules.Profile.XC_PRO_EXTENDED
        )
        val task = simpleLineTask()
        val distanceMeters = RacingGeometryUtils.haversineDistanceMeters(
            0.0,
            0.0,
            0.0,
            0.01
        )

        val slowLog = builder.build(
            task = task,
            startTimestampMillis = 0L,
            targetSpeedKmhOverride = 36.0
        )
        val fastLog = builder.build(
            task = task,
            startTimestampMillis = 0L,
            targetSpeedKmhOverride = 72.0
        )

        val expectedSlow = expectedTotalDurationMs(distanceMeters, 36.0, stepMillis)
        val expectedFast = expectedTotalDurationMs(distanceMeters, 72.0, stepMillis)
        val actualSlow = slowLog.points.last().timestampMillis - slowLog.points.first().timestampMillis
        val actualFast = fastLog.points.last().timestampMillis - fastLog.points.first().timestampMillis

        assertEquals(expectedSlow, actualSlow)
        assertEquals(expectedFast, actualFast)
        assertTrue(actualFast < actualSlow)
    }

    @Test
    fun build_quantizesSegmentDurationToStepMillis() {
        val stepMillis = 1_500L
        val speedKmh = 47.0
        val builder = RacingReplayLogBuilder(
            stepMillis = stepMillis,
            targetSpeedKmh = speedKmh,
            validationProfile = RacingTaskStructureRules.Profile.XC_PRO_EXTENDED
        )
        val task = simpleLineTask()
        val distanceMeters = RacingGeometryUtils.haversineDistanceMeters(
            0.0,
            0.0,
            0.0,
            0.01
        )

        val log = builder.build(
            task = task,
            startTimestampMillis = 0L
        )
        val durationMs = log.points.last().timestampMillis - log.points.first().timestampMillis
        val expectedDurationMs = expectedTotalDurationMs(distanceMeters, speedKmh, stepMillis)

        assertEquals(0L, durationMs % stepMillis)
        assertEquals(expectedDurationMs, durationMs)
    }

    @Test
    fun build_coreTaskInputMatchesSimpleTaskCompatibilityPath() {
        val builder = RacingReplayLogBuilder(
            stepMillis = 1_000L,
            targetSpeedKmh = 60.0,
            validationProfile = RacingTaskStructureRules.Profile.XC_PRO_EXTENDED
        )
        val task = simpleTaskWithExplicitGateWidths()
        val simpleLog = builder.build(task = task, startTimestampMillis = 0L)
        val coreLog = builder.build(task = task.toCoreTask(), startTimestampMillis = 0L)

        assertEquals(simpleLog.points, coreLog.points)
    }

    @Test(expected = IllegalArgumentException::class)
    fun build_defaultStrictProfileRejectsShortRacingTask() {
        val builder = RacingReplayLogBuilder(stepMillis = 1_000L, targetSpeedKmh = 60.0)
        builder.build(task = simpleLineTask(), startTimestampMillis = 0L)
    }

    private fun simpleLineTask(): SimpleRacingTask {
        val start = RacingWaypoint(
            id = "S",
            title = "Start",
            subtitle = "",
            lat = 0.0,
            lon = 0.0,
            role = RacingWaypointRole.START,
            startPointType = RacingStartPointType.START_LINE,
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER,
            gateWidthMeters = 0.0
        )
        val finish = RacingWaypoint(
            id = "F",
            title = "Finish",
            subtitle = "",
            lat = 0.0,
            lon = 0.01,
            role = RacingWaypointRole.FINISH,
            finishPointType = RacingFinishPointType.FINISH_LINE,
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER,
            gateWidthMeters = 0.0
        )
        return SimpleRacingTask(
            id = "task",
            waypoints = listOf(start, finish)
        )
    }

    private fun simpleTaskWithExplicitGateWidths(): SimpleRacingTask {
        val start = RacingWaypoint(
            id = "S2",
            title = "Start2",
            subtitle = "",
            lat = 0.0,
            lon = 0.0,
            role = RacingWaypointRole.START,
            startPointType = RacingStartPointType.START_LINE,
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER,
            gateWidthMeters = 5_000.0
        )
        val finish = RacingWaypoint(
            id = "F2",
            title = "Finish2",
            subtitle = "",
            lat = 0.0,
            lon = 0.01,
            role = RacingWaypointRole.FINISH,
            finishPointType = RacingFinishPointType.FINISH_LINE,
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER,
            gateWidthMeters = 3_000.0
        )
        return SimpleRacingTask(
            id = "task-2",
            waypoints = listOf(start, finish)
        )
    }

    private fun expectedTotalDurationMs(
        mainLegDistanceMeters: Double,
        speedKmh: Double,
        stepMillis: Long
    ): Long {
        val speedMs = speedKmh / 3.6
        val expectedMainDurationMs = max(stepMillis.toDouble(), mainLegDistanceMeters / speedMs * 1000.0)
        val mainSteps = ceil(expectedMainDurationMs / stepMillis.toDouble()).toLong()
        val zeroLegSteps = 2L // start pre->post and finish pre->post
        return (mainSteps + zeroLegSteps) * stepMillis
    }
}
