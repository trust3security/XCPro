package com.example.xcpro.map

data class MapOverlayRuntimeTrafficCounters(
    val overlayFrontOrderApplyCount: Long,
    val overlayFrontOrderSkippedCount: Long,
    val adsbIconUnknownRenderCount: Long,
    val adsbIconLegacyUnknownRenderCount: Long,
    val adsbIconResolveLatencySampleCount: Long,
    val adsbIconResolveLatencyLastMs: Long?,
    val adsbIconResolveLatencyMaxMs: Long?,
    val adsbIconResolveLatencyAverageMs: Long?,
    val adsbDefaultMediumUnknownIconEnabled: Boolean
)

data class OverlayFrontOrderSignature(
    val mapId: Int,
    val styleId: Int,
    val layerCount: Int,
    val topLayerId: String?,
    val blueOverlayId: Int,
    val ognOverlayId: Int,
    val ognTargetRingOverlayId: Int,
    val ognTargetLineOverlayId: Int,
    val ognOwnshipTargetBadgeOverlayId: Int,
    val adsbOverlayId: Int
)

internal data class AdsbRenderThrottleState(
    var lastRenderMonoMs: Long = 0L,
    var pendingJob: kotlinx.coroutines.Job? = null,
    var pendingDueMonoMs: Long = Long.MAX_VALUE
)
