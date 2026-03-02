package com.example.xcpro.map.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentIssueVisibilityPolicyTest {

    @Test
    fun reducePersistentIssueVisibility_dismissesOnlyAfterRecoveryDwell() {
        val dwellMs = 2_000L
        var state = PersistentIssueVisibilityState(visible = false, healthySinceMonoMs = null)

        state = reducePersistentIssueVisibility(
            previous = state,
            enabled = true,
            issueActive = true,
            healthy = false,
            recoveryDwellMs = dwellMs,
            nowMonoMs = 0L
        )
        assertTrue(state.visible)
        assertEquals(null, state.healthySinceMonoMs)

        state = reducePersistentIssueVisibility(
            previous = state,
            enabled = true,
            issueActive = false,
            healthy = true,
            recoveryDwellMs = dwellMs,
            nowMonoMs = 1_000L
        )
        assertTrue(state.visible)
        assertEquals(1_000L, state.healthySinceMonoMs)

        state = reducePersistentIssueVisibility(
            previous = state,
            enabled = true,
            issueActive = false,
            healthy = true,
            recoveryDwellMs = dwellMs,
            nowMonoMs = 2_900L
        )
        assertTrue(state.visible)
        assertEquals(1_000L, state.healthySinceMonoMs)

        state = reducePersistentIssueVisibility(
            previous = state,
            enabled = true,
            issueActive = false,
            healthy = true,
            recoveryDwellMs = dwellMs,
            nowMonoMs = 3_000L
        )
        assertFalse(state.visible)
        assertEquals(null, state.healthySinceMonoMs)
    }

    @Test
    fun reducePersistentIssueVisibility_resetsRecoveryTimerWhenIssueReturns() {
        val dwellMs = 2_000L
        var state = PersistentIssueVisibilityState(visible = true, healthySinceMonoMs = null)

        state = reducePersistentIssueVisibility(
            previous = state,
            enabled = true,
            issueActive = false,
            healthy = true,
            recoveryDwellMs = dwellMs,
            nowMonoMs = 1_000L
        )
        assertTrue(state.visible)
        assertEquals(1_000L, state.healthySinceMonoMs)

        state = reducePersistentIssueVisibility(
            previous = state,
            enabled = true,
            issueActive = true,
            healthy = false,
            recoveryDwellMs = dwellMs,
            nowMonoMs = 1_500L
        )
        assertTrue(state.visible)
        assertEquals(null, state.healthySinceMonoMs)

        state = reducePersistentIssueVisibility(
            previous = state,
            enabled = true,
            issueActive = false,
            healthy = true,
            recoveryDwellMs = dwellMs,
            nowMonoMs = 2_000L
        )
        assertTrue(state.visible)
        assertEquals(2_000L, state.healthySinceMonoMs)

        state = reducePersistentIssueVisibility(
            previous = state,
            enabled = true,
            issueActive = false,
            healthy = true,
            recoveryDwellMs = dwellMs,
            nowMonoMs = 4_100L
        )
        assertFalse(state.visible)
        assertEquals(null, state.healthySinceMonoMs)
    }

    @Test
    fun reducePersistentIssueVisibility_disablesImmediately() {
        val state = reducePersistentIssueVisibility(
            previous = PersistentIssueVisibilityState(visible = true, healthySinceMonoMs = 5_000L),
            enabled = false,
            issueActive = true,
            healthy = false,
            recoveryDwellMs = 2_000L,
            nowMonoMs = 6_000L
        )
        assertFalse(state.visible)
        assertEquals(null, state.healthySinceMonoMs)
    }
}
