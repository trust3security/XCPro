package com.example.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbPollingHealthPolicyTest {

    @Test
    fun recordFailure_belowThreshold_keepsCircuitClosed() {
        val policy = newPolicy()

        val opened = policy.recordFailureAndMaybeOpenCircuit(nowMonoMs = 100L)

        assertFalse(opened)
        assertEquals(1, policy.consecutiveFailureCount)
        assertEquals(100L, policy.lastFailureMonoMs)
        assertNull(policy.nextRetryMonoMs)
        assertNull(policy.circuitOpenProbeDelayMsOrNull())
    }

    @Test
    fun recordFailure_atThreshold_opensCircuitAndSchedulesProbe() {
        val policy = newPolicy()
        policy.recordFailureAndMaybeOpenCircuit(nowMonoMs = 100L)
        policy.recordFailureAndMaybeOpenCircuit(nowMonoMs = 200L)

        val opened = policy.recordFailureAndMaybeOpenCircuit(nowMonoMs = 300L)

        assertTrue(opened)
        assertEquals(3, policy.consecutiveFailureCount)
        assertEquals(300L, policy.lastFailureMonoMs)
        assertEquals(30_300L, policy.nextRetryMonoMs)
        assertEquals(30_000L, policy.circuitOpenProbeDelayMsOrNull())
    }

    @Test
    fun failureInHalfOpen_reopensCircuitImmediately() {
        val policy = newPolicy()
        policy.recordFailureAndMaybeOpenCircuit(nowMonoMs = 100L)
        policy.recordFailureAndMaybeOpenCircuit(nowMonoMs = 200L)
        policy.recordFailureAndMaybeOpenCircuit(nowMonoMs = 300L)
        assertTrue(policy.transitionOpenToHalfOpen())

        val reopened = policy.recordFailureAndMaybeOpenCircuit(nowMonoMs = 400L)

        assertTrue(reopened)
        assertEquals(4, policy.consecutiveFailureCount)
        assertEquals(400L, policy.lastFailureMonoMs)
        assertEquals(30_400L, policy.nextRetryMonoMs)
        assertEquals(30_000L, policy.circuitOpenProbeDelayMsOrNull())
    }

    @Test
    fun resetAfterSuccess_closesCircuitAndClearsRetrySchedule() {
        val policy = newPolicy()
        policy.recordFailureAndMaybeOpenCircuit(nowMonoMs = 100L)
        policy.recordFailureAndMaybeOpenCircuit(nowMonoMs = 200L)
        policy.recordFailureAndMaybeOpenCircuit(nowMonoMs = 300L)

        policy.resetAfterSuccessfulRequest()

        assertEquals(0, policy.consecutiveFailureCount)
        assertEquals(300L, policy.lastFailureMonoMs)
        assertNull(policy.nextRetryMonoMs)
        assertNull(policy.circuitOpenProbeDelayMsOrNull())
    }

    @Test
    fun resetForStop_clearsFailureTelemetry() {
        val policy = newPolicy()
        policy.markFailureEvent(nowMonoMs = 123L)
        policy.scheduleNextRetry(nowMonoMs = 456L, waitMs = 7_000L)

        policy.resetForStop()

        assertEquals(0, policy.consecutiveFailureCount)
        assertNull(policy.lastFailureMonoMs)
        assertNull(policy.nextRetryMonoMs)
        assertNull(policy.circuitOpenProbeDelayMsOrNull())
    }

    private fun newPolicy(): AdsbPollingHealthPolicy =
        AdsbPollingHealthPolicy(
            circuitBreakerFailureThreshold = 3,
            circuitBreakerOpenWindowMs = 30_000L
        )
}
