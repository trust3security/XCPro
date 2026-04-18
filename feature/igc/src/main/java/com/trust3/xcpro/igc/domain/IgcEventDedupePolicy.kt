package com.trust3.xcpro.igc.domain

/**
 * Deterministic event dedupe/rate policy for E-record emission.
 */
class IgcEventDedupePolicy(
    private val dedupeWindowMs: Long = 5_000L,
    private val maxEventsPerSecond: Int = 1
) {

    data class State(
        val lastEmissionByKeyMs: Map<String, Long> = emptyMap(),
        val secondBucket: Long? = null,
        val emittedInSecond: Int = 0
    )

    data class Decision(
        val shouldEmit: Boolean,
        val nextState: State
    )

    init {
        require(dedupeWindowMs > 0L) { "dedupeWindowMs must be > 0" }
        require(maxEventsPerSecond > 0) { "maxEventsPerSecond must be > 0" }
    }

    fun evaluate(
        state: State,
        dedupeKey: String,
        monoNowMs: Long
    ): Decision {
        if (monoNowMs < 0L) {
            return Decision(shouldEmit = false, nextState = state)
        }
        val key = normalizePayload(dedupeKey)
        if (key.isBlank()) {
            return Decision(shouldEmit = false, nextState = state)
        }

        val lastEmission = state.lastEmissionByKeyMs[key]
        if (lastEmission != null && monoNowMs - lastEmission < dedupeWindowMs) {
            return Decision(shouldEmit = false, nextState = state)
        }

        val currentBucket = monoNowMs / 1_000L
        val nextCount = if (state.secondBucket == currentBucket) {
            state.emittedInSecond + 1
        } else {
            1
        }
        if (nextCount > maxEventsPerSecond) {
            return Decision(shouldEmit = false, nextState = state)
        }

        val nextMap = state.lastEmissionByKeyMs.toMutableMap()
        nextMap[key] = monoNowMs
        return Decision(
            shouldEmit = true,
            nextState = State(
                lastEmissionByKeyMs = nextMap,
                secondBucket = currentBucket,
                emittedInSecond = nextCount
            )
        )
    }

    companion object {
        fun normalizePayload(payload: String): String {
            return payload
                .replace("\r", " ")
                .replace("\n", " ")
                .trim()
                .uppercase()
        }
    }
}
