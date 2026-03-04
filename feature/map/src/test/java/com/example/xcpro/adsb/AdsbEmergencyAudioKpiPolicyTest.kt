package com.example.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbEmergencyAudioKpiPolicyTest {

    @Test
    fun firstViolationCode_prefersRetriggerBeforeOtherViolations() {
        val code = AdsbEmergencyAudioKpiPolicy.firstViolationCode(
            AdsbEmergencyAudioKpiSnapshot(
                disableEventCount = 4,
                disableWithin5MinRate = 0.75,
                retriggerWithinCooldownCount = 1,
                determinismMismatchCount = 1
            )
        )

        assertEquals("retrigger_within_cooldown_count", code)
    }

    @Test
    fun firstViolationCode_reportsDeterminismMismatchWhenRetriggerClear() {
        val code = AdsbEmergencyAudioKpiPolicy.firstViolationCode(
            AdsbEmergencyAudioKpiSnapshot(
                disableEventCount = 3,
                disableWithin5MinRate = 0.5,
                retriggerWithinCooldownCount = 0,
                determinismMismatchCount = 2
            )
        )

        assertEquals("determinism_mismatch_count", code)
    }

    @Test
    fun firstViolationCode_reportsDisableRateOnlyWhenEventFloorMet() {
        val belowEventFloor = AdsbEmergencyAudioKpiPolicy.firstViolationCode(
            AdsbEmergencyAudioKpiSnapshot(
                disableEventCount = 1,
                disableWithin5MinRate = 1.0
            )
        )
        val aboveEventFloor = AdsbEmergencyAudioKpiPolicy.firstViolationCode(
            AdsbEmergencyAudioKpiSnapshot(
                disableEventCount = 2,
                disableWithin5MinRate = 0.21
            )
        )

        assertEquals(null, belowEventFloor)
        assertEquals("disable_within_5min_rate", aboveEventFloor)
    }

    @Test
    fun disableRateViolation_requiresStrictThresholdBreach() {
        val atThreshold = AdsbEmergencyAudioKpiPolicy.isDisableWithinFiveMinutesRateViolated(
            AdsbEmergencyAudioKpiSnapshot(
                disableEventCount = 2,
                disableWithin5MinRate = 0.20
            )
        )
        val aboveThreshold = AdsbEmergencyAudioKpiPolicy.isDisableWithinFiveMinutesRateViolated(
            AdsbEmergencyAudioKpiSnapshot(
                disableEventCount = 2,
                disableWithin5MinRate = 0.2000001
            )
        )

        assertFalse(atThreshold)
        assertTrue(aboveThreshold)
    }
}

