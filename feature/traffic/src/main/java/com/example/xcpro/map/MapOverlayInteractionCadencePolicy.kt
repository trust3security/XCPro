package com.example.xcpro.map

import kotlin.math.max

const val OGN_INTERACTION_MIN_RENDER_INTERVAL_MS = 900L
const val ADSB_INTERACTION_MIN_RENDER_INTERVAL_MS = 750L
const val WEATHER_RAIN_INTERACTION_MIN_APPLY_INTERVAL_MS = 1_200L
const val OVERLAY_FRONT_ORDER_INTERACTION_MIN_APPLY_INTERVAL_MS = 500L
const val MAP_INTERACTION_DEACTIVATION_GRACE_MS = 500L

fun resolveInteractionAwareIntervalMs(
    baseIntervalMs: Long,
    interactionActive: Boolean,
    interactionFloorMs: Long
): Long {
    if (!interactionActive) return baseIntervalMs
    return max(baseIntervalMs, interactionFloorMs)
}

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

fun shouldThrottleOverlayFrontOrderDuringInteraction(
    interactionActive: Boolean,
    lastAppliedMonoMs: Long,
    nowMonoMs: Long,
    minIntervalMs: Long = OVERLAY_FRONT_ORDER_INTERACTION_MIN_APPLY_INTERVAL_MS
): Boolean {
    if (!interactionActive || lastAppliedMonoMs <= 0L) return false
    val elapsedMs = nowMonoMs - lastAppliedMonoMs
    return elapsedMs in Long.MIN_VALUE until minIntervalMs
}

fun resolveMapInteractionDeactivateDelayMs(
    interactionWasActive: Boolean,
    requestedActive: Boolean,
    graceMs: Long = MAP_INTERACTION_DEACTIVATION_GRACE_MS
): Long {
    if (requestedActive || !interactionWasActive) return 0L
    return graceMs.coerceAtLeast(0L)
}

