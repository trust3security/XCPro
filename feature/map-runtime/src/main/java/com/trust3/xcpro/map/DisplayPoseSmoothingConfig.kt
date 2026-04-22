package com.trust3.xcpro.map

/**
 * UI-only smoothing parameters for DisplayPoseSmoother.
 * Defaults preserve current behavior.
 */
data class DisplayPoseSmoothingConfig(
    val posSmoothMs: Double = DEFAULT_POS_SMOOTH_MS,
    val headingSmoothMs: Double = DEFAULT_HEADING_SMOOTH_MS,
    val deadReckonLimitMs: Long = DEFAULT_DEAD_RECKON_LIMIT_MS,
    val staleFixTimeoutMs: Long = DEFAULT_STALE_FIX_TIMEOUT_MS,
    val frameActiveWindowMs: Long? = null
) {
    companion object {
        const val DEFAULT_POS_SMOOTH_MS = 300.0
        const val DEFAULT_HEADING_SMOOTH_MS = 250.0
        const val DEFAULT_DEAD_RECKON_LIMIT_MS = 500L
        const val DEFAULT_STALE_FIX_TIMEOUT_MS = 2_000L
    }
}
