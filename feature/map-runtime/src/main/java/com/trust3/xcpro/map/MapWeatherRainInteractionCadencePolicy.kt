package com.trust3.xcpro.map

const val WEATHER_RAIN_INTERACTION_MIN_APPLY_INTERVAL_MS = 1_200L

fun shouldSkipWeatherRainApplyDuringInteraction(
    interactionActive: Boolean,
    enabled: Boolean,
    hasFrameSelection: Boolean,
    lastAppliedMonoMs: Long,
    nowMonoMs: Long,
    minIntervalMs: Long = WEATHER_RAIN_INTERACTION_MIN_APPLY_INTERVAL_MS
): Boolean {
    if (!interactionActive || !enabled || !hasFrameSelection) return false
    if (lastAppliedMonoMs <= 0L) return false
    val elapsedMs = nowMonoMs - lastAppliedMonoMs
    return elapsedMs in Long.MIN_VALUE until minIntervalMs
}

fun effectiveWeatherRainTransitionDurationMs(
    interactionActive: Boolean,
    requestedDurationMs: Long
): Long = if (interactionActive) 0L else requestedDurationMs.coerceAtLeast(0L)
