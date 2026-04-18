package com.trust3.xcpro.igc.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcEventDedupePolicyTest {

    @Test
    fun evaluate_suppressesDuplicateWithinWindow() {
        val policy = IgcEventDedupePolicy(dedupeWindowMs = 5_000L, maxEventsPerSecond = 2)
        var state = IgcEventDedupePolicy.State()

        val first = policy.evaluate(state, "1|SYS|THERMAL", monoNowMs = 10_000L)
        state = first.nextState
        val second = policy.evaluate(state, "1|SYS|THERMAL", monoNowMs = 12_000L)

        assertTrue(first.shouldEmit)
        assertFalse(second.shouldEmit)
    }

    @Test
    fun evaluate_allowsSameKeyAfterWindow() {
        val policy = IgcEventDedupePolicy(dedupeWindowMs = 5_000L, maxEventsPerSecond = 2)
        var state = IgcEventDedupePolicy.State()

        state = policy.evaluate(state, "1|SYS|THERMAL", monoNowMs = 10_000L).nextState
        val afterWindow = policy.evaluate(state, "1|SYS|THERMAL", monoNowMs = 15_100L)

        assertTrue(afterWindow.shouldEmit)
    }

    @Test
    fun evaluate_enforcesGlobalPerSecondRateLimit() {
        val policy = IgcEventDedupePolicy(dedupeWindowMs = 5_000L, maxEventsPerSecond = 1)
        var state = IgcEventDedupePolicy.State()

        val first = policy.evaluate(state, "1|SYS|A", monoNowMs = 20_000L)
        state = first.nextState
        val second = policy.evaluate(state, "1|TSK|B", monoNowMs = 20_900L)

        assertTrue(first.shouldEmit)
        assertFalse(second.shouldEmit)
    }
}
