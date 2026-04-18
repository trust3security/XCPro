package com.trust3.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbEmergencyAudioReplayDeterminismTest {

    private val emergencyId = Icao24.from("abc123") ?: error("invalid test id")

    @Test
    fun replayMatrix_r1_steadyEmergencyEntry_once() {
        val result = runScenario(
            listOf(
                step(nowMonoMs = 0L, emergencyPresent = false),
                step(nowMonoMs = 10_000L, emergencyPresent = true),
                step(nowMonoMs = 20_000L, emergencyPresent = true),
                step(nowMonoMs = 60_000L, emergencyPresent = true)
            )
        )

        assertEquals(1, result.alerts.size)
        assertEquals(0, result.finalTelemetry.cooldownBlockEpisodeCount)
        assertEquals(
            1,
            result.transitions.count { it.event == AdsbEmergencyAudioTransitionEvent.IDLE_TO_ACTIVE_ALERT }
        )
    }

    @Test
    fun replayMatrix_r2_activeToClearToReentryBeforeCooldown_blocksRetrigger() {
        val result = runScenario(
            listOf(
                step(nowMonoMs = 0L, emergencyPresent = false),
                step(nowMonoMs = 10_000L, emergencyPresent = true),
                step(nowMonoMs = 20_000L, emergencyPresent = false),
                step(nowMonoMs = 30_000L, emergencyPresent = true),
                step(nowMonoMs = 40_000L, emergencyPresent = true)
            )
        )

        assertEquals(1, result.alerts.size)
        assertEquals(1, result.finalTelemetry.cooldownBlockEpisodeCount)
        assertEquals(1, result.finalTelemetry.alertTriggerCount)
    }

    @Test
    fun replayMatrix_r3_reentryAfterCooldown_realertsOnFirstEligibleTick() {
        val result = runScenario(
            listOf(
                step(nowMonoMs = 0L, emergencyPresent = false),
                step(nowMonoMs = 10_000L, emergencyPresent = true),
                step(nowMonoMs = 20_000L, emergencyPresent = false),
                step(nowMonoMs = 70_000L, emergencyPresent = true)
            )
        )

        assertEquals(2, result.alerts.size)
        assertEquals(70_000L, result.alerts[1].triggerMonoMs)
        assertEquals(
            AdsbEmergencyAudioTransitionEvent.COOLDOWN_TO_ACTIVE_ALERT,
            result.alerts[1].transition
        )
    }

    @Test
    fun replayMatrix_r4_ownshipLostMidEpisode_noAlertWhileOwnshipMissing() {
        val result = runScenario(
            listOf(
                step(nowMonoMs = 0L, emergencyPresent = false, hasOwnshipReference = true),
                step(nowMonoMs = 10_000L, emergencyPresent = true, hasOwnshipReference = true),
                step(nowMonoMs = 20_000L, emergencyPresent = true, hasOwnshipReference = false),
                step(nowMonoMs = 30_000L, emergencyPresent = true, hasOwnshipReference = false),
                step(nowMonoMs = 40_000L, emergencyPresent = true, hasOwnshipReference = true),
                step(nowMonoMs = 70_000L, emergencyPresent = true, hasOwnshipReference = true)
            )
        )

        assertEquals(listOf(10_000L, 70_000L), result.alerts.map { it.triggerMonoMs })
        assertEquals(2, result.finalTelemetry.alertTriggerCount)
    }

    @Test
    fun replayMatrix_r5_featureFlagToggleRuntime_noBackfillBeforeEnable() {
        val result = runScenario(
            listOf(
                step(nowMonoMs = 0L, emergencyPresent = true, featureFlagEnabled = false),
                step(nowMonoMs = 10_000L, emergencyPresent = true, featureFlagEnabled = false),
                step(nowMonoMs = 20_000L, emergencyPresent = true, featureFlagEnabled = true),
                step(nowMonoMs = 30_000L, emergencyPresent = true, featureFlagEnabled = true)
            )
        )

        assertEquals(1, result.alerts.size)
        assertEquals(20_000L, result.alerts.single().triggerMonoMs)
    }

    @Test
    fun replayMatrix_r6_settingsToggleRuntime_noDuplicateInSameContinuousEpisode() {
        val result = runScenario(
            listOf(
                step(nowMonoMs = 0L, emergencyPresent = false, settingsEnabled = true),
                step(nowMonoMs = 10_000L, emergencyPresent = true, settingsEnabled = true),
                step(nowMonoMs = 20_000L, emergencyPresent = true, settingsEnabled = false),
                step(nowMonoMs = 30_000L, emergencyPresent = true, settingsEnabled = true),
                step(nowMonoMs = 40_000L, emergencyPresent = false, settingsEnabled = true),
                step(nowMonoMs = 50_000L, emergencyPresent = true, settingsEnabled = true)
            )
        )

        assertEquals(listOf(10_000L, 50_000L), result.alerts.map { it.triggerMonoMs })
        assertEquals(2, result.finalTelemetry.alertTriggerCount)
    }

    @Test
    fun replayMatrix_r7_churnNoise_countsCooldownBlockByEpisodeNotByTick() {
        val result = runScenario(
            listOf(
                step(nowMonoMs = 0L, emergencyPresent = false),
                step(nowMonoMs = 10_000L, emergencyPresent = true),
                step(nowMonoMs = 20_000L, emergencyPresent = false),
                step(nowMonoMs = 30_000L, emergencyPresent = true),
                step(nowMonoMs = 35_000L, emergencyPresent = true),
                step(nowMonoMs = 40_000L, emergencyPresent = false),
                step(nowMonoMs = 45_000L, emergencyPresent = true),
                step(nowMonoMs = 50_000L, emergencyPresent = true)
            )
        )

        assertEquals(1, result.alerts.size)
        assertEquals(2, result.finalTelemetry.cooldownBlockEpisodeCount)
        assertEquals(
            2,
            result.transitions.count { it.event == AdsbEmergencyAudioTransitionEvent.COOLDOWN_BLOCKED }
        )
    }

    @Test
    fun replayMatrix_r8_parityDoubleRun_serializedTraceIsIdentical() {
        val steps = listOf(
            step(nowMonoMs = 0L, emergencyPresent = false),
            step(nowMonoMs = 10_000L, emergencyPresent = true),
            step(nowMonoMs = 20_000L, emergencyPresent = false),
            step(nowMonoMs = 30_000L, emergencyPresent = true),
            step(nowMonoMs = 40_000L, emergencyPresent = true, settingsEnabled = false),
            step(nowMonoMs = 50_000L, emergencyPresent = true, settingsEnabled = true),
            step(nowMonoMs = 60_000L, emergencyPresent = false, settingsEnabled = true),
            step(nowMonoMs = 70_000L, emergencyPresent = true, settingsEnabled = true),
            step(nowMonoMs = 80_000L, emergencyPresent = true, featureFlagEnabled = false),
            step(nowMonoMs = 90_000L, emergencyPresent = true, featureFlagEnabled = true)
        )

        val first = runScenario(steps).serialize()
        val second = runScenario(steps).serialize()

        assertEquals(first, second)
        assertTrue(first.isNotBlank())
    }

    @Test
    fun replayMatrix_r9_nominalScenario_hasNoKpiViolationAndNoThresholdAlert() {
        val result = runScenario(
            listOf(
                step(nowMonoMs = 0L, emergencyPresent = false),
                step(nowMonoMs = 10_000L, emergencyPresent = true),
                step(nowMonoMs = 20_000L, emergencyPresent = false),
                step(nowMonoMs = 40_000L, emergencyPresent = false),
                step(nowMonoMs = 70_000L, emergencyPresent = true)
            )
        )

        assertEquals(0, result.finalKpis.retriggerWithinCooldownCount)
        assertEquals(0, result.finalKpis.determinismMismatchCount)
        assertEquals(null, AdsbEmergencyAudioKpiPolicy.firstViolationCode(result.finalKpis))
    }

    private fun runScenario(steps: List<ReplayStep>): ReplayResult {
        val fsm = AdsbEmergencyAudioAlertFsm()
        val kpiAccumulator = AdsbEmergencyAudioKpiAccumulator()
        val alerts = mutableListOf<AlertTrace>()
        val transitions = mutableListOf<TransitionTrace>()
        val kpiTimeline = mutableListOf<KpiTrace>()
        var telemetry: AdsbEmergencyAudioTelemetry? = null
        var kpis: AdsbEmergencyAudioKpiSnapshot? = null

        steps.forEach { step ->
            val decision = fsm.evaluate(
                nowMonoMs = step.nowMonoMs,
                emergencyTargetId = if (step.emergencyPresent) emergencyId else null,
                hasOwnshipReference = step.hasOwnshipReference,
                settings = AdsbEmergencyAudioSettings(
                    enabled = step.settingsEnabled,
                    cooldownMs = step.cooldownMs
                ),
                featureFlagEnabled = step.featureFlagEnabled
            )
            val events = fsm.drainTransitionEvents()
            events.forEach { event ->
                transitions += TransitionTrace(step.nowMonoMs, event)
            }
            telemetry = fsm.snapshotTelemetry(step.nowMonoMs)
            kpis = kpiAccumulator.updateAndSnapshot(
                nowMonoMs = step.nowMonoMs,
                observationActive = step.hasOwnshipReference && step.featureFlagEnabled,
                policyEnabled = step.settingsEnabled && step.featureFlagEnabled,
                cooldownMs = step.cooldownMs,
                telemetry = telemetry ?: error("Missing telemetry at t=${step.nowMonoMs}")
            )
            kpiTimeline += KpiTrace(
                atMonoMs = step.nowMonoMs,
                snapshot = kpis ?: AdsbEmergencyAudioKpiSnapshot()
            )

            if (decision.shouldPlayAlert) {
                val activationEvent = events.lastOrNull { event ->
                    event == AdsbEmergencyAudioTransitionEvent.IDLE_TO_ACTIVE_ALERT ||
                        event == AdsbEmergencyAudioTransitionEvent.COOLDOWN_TO_ACTIVE_ALERT
                } ?: error("Missing activation transition for alert at t=${step.nowMonoMs}")
                alerts += AlertTrace(
                    triggerMonoMs = step.nowMonoMs,
                    targetId = decision.activeEmergencyTargetId?.raw,
                    transition = activationEvent
                )
            }
        }

        return ReplayResult(
            alerts = alerts,
            transitions = transitions,
            finalTelemetry = telemetry ?: fsm.snapshotTelemetry(0L),
            finalKpis = kpis ?: AdsbEmergencyAudioKpiSnapshot(),
            kpiTimeline = kpiTimeline
        )
    }

    private fun step(
        nowMonoMs: Long,
        emergencyPresent: Boolean,
        hasOwnshipReference: Boolean = true,
        featureFlagEnabled: Boolean = true,
        settingsEnabled: Boolean = true,
        cooldownMs: Long = ADSB_EMERGENCY_AUDIO_DEFAULT_COOLDOWN_MS
    ): ReplayStep = ReplayStep(
        nowMonoMs = nowMonoMs,
        emergencyPresent = emergencyPresent,
        hasOwnshipReference = hasOwnshipReference,
        featureFlagEnabled = featureFlagEnabled,
        settingsEnabled = settingsEnabled,
        cooldownMs = cooldownMs
    )

    private data class ReplayStep(
        val nowMonoMs: Long,
        val emergencyPresent: Boolean,
        val hasOwnshipReference: Boolean,
        val featureFlagEnabled: Boolean,
        val settingsEnabled: Boolean,
        val cooldownMs: Long
    )

    private data class AlertTrace(
        val triggerMonoMs: Long,
        val targetId: String?,
        val transition: AdsbEmergencyAudioTransitionEvent
    )

    private data class TransitionTrace(
        val atMonoMs: Long,
        val event: AdsbEmergencyAudioTransitionEvent
    )

    private data class ReplayResult(
        val alerts: List<AlertTrace>,
        val transitions: List<TransitionTrace>,
        val finalTelemetry: AdsbEmergencyAudioTelemetry,
        val finalKpis: AdsbEmergencyAudioKpiSnapshot,
        val kpiTimeline: List<KpiTrace>
    ) {
        fun serialize(): String = buildString {
            append(
                alerts.joinToString(separator = "|") { alert ->
                    "${alert.triggerMonoMs}:${alert.targetId}:${alert.transition.name}"
                }
            )
            append("||")
            append(
                transitions.joinToString(separator = "|") { transition ->
                    "${transition.atMonoMs}:${transition.event.name}"
                }
            )
            append("||")
            append("state=${finalTelemetry.state.name};")
            append("alerts=${finalTelemetry.alertTriggerCount};")
            append("blocks=${finalTelemetry.cooldownBlockEpisodeCount};")
            append("events=${finalTelemetry.transitionEventCount};")
            append("lastAlert=${finalTelemetry.lastAlertMonoMs};")
            append("cooldownRemaining=${finalTelemetry.cooldownRemainingMs};")
            append("target=${finalTelemetry.activeEmergencyTargetId?.raw}")
            append("||")
            append(
                kpiTimeline.joinToString(separator = "|") { trace ->
                    "${trace.atMonoMs}:${trace.snapshot.alertTriggerCount}:${trace.snapshot.cooldownBlockEpisodeCount}:${trace.snapshot.disableWithin5MinCount}:${trace.snapshot.disableEventCount}:${trace.snapshot.retriggerWithinCooldownCount}:${trace.snapshot.determinismMismatchCount}"
                }
            )
            append("||")
            append("alertsPerHour=${finalKpis.alertsPerFlightHour};")
            append("blocksPerHour=${finalKpis.cooldownBlockEpisodesPerFlightHour};")
            append("disableRate=${finalKpis.disableWithin5MinRate};")
            append("retrigger=${finalKpis.retriggerWithinCooldownCount};")
            append("mismatch=${finalKpis.determinismMismatchCount};")
            append("activeMs=${finalKpis.activeObservationMs}")
        }
    }

    private data class KpiTrace(
        val atMonoMs: Long,
        val snapshot: AdsbEmergencyAudioKpiSnapshot
    )
}
