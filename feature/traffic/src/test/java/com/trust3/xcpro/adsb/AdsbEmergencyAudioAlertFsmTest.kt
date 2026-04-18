package com.trust3.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbEmergencyAudioAlertFsmTest {

    private val emergencyId = Icao24.from("abc123") ?: error("invalid test id")
    private val settingsEnabled = AdsbEmergencyAudioSettings(
        enabled = true,
        cooldownMs = 20_000L
    )
    private val settingsDisabled = settingsEnabled.copy(enabled = false)

    @Test
    fun evaluate_gateDisabled_staysDisabledWithoutAlerts() {
        val fsm = AdsbEmergencyAudioAlertFsm()

        val decision = fsm.evaluate(
            nowMonoMs = 1_000L,
            emergencyTargetId = emergencyId,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = false
        )

        assertEquals(AdsbEmergencyAudioAlertState.DISABLED, decision.state)
        assertFalse(decision.shouldPlayAlert)
        assertEquals(0L, decision.cooldownRemainingMs)
        assertEquals(emptyList<AdsbEmergencyAudioTransitionEvent>(), fsm.drainTransitionEvents())
    }

    @Test
    fun evaluate_enablingGateTransitionsToIdle() {
        val fsm = AdsbEmergencyAudioAlertFsm()

        val decision = fsm.evaluate(
            nowMonoMs = 1_000L,
            emergencyTargetId = null,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )

        assertEquals(AdsbEmergencyAudioAlertState.IDLE, decision.state)
        assertFalse(decision.shouldPlayAlert)
        assertEquals(
            listOf(AdsbEmergencyAudioTransitionEvent.DISABLED_TO_IDLE),
            fsm.drainTransitionEvents()
        )
    }

    @Test
    fun evaluate_idleToActive_emitsSingleAlertPerEpisode() {
        val fsm = AdsbEmergencyAudioAlertFsm()
        fsm.evaluate(
            nowMonoMs = 1_000L,
            emergencyTargetId = null,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.drainTransitionEvents()

        val firstAlert = fsm.evaluate(
            nowMonoMs = 2_000L,
            emergencyTargetId = emergencyId,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        val secondActiveTick = fsm.evaluate(
            nowMonoMs = 3_000L,
            emergencyTargetId = emergencyId,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )

        assertTrue(firstAlert.shouldPlayAlert)
        assertEquals(AdsbEmergencyAudioAlertState.ACTIVE, firstAlert.state)
        assertFalse(secondActiveTick.shouldPlayAlert)
        assertEquals(AdsbEmergencyAudioAlertState.ACTIVE, secondActiveTick.state)
    }

    @Test
    fun evaluate_activeToCooldown_blocksRetriggerUntilExpiry() {
        val fsm = AdsbEmergencyAudioAlertFsm()
        fsm.evaluate(
            nowMonoMs = 1_000L,
            emergencyTargetId = null,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.drainTransitionEvents()
        fsm.evaluate(
            nowMonoMs = 2_000L,
            emergencyTargetId = emergencyId,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.drainTransitionEvents()

        val cooldown = fsm.evaluate(
            nowMonoMs = 3_000L,
            emergencyTargetId = null,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        assertEquals(AdsbEmergencyAudioAlertState.COOLDOWN, cooldown.state)
        assertFalse(cooldown.shouldPlayAlert)
        assertTrue(cooldown.cooldownRemainingMs > 0L)
        assertEquals(
            listOf(AdsbEmergencyAudioTransitionEvent.ACTIVE_TO_COOLDOWN),
            fsm.drainTransitionEvents()
        )

        val blockedFirst = fsm.evaluate(
            nowMonoMs = 4_000L,
            emergencyTargetId = emergencyId,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        assertFalse(blockedFirst.shouldPlayAlert)
        assertTrue(blockedFirst.cooldownBlockedByPolicy)
        assertEquals(
            listOf(AdsbEmergencyAudioTransitionEvent.COOLDOWN_BLOCKED),
            fsm.drainTransitionEvents()
        )

        val blockedSecond = fsm.evaluate(
            nowMonoMs = 5_000L,
            emergencyTargetId = emergencyId,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        assertFalse(blockedSecond.shouldPlayAlert)
        assertTrue(blockedSecond.cooldownBlockedByPolicy)
        assertEquals(emptyList<AdsbEmergencyAudioTransitionEvent>(), fsm.drainTransitionEvents())

        fsm.evaluate(
            nowMonoMs = 6_000L,
            emergencyTargetId = null,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.drainTransitionEvents()

        fsm.evaluate(
            nowMonoMs = 7_000L,
            emergencyTargetId = emergencyId,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        assertEquals(
            listOf(AdsbEmergencyAudioTransitionEvent.COOLDOWN_BLOCKED),
            fsm.drainTransitionEvents()
        )
    }

    @Test
    fun evaluate_realertsAfterCooldownWhenEmergencyPersists() {
        val fsm = AdsbEmergencyAudioAlertFsm()
        fsm.evaluate(
            nowMonoMs = 1_000L,
            emergencyTargetId = null,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.evaluate(
            nowMonoMs = 2_000L,
            emergencyTargetId = emergencyId,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.evaluate(
            nowMonoMs = 3_000L,
            emergencyTargetId = null,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.drainTransitionEvents()

        val reAlert = fsm.evaluate(
            nowMonoMs = 23_000L,
            emergencyTargetId = emergencyId,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )

        assertTrue(reAlert.shouldPlayAlert)
        assertEquals(AdsbEmergencyAudioAlertState.ACTIVE, reAlert.state)
        assertEquals(
            listOf(AdsbEmergencyAudioTransitionEvent.COOLDOWN_TO_ACTIVE_ALERT),
            fsm.drainTransitionEvents()
        )
        assertEquals(2, fsm.snapshotTelemetry(nowMonoMs = 23_000L).alertTriggerCount)
    }

    @Test
    fun evaluate_cooldownExpiresToIdleWhenNoEmergency() {
        val fsm = AdsbEmergencyAudioAlertFsm()
        fsm.evaluate(
            nowMonoMs = 1_000L,
            emergencyTargetId = null,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.evaluate(
            nowMonoMs = 2_000L,
            emergencyTargetId = emergencyId,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.evaluate(
            nowMonoMs = 3_000L,
            emergencyTargetId = null,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.drainTransitionEvents()

        val resolved = fsm.evaluate(
            nowMonoMs = 23_500L,
            emergencyTargetId = null,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )

        assertEquals(AdsbEmergencyAudioAlertState.IDLE, resolved.state)
        assertFalse(resolved.shouldPlayAlert)
        assertEquals(
            listOf(AdsbEmergencyAudioTransitionEvent.COOLDOWN_TO_IDLE),
            fsm.drainTransitionEvents()
        )
    }

    @Test
    fun evaluate_ownshipMissingBlocksEmergencyAlert() {
        val fsm = AdsbEmergencyAudioAlertFsm()
        fsm.evaluate(
            nowMonoMs = 1_000L,
            emergencyTargetId = null,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.drainTransitionEvents()

        val decision = fsm.evaluate(
            nowMonoMs = 2_000L,
            emergencyTargetId = emergencyId,
            hasOwnshipReference = false,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )

        assertEquals(AdsbEmergencyAudioAlertState.IDLE, decision.state)
        assertFalse(decision.shouldPlayAlert)
        assertNull(decision.activeEmergencyTargetId)
    }

    @Test
    fun evaluate_settingDisabledForcesDisabledState() {
        val fsm = AdsbEmergencyAudioAlertFsm()
        fsm.evaluate(
            nowMonoMs = 1_000L,
            emergencyTargetId = null,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.drainTransitionEvents()

        val decision = fsm.evaluate(
            nowMonoMs = 2_000L,
            emergencyTargetId = emergencyId,
            hasOwnshipReference = true,
            settings = settingsDisabled,
            featureFlagEnabled = true
        )

        assertEquals(AdsbEmergencyAudioAlertState.DISABLED, decision.state)
        assertFalse(decision.shouldPlayAlert)
        assertEquals(
            listOf(AdsbEmergencyAudioTransitionEvent.TO_DISABLED),
            fsm.drainTransitionEvents()
        )
    }

    @Test
    fun evaluate_nonMonotonicInputTimeDoesNotPrematurelyExpireCooldown() {
        val fsm = AdsbEmergencyAudioAlertFsm()
        fsm.evaluate(
            nowMonoMs = 1_000L,
            emergencyTargetId = null,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.evaluate(
            nowMonoMs = 2_000L,
            emergencyTargetId = emergencyId,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.evaluate(
            nowMonoMs = 3_000L,
            emergencyTargetId = null,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )
        fsm.drainTransitionEvents()

        val backwardTick = fsm.evaluate(
            nowMonoMs = 2_500L,
            emergencyTargetId = emergencyId,
            hasOwnshipReference = true,
            settings = settingsEnabled,
            featureFlagEnabled = true
        )

        assertEquals(AdsbEmergencyAudioAlertState.COOLDOWN, backwardTick.state)
        assertFalse(backwardTick.shouldPlayAlert)
        assertEquals(20_000L, backwardTick.cooldownRemainingMs)
    }

    @Test
    fun evaluate_sameSequenceIsDeterministicAcrossInstances() {
        val steps = listOf(
            Step(1_000L, null, true, true, settingsEnabled),
            Step(2_000L, emergencyId, true, true, settingsEnabled),
            Step(3_000L, null, true, true, settingsEnabled),
            Step(4_000L, emergencyId, true, true, settingsEnabled),
            Step(25_000L, emergencyId, true, true, settingsEnabled),
            Step(26_000L, emergencyId, false, true, settingsEnabled),
            Step(27_000L, emergencyId, true, true, settingsDisabled),
            Step(28_000L, emergencyId, true, true, settingsEnabled),
            Step(29_000L, emergencyId, true, false, settingsEnabled)
        )

        val firstRun = runSequence(steps)
        val secondRun = runSequence(steps)

        assertEquals(firstRun, secondRun)
    }

    private fun runSequence(steps: List<Step>): List<Snapshot> {
        val fsm = AdsbEmergencyAudioAlertFsm()
        return steps.map { step ->
            val decision = fsm.evaluate(
                nowMonoMs = step.nowMonoMs,
                emergencyTargetId = step.emergencyTargetId,
                hasOwnshipReference = step.hasOwnshipReference,
                settings = step.settings,
                featureFlagEnabled = step.featureFlagEnabled
            )
            Snapshot(
                decision = decision,
                events = fsm.drainTransitionEvents(),
                telemetry = fsm.snapshotTelemetry(step.nowMonoMs)
            )
        }
    }

    private data class Step(
        val nowMonoMs: Long,
        val emergencyTargetId: Icao24?,
        val hasOwnshipReference: Boolean,
        val featureFlagEnabled: Boolean,
        val settings: AdsbEmergencyAudioSettings
    )

    private data class Snapshot(
        val decision: AdsbEmergencyAudioDecision,
        val events: List<AdsbEmergencyAudioTransitionEvent>,
        val telemetry: AdsbEmergencyAudioTelemetry
    )
}
