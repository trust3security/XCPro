package com.trust3.xcpro.puretrack

internal class PureTrackPollingPolicy(
    private val visibleFlyingCadenceMs: Long = 30_000L,
    private val visibleIdleCadenceMs: Long = 60_000L,
    private val minimumDelayMs: Long = 15_000L,
    errorBackoffMs: List<Long> = listOf(30_000L, 60_000L, 120_000L, 300_000L)
) {
    private val errorBackoffMs: List<Long> = errorBackoffMs.toList()

    init {
        require(visibleFlyingCadenceMs > 0L) { "visibleFlyingCadenceMs must be positive" }
        require(visibleIdleCadenceMs > 0L) { "visibleIdleCadenceMs must be positive" }
        require(minimumDelayMs > 0L) { "minimumDelayMs must be positive" }
        require(this.errorBackoffMs.isNotEmpty()) { "errorBackoffMs must not be empty" }
        require(this.errorBackoffMs.all { it > 0L }) { "errorBackoffMs values must be positive" }
    }

    fun nextDecision(input: PureTrackPollingInput): PureTrackPollingDecision {
        val baseCadenceMs = when (input.visibilityState) {
            PureTrackPollingVisibilityState.NOT_VISIBLE -> {
                return PureTrackPollingDecision.Paused
            }
            PureTrackPollingVisibilityState.VISIBLE_FLYING -> visibleFlyingCadenceMs
            PureTrackPollingVisibilityState.VISIBLE_IDLE -> visibleIdleCadenceMs
        }

        return PureTrackPollingDecision.Delay(
            delayMs = maxOf(
                baseCadenceMs,
                input.retryAfterSeconds.toDelayMsOrZero(),
                input.consecutiveErrorCount.toBackoffMs(),
                minimumDelayMs
            )
        )
    }

    private fun Long?.toDelayMsOrZero(): Long {
        val seconds = this ?: return 0L
        if (seconds <= 0L) return 0L
        return seconds.saturatingMultiplyByMillis()
    }

    private fun Long.saturatingMultiplyByMillis(): Long {
        val maxSafeSeconds = Long.MAX_VALUE / MILLIS_PER_SECOND
        return if (this >= maxSafeSeconds) {
            Long.MAX_VALUE
        } else {
            this * MILLIS_PER_SECOND
        }
    }

    private fun Int.toBackoffMs(): Long {
        if (this <= 0) return 0L
        val index = (this - 1).coerceAtMost(errorBackoffMs.lastIndex)
        return errorBackoffMs[index]
    }

    private companion object {
        private const val MILLIS_PER_SECOND = 1_000L
    }
}

internal data class PureTrackPollingInput(
    val visibilityState: PureTrackPollingVisibilityState,
    val retryAfterSeconds: Long? = null,
    val consecutiveErrorCount: Int = 0
)

internal enum class PureTrackPollingVisibilityState {
    NOT_VISIBLE,
    VISIBLE_FLYING,
    VISIBLE_IDLE
}

internal sealed interface PureTrackPollingDecision {
    data object Paused : PureTrackPollingDecision

    data class Delay(
        val delayMs: Long
    ) : PureTrackPollingDecision
}
