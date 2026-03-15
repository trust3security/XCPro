package com.example.xcpro.tasks.racing

import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.core.RacingAltitudeReference
import com.example.xcpro.tasks.core.RacingPevCustomParams
import com.example.xcpro.tasks.core.RacingStartCustomParams
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.TaskWaypointParamKeys
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RacingTaskManagerRulePersistenceTest {

    @Test
    fun waypointMutations_preserveStartAndFinishRulePayloads() {
        val manager = RacingTaskManager()
        manager.initializeFromCoreTask(seedTask())

        manager.updateRacingWaypointType(
            index = 1,
            turnType = RacingTurnPointType.KEYHOLE,
            gateWidthMeters = 10_000.0
        )

        val updated = manager.getCoreTask()
        val startWaypoint = updated.waypoints.first { it.role == WaypointRole.START }
        val finishWaypoint = updated.waypoints.first { it.role == WaypointRole.FINISH }
        val startRules = RacingStartCustomParams.from(startWaypoint.customParameters)
        val finishRules = RacingFinishCustomParams.from(finishWaypoint.customParameters)

        assertEquals(1_000L, startRules.gateOpenTimeMillis)
        assertEquals(2_000L, startRules.gateCloseTimeMillis)
        assertEquals(180.0, startRules.directionOverrideDegrees)
        assertEquals(3_000L, finishRules.closeTimeMillis)
        assertEquals(700.0, finishRules.minAltitudeMeters)
        assertEquals(90.0, finishRules.directionOverrideDegrees)
        assertEquals(RacingTaskStructureRules.Profile.XC_PRO_EXTENDED, manager.getRacingValidationProfile())
    }

    @Test
    fun reorderWaypoints_keepsRuleKeysBoundToCurrentStartAndFinishRoles() {
        val manager = RacingTaskManager()
        manager.initializeFromCoreTask(seedTask())

        manager.reorderRacingWaypoints(fromIndex = 0, toIndex = 1)

        val updated = manager.getCoreTask()
        val startWaypoint = updated.waypoints.first { it.role == WaypointRole.START }
        val finishWaypoint = updated.waypoints.first { it.role == WaypointRole.FINISH }
        val movedTurnpoint = updated.waypoints.first { it.id == "start" }

        assertEquals(1_000L, RacingStartCustomParams.from(startWaypoint.customParameters).gateOpenTimeMillis)
        assertEquals(3_000L, RacingFinishCustomParams.from(finishWaypoint.customParameters).closeTimeMillis)
        assertNull(movedTurnpoint.customParameters[TaskWaypointParamKeys.START_GATE_OPEN_TIME_MILLIS])
        assertNull(movedTurnpoint.customParameters[TaskWaypointParamKeys.FINISH_CLOSE_TIME_MILLIS])
    }

    @Test
    fun updateRuleCommands_persistAllStartAndFinishFields() {
        val manager = RacingTaskManager()
        manager.initializeFromCoreTask(seedTask())

        manager.updateRacingStartRules(
            UpdateRacingStartRulesCommand(
                rules = RacingStartCustomParams(
                    gateOpenTimeMillis = 11_000L,
                    gateCloseTimeMillis = 22_000L,
                    toleranceMeters = 420.0,
                    preStartAltitudeMeters = 1_450.0,
                    altitudeReference = RacingAltitudeReference.QNH,
                    directionOverrideDegrees = 179.0,
                    maxStartAltitudeMeters = 2_200.0,
                    maxStartGroundspeedMs = 44.0,
                    pev = RacingPevCustomParams(
                        enabled = true,
                        waitTimeMinutes = 6,
                        startWindowMinutes = 9,
                        maxPressesPerLaunch = 2,
                        dedupeSeconds = 15L,
                        minIntervalMinutes = 5,
                        pressTimestampsMillis = listOf(1_000L, 2_000L)
                    )
                )
            )
        )

        manager.updateRacingFinishRules(
            UpdateRacingFinishRulesCommand(
                rules = RacingFinishCustomParams(
                    closeTimeMillis = 33_000L,
                    minAltitudeMeters = 920.0,
                    altitudeReference = RacingAltitudeReference.QNH,
                    directionOverrideDegrees = 88.0,
                    allowStraightInBelowMinAltitude = true,
                    requireLandWithoutDelay = true,
                    landWithoutDelayWindowSeconds = 420L,
                    landingSpeedThresholdMs = 7.2,
                    landingHoldSeconds = 25L,
                    contestBoundaryRadiusMeters = 8_800.0,
                    stopPlusFiveEnabled = true,
                    stopPlusFiveMinutes = 8L
                )
            )
        )

        val task = manager.getCoreTask()
        val startWaypoint = task.waypoints.first { it.role == WaypointRole.START }
        val finishWaypoint = task.waypoints.first { it.role == WaypointRole.FINISH }
        val start = RacingStartCustomParams.from(startWaypoint.customParameters)
        val finish = RacingFinishCustomParams.from(finishWaypoint.customParameters)

        assertEquals(11_000L, start.gateOpenTimeMillis)
        assertEquals(22_000L, start.gateCloseTimeMillis)
        assertEquals(420.0, start.toleranceMeters, 0.0)
        assertNotNull(start.preStartAltitudeMeters)
        assertEquals(1_450.0, start.preStartAltitudeMeters!!, 0.0)
        assertEquals(RacingAltitudeReference.QNH, start.altitudeReference)
        assertNotNull(start.directionOverrideDegrees)
        assertEquals(179.0, start.directionOverrideDegrees!!, 0.0)
        assertNotNull(start.maxStartAltitudeMeters)
        assertEquals(2_200.0, start.maxStartAltitudeMeters!!, 0.0)
        assertNotNull(start.maxStartGroundspeedMs)
        assertEquals(44.0, start.maxStartGroundspeedMs!!, 0.0)
        assertEquals(true, start.pev.enabled)
        assertEquals(6, start.pev.waitTimeMinutes)
        assertEquals(9, start.pev.startWindowMinutes)
        assertEquals(2, start.pev.maxPressesPerLaunch)
        assertEquals(15L, start.pev.dedupeSeconds)
        assertEquals(5, start.pev.minIntervalMinutes)
        assertEquals(listOf(1_000L, 2_000L), start.pev.pressTimestampsMillis)

        assertEquals(33_000L, finish.closeTimeMillis)
        assertNotNull(finish.minAltitudeMeters)
        assertEquals(920.0, finish.minAltitudeMeters!!, 0.0)
        assertEquals(RacingAltitudeReference.QNH, finish.altitudeReference)
        assertNotNull(finish.directionOverrideDegrees)
        assertEquals(88.0, finish.directionOverrideDegrees!!, 0.0)
        assertEquals(true, finish.allowStraightInBelowMinAltitude)
        assertEquals(true, finish.requireLandWithoutDelay)
        assertEquals(420L, finish.landWithoutDelayWindowSeconds)
        assertEquals(7.2, finish.landingSpeedThresholdMs, 0.0)
        assertEquals(25L, finish.landingHoldSeconds)
        assertNotNull(finish.contestBoundaryRadiusMeters)
        assertEquals(8_800.0, finish.contestBoundaryRadiusMeters!!, 0.0)
        assertEquals(true, finish.stopPlusFiveEnabled)
        assertEquals(8L, finish.stopPlusFiveMinutes)
    }

    private fun seedTask(): Task {
        val startParams = mutableMapOf<String, Any>()
        RacingStartCustomParams(
            gateOpenTimeMillis = 1_000L,
            gateCloseTimeMillis = 2_000L,
            directionOverrideDegrees = 180.0
        ).applyTo(startParams)
        startParams[TaskWaypointParamKeys.RACING_VALIDATION_PROFILE] =
            RacingTaskStructureRules.Profile.XC_PRO_EXTENDED.name

        val finishParams = mutableMapOf<String, Any>()
        RacingFinishCustomParams(
            closeTimeMillis = 3_000L,
            minAltitudeMeters = 700.0,
            directionOverrideDegrees = 90.0
        ).applyTo(finishParams)

        return Task(
            id = "racing-rules-seed",
            waypoints = listOf(
                TaskWaypoint(
                    id = "start",
                    title = "Start",
                    subtitle = "",
                    lat = 0.0,
                    lon = 0.0,
                    role = WaypointRole.START,
                    customParameters = startParams
                ),
                TaskWaypoint(
                    id = "tp-1",
                    title = "TP",
                    subtitle = "",
                    lat = 0.5,
                    lon = 0.5,
                    role = WaypointRole.TURNPOINT
                ),
                TaskWaypoint(
                    id = "finish",
                    title = "Finish",
                    subtitle = "",
                    lat = 1.0,
                    lon = 1.0,
                    role = WaypointRole.FINISH,
                    customParameters = finishParams
                )
            )
        )
    }
}
