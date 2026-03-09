package com.example.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbEmergencyAudioKpiAccumulatorTest {

    @Test
    fun updateAndSnapshot_perHourUsesMonotonicActiveObservation() {
        val accumulator = AdsbEmergencyAudioKpiAccumulator()

        accumulator.updateAndSnapshot(
            nowMonoMs = 0L,
            observationActive = true,
            policyEnabled = true,
            cooldownMs = 30_000L,
            telemetry = telemetry(alerts = 0, blocks = 0, lastAlertMonoMs = null)
        )
        val snapshot = accumulator.updateAndSnapshot(
            nowMonoMs = 3_600_000L,
            observationActive = true,
            policyEnabled = true,
            cooldownMs = 30_000L,
            telemetry = telemetry(alerts = 2, blocks = 1, lastAlertMonoMs = 3_600_000L)
        )

        assertEquals(3_600_000L, snapshot.activeObservationMs)
        assertEquals(2.0, snapshot.alertsPerFlightHour, 1e-9)
        assertEquals(1.0, snapshot.cooldownBlockEpisodesPerFlightHour, 1e-9)
    }

    @Test
    fun updateAndSnapshot_disableWithinFiveMinutesRate_tracksEnabledToDisabledEpisodes() {
        val accumulator = AdsbEmergencyAudioKpiAccumulator()

        accumulator.updateAndSnapshot(
            nowMonoMs = 0L,
            observationActive = true,
            policyEnabled = true,
            cooldownMs = 30_000L,
            telemetry = telemetry(alerts = 0, blocks = 0, lastAlertMonoMs = null)
        )
        accumulator.updateAndSnapshot(
            nowMonoMs = 10_000L,
            observationActive = true,
            policyEnabled = true,
            cooldownMs = 30_000L,
            telemetry = telemetry(alerts = 1, blocks = 0, lastAlertMonoMs = 10_000L)
        )
        val firstDisable = accumulator.updateAndSnapshot(
            nowMonoMs = 100_000L,
            observationActive = true,
            policyEnabled = false,
            cooldownMs = 30_000L,
            telemetry = telemetry(alerts = 1, blocks = 0, lastAlertMonoMs = 10_000L)
        )
        accumulator.updateAndSnapshot(
            nowMonoMs = 500_000L,
            observationActive = true,
            policyEnabled = true,
            cooldownMs = 30_000L,
            telemetry = telemetry(alerts = 1, blocks = 0, lastAlertMonoMs = 10_000L)
        )
        val secondDisable = accumulator.updateAndSnapshot(
            nowMonoMs = 800_001L,
            observationActive = true,
            policyEnabled = false,
            cooldownMs = 30_000L,
            telemetry = telemetry(alerts = 1, blocks = 0, lastAlertMonoMs = 10_000L)
        )

        assertEquals(1, firstDisable.disableEventCount)
        assertEquals(1, firstDisable.disableWithin5MinCount)
        assertEquals(1.0, firstDisable.disableWithin5MinRate, 1e-9)
        assertEquals(2, secondDisable.disableEventCount)
        assertEquals(1, secondDisable.disableWithin5MinCount)
        assertEquals(0.5, secondDisable.disableWithin5MinRate, 1e-9)
    }

    @Test
    fun updateAndSnapshot_retriggerWithinCooldownCount_detectsCounterViolation() {
        val accumulator = AdsbEmergencyAudioKpiAccumulator()

        accumulator.updateAndSnapshot(
            nowMonoMs = 0L,
            observationActive = true,
            policyEnabled = true,
            cooldownMs = 30_000L,
            telemetry = telemetry(alerts = 0, blocks = 0, lastAlertMonoMs = null)
        )
        accumulator.updateAndSnapshot(
            nowMonoMs = 10_000L,
            observationActive = true,
            policyEnabled = true,
            cooldownMs = 30_000L,
            telemetry = telemetry(alerts = 1, blocks = 0, lastAlertMonoMs = 10_000L)
        )
        val snapshot = accumulator.updateAndSnapshot(
            nowMonoMs = 20_000L,
            observationActive = true,
            policyEnabled = true,
            cooldownMs = 30_000L,
            telemetry = telemetry(alerts = 2, blocks = 0, lastAlertMonoMs = 20_000L)
        )

        assertEquals(1, snapshot.retriggerWithinCooldownCount)
    }

    @Test
    fun updateAndSnapshot_backwardMonotonicTime_incrementsDeterminismMismatchCount() {
        val accumulator = AdsbEmergencyAudioKpiAccumulator()

        accumulator.updateAndSnapshot(
            nowMonoMs = 10_000L,
            observationActive = true,
            policyEnabled = true,
            cooldownMs = 30_000L,
            telemetry = telemetry(alerts = 0, blocks = 0, lastAlertMonoMs = null)
        )
        val snapshot = accumulator.updateAndSnapshot(
            nowMonoMs = 9_000L,
            observationActive = true,
            policyEnabled = true,
            cooldownMs = 30_000L,
            telemetry = telemetry(alerts = 0, blocks = 0, lastAlertMonoMs = null)
        )

        assertEquals(1, snapshot.determinismMismatchCount)
    }

    private fun telemetry(
        alerts: Int,
        blocks: Int,
        lastAlertMonoMs: Long?
    ): AdsbEmergencyAudioTelemetry = AdsbEmergencyAudioTelemetry(
        state = AdsbEmergencyAudioAlertState.IDLE,
        alertTriggerCount = alerts,
        cooldownBlockEpisodeCount = blocks,
        transitionEventCount = 0,
        lastAlertMonoMs = lastAlertMonoMs,
        cooldownRemainingMs = 0L,
        activeEmergencyTargetId = null
    )
}
