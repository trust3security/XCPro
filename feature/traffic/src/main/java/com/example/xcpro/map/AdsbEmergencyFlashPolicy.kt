package com.example.xcpro.map




object AdsbEmergencyFlashPolicy {
    private const val EMERGENCY_FLASH_PERIOD_MS = 800L
    private const val EMERGENCY_FLASH_MIN_ALPHA = 0.35

    fun alphaForTarget(
        target: AdsbTrafficUiModel,
        nowMonoMs: Long,
        liveAlpha: Double,
        staleAlpha: Double,
        emergencyFlashEnabled: Boolean = true
    ): Double {
        if (isStaleForVisual(target)) return staleAlpha
        if (!emergencyFlashEnabled) return liveAlpha
        if (target.proximityTier != AdsbProximityTier.EMERGENCY) return liveAlpha
        return emergencyPulseAlpha(
            nowMonoMs = nowMonoMs,
            maxAlpha = liveAlpha
        )
    }

    private fun isStaleForVisual(target: AdsbTrafficUiModel): Boolean =
        target.isPositionStale || target.isStale

    fun emergencyPulseAlpha(
        nowMonoMs: Long,
        maxAlpha: Double,
        minAlpha: Double = EMERGENCY_FLASH_MIN_ALPHA,
        periodMs: Long = EMERGENCY_FLASH_PERIOD_MS
    ): Double {
        val boundedMaxAlpha = maxAlpha.coerceIn(0.0, 1.0)
        val boundedMinAlpha = minAlpha.coerceIn(0.0, boundedMaxAlpha)
        if (periodMs <= 0L) return boundedMaxAlpha
        if (boundedMaxAlpha <= boundedMinAlpha) return boundedMinAlpha

        val phase = ((nowMonoMs % periodMs + periodMs) % periodMs).toDouble() / periodMs.toDouble()
        val triangle = if (phase < 0.5) {
            phase * 2.0
        } else {
            (1.0 - phase) * 2.0
        }
        return boundedMinAlpha + (boundedMaxAlpha - boundedMinAlpha) * triangle
    }
}
