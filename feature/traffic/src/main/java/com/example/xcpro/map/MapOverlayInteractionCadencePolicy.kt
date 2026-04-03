package com.example.xcpro.map

import kotlin.math.max

const val OGN_INTERACTION_MIN_RENDER_INTERVAL_MS = 900L
const val ADSB_INTERACTION_MIN_RENDER_INTERVAL_MS = 750L
const val OVERLAY_FRONT_ORDER_INTERACTION_MIN_APPLY_INTERVAL_MS = 500L
const val TRAFFIC_PROJECTION_INVALIDATION_MIN_RENDER_INTERVAL_MS = 120L
const val TRAFFIC_PROJECTION_INVALIDATION_INTERACTION_MIN_RENDER_INTERVAL_MS = 250L

fun resolveInteractionAwareIntervalMs(
    baseIntervalMs: Long,
    interactionActive: Boolean,
    interactionFloorMs: Long
): Long {
    if (!interactionActive) return baseIntervalMs
    return max(baseIntervalMs, interactionFloorMs)
}

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

fun resolveTrafficProjectionInvalidationIntervalMs(
    interactionActive: Boolean
): Long = resolveInteractionAwareIntervalMs(
    baseIntervalMs = TRAFFIC_PROJECTION_INVALIDATION_MIN_RENDER_INTERVAL_MS,
    interactionActive = interactionActive,
    interactionFloorMs = TRAFFIC_PROJECTION_INVALIDATION_INTERACTION_MIN_RENDER_INTERVAL_MS
)
