package com.example.xcpro.tasks.racing.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingAdvanceStateTest {

    @Test
    fun shouldAdvance_turnpointNearMissNeverAutoAdvances() {
        val advanceState = RacingAdvanceState()
        advanceState.setArmed(true)
        advanceState.onStartAdvanced()

        assertFalse(advanceState.shouldAdvance(RacingNavigationEventType.TURNPOINT_NEAR_MISS))
    }

    @Test
    fun shouldAdvance_turnpointAdvancesWhenTurnLegIsArmed() {
        val advanceState = RacingAdvanceState()
        advanceState.setArmed(true)
        advanceState.onStartAdvanced()

        assertTrue(advanceState.shouldAdvance(RacingNavigationEventType.TURNPOINT))
    }
}
