package com.example.xcpro.igc.domain

/**
 * Enforces configurable B-record cadence for live stream emission.
 */
class IgcBRecordCadencePolicy(
    private val config: Config = Config()
) {

    data class Config(
        val intervalSeconds: Int = 1
    ) {
        init {
            require(intervalSeconds in 1..5) { "intervalSeconds must be within 1..5: $intervalSeconds" }
        }
    }

    fun shouldEmit(
        sampleWallTimeMs: Long,
        lastEmissionWallTimeMs: Long?
    ): Boolean {
        if (sampleWallTimeMs <= 0L) return false
        val previous = lastEmissionWallTimeMs ?: return true
        if (sampleWallTimeMs <= previous) return false
        val intervalMs = config.intervalSeconds * 1_000L
        return sampleWallTimeMs - previous >= intervalMs
    }
}
