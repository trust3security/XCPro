package com.trust3.xcpro.puretrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PureTrackPollingPolicyTest {

    @Test
    fun nextDecision_visibleFlyingUsesThirtySecondCadence() {
        val delayMs = PureTrackPollingPolicy().delayFor(
            PureTrackPollingInput(
                visibilityState = PureTrackPollingVisibilityState.VISIBLE_FLYING
            )
        )

        assertEquals(30_000L, delayMs)
    }

    @Test
    fun nextDecision_visibleIdleUsesSixtySecondCadence() {
        val delayMs = PureTrackPollingPolicy().delayFor(
            PureTrackPollingInput(
                visibilityState = PureTrackPollingVisibilityState.VISIBLE_IDLE
            )
        )

        assertEquals(60_000L, delayMs)
    }

    @Test
    fun nextDecision_notVisiblePauses() {
        val decision = PureTrackPollingPolicy().nextDecision(
            PureTrackPollingInput(
                visibilityState = PureTrackPollingVisibilityState.NOT_VISIBLE,
                retryAfterSeconds = 300,
                consecutiveErrorCount = 4
            )
        )

        assertTrue(decision is PureTrackPollingDecision.Paused)
    }

    @Test
    fun nextDecision_shortRetryAfterDoesNotOverrideBaseCadence() {
        val delayMs = PureTrackPollingPolicy().delayFor(
            PureTrackPollingInput(
                visibilityState = PureTrackPollingVisibilityState.VISIBLE_FLYING,
                retryAfterSeconds = 5
            )
        )

        assertEquals(30_000L, delayMs)
    }

    @Test
    fun nextDecision_longRetryAfterIsHonored() {
        val delayMs = PureTrackPollingPolicy().delayFor(
            PureTrackPollingInput(
                visibilityState = PureTrackPollingVisibilityState.VISIBLE_FLYING,
                retryAfterSeconds = 90
            )
        )

        assertEquals(90_000L, delayMs)
    }

    @Test
    fun nextDecision_nonPositiveRetryAfterDoesNotProduceTooFastDelay() {
        val policy = PureTrackPollingPolicy()

        val zeroRetryAfterDelayMs = policy.delayFor(
            PureTrackPollingInput(
                visibilityState = PureTrackPollingVisibilityState.VISIBLE_FLYING,
                retryAfterSeconds = 0
            )
        )
        val negativeRetryAfterDelayMs = policy.delayFor(
            PureTrackPollingInput(
                visibilityState = PureTrackPollingVisibilityState.VISIBLE_FLYING,
                retryAfterSeconds = -5
            )
        )

        assertEquals(30_000L, zeroRetryAfterDelayMs)
        assertEquals(30_000L, negativeRetryAfterDelayMs)
    }

    @Test
    fun nextDecision_negativeErrorCountIsIgnored() {
        val delayMs = PureTrackPollingPolicy().delayFor(
            PureTrackPollingInput(
                visibilityState = PureTrackPollingVisibilityState.VISIBLE_FLYING,
                consecutiveErrorCount = -1
            )
        )

        assertEquals(30_000L, delayMs)
    }

    @Test
    fun nextDecision_errorBackoffSequenceIsConservativeMax() {
        val policy = PureTrackPollingPolicy()

        assertEquals(30_000L, policy.delayFor(errorCount = 1))
        assertEquals(60_000L, policy.delayFor(errorCount = 2))
        assertEquals(120_000L, policy.delayFor(errorCount = 3))
        assertEquals(300_000L, policy.delayFor(errorCount = 4))
        assertEquals(300_000L, policy.delayFor(errorCount = 5))
    }

    @Test
    fun nextDecision_usesMaxOfBaseRetryAfterBackoffAndMinimumGuard() {
        val delayMs = PureTrackPollingPolicy().delayFor(
            PureTrackPollingInput(
                visibilityState = PureTrackPollingVisibilityState.VISIBLE_IDLE,
                retryAfterSeconds = 45,
                consecutiveErrorCount = 3
            )
        )

        assertEquals(120_000L, delayMs)
    }

    @Test
    fun nextDecision_minimumGuardAppliesToCustomFastCadence() {
        val delayMs = PureTrackPollingPolicy(
            visibleFlyingCadenceMs = 5_000L,
            visibleIdleCadenceMs = 5_000L,
            minimumDelayMs = 15_000L
        ).delayFor(
            PureTrackPollingInput(
                visibilityState = PureTrackPollingVisibilityState.VISIBLE_FLYING
            )
        )

        assertEquals(15_000L, delayMs)
    }

    @Test
    fun constructor_defensivelyCopiesBackoffList() {
        val backoff = mutableListOf(30_000L, 60_000L)
        val policy = PureTrackPollingPolicy(errorBackoffMs = backoff)
        backoff[0] = 300_000L

        val delayMs = policy.delayFor(errorCount = 1)

        assertEquals(30_000L, delayMs)
    }

    private fun PureTrackPollingPolicy.delayFor(errorCount: Int): Long =
        delayFor(
            PureTrackPollingInput(
                visibilityState = PureTrackPollingVisibilityState.VISIBLE_FLYING,
                consecutiveErrorCount = errorCount
            )
        )

    private fun PureTrackPollingPolicy.delayFor(input: PureTrackPollingInput): Long {
        val decision = nextDecision(input)
        assertTrue(decision is PureTrackPollingDecision.Delay)
        return (decision as PureTrackPollingDecision.Delay).delayMs
    }
}
