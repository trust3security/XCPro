package com.example.xcpro.adsb

internal data class AdsbPollingHealthSnapshot(
    val consecutiveFailureCount: Int,
    val nextRetryMonoMs: Long?,
    val lastFailureMonoMs: Long?
)

internal class AdsbPollingHealthPolicy(
    private val circuitBreakerFailureThreshold: Int,
    private val circuitBreakerOpenWindowMs: Long
) {
    private val lock = Any()

    private var consecutiveFailureCountValue: Int = 0
    private var nextRetryMonoMsValue: Long? = null
    private var lastFailureMonoMsValue: Long? = null

    val consecutiveFailureCount: Int
        get() = synchronized(lock) { consecutiveFailureCountValue }

    val nextRetryMonoMs: Long?
        get() = synchronized(lock) { nextRetryMonoMsValue }

    val lastFailureMonoMs: Long?
        get() = synchronized(lock) { lastFailureMonoMsValue }

    private var circuitBreakerState: CircuitBreakerState = CircuitBreakerState.Closed

    fun markFailureEvent(nowMonoMs: Long) {
        synchronized(lock) {
            lastFailureMonoMsValue = nowMonoMs
        }
    }

    fun recordFailureAndMaybeOpenCircuit(nowMonoMs: Long): Boolean = synchronized(lock) {
        lastFailureMonoMsValue = nowMonoMs
        consecutiveFailureCountValue += 1
        val shouldOpen = when (circuitBreakerState) {
            CircuitBreakerState.Closed -> consecutiveFailureCountValue >= circuitBreakerFailureThreshold
            CircuitBreakerState.HalfOpen -> true
            is CircuitBreakerState.Open -> true
        }
        if (!shouldOpen) return false

        circuitBreakerState = CircuitBreakerState.Open(circuitBreakerOpenWindowMs)
        scheduleNextRetryLocked(nowMonoMs = nowMonoMs, waitMs = circuitBreakerOpenWindowMs)
        return true
    }

    fun resetAfterSuccessfulRequest() {
        synchronized(lock) {
            consecutiveFailureCountValue = 0
            nextRetryMonoMsValue = null
            circuitBreakerState = CircuitBreakerState.Closed
        }
    }

    fun resetForStop() {
        synchronized(lock) {
            consecutiveFailureCountValue = 0
            nextRetryMonoMsValue = null
            lastFailureMonoMsValue = null
            circuitBreakerState = CircuitBreakerState.Closed
        }
    }

    fun clearNextRetry() {
        synchronized(lock) {
            nextRetryMonoMsValue = null
        }
    }

    fun scheduleNextRetry(nowMonoMs: Long, waitMs: Long) {
        synchronized(lock) {
            scheduleNextRetryLocked(nowMonoMs = nowMonoMs, waitMs = waitMs)
        }
    }

    fun circuitOpenProbeDelayMsOrNull(): Long? = synchronized(lock) {
        (circuitBreakerState as? CircuitBreakerState.Open)?.probeAfterMs
    }

    fun transitionOpenToHalfOpen(): Boolean = synchronized(lock) {
        val state = circuitBreakerState
        if (state !is CircuitBreakerState.Open) return false
        nextRetryMonoMsValue = null
        circuitBreakerState = CircuitBreakerState.HalfOpen
        return true
    }

    fun snapshotTelemetry(): AdsbPollingHealthSnapshot = synchronized(lock) {
        AdsbPollingHealthSnapshot(
            consecutiveFailureCount = consecutiveFailureCountValue,
            nextRetryMonoMs = nextRetryMonoMsValue,
            lastFailureMonoMs = lastFailureMonoMsValue
        )
    }

    private fun scheduleNextRetryLocked(nowMonoMs: Long, waitMs: Long) {
        nextRetryMonoMsValue = nowMonoMs + waitMs.coerceAtLeast(0L)
    }

    private sealed interface CircuitBreakerState {
        data object Closed : CircuitBreakerState
        data class Open(val probeAfterMs: Long) : CircuitBreakerState
        data object HalfOpen : CircuitBreakerState
    }
}
