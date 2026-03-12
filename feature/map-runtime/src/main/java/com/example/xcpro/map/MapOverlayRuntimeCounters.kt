package com.example.xcpro.map

data class MapOverlayRuntimeCounters(
    val overlayFrontOrderApplyCount: Long,
    val overlayFrontOrderSkippedCount: Long,
    val aatPreviewForwardCount: Long,
    val adsbIconUnknownRenderCount: Long,
    val adsbIconLegacyUnknownRenderCount: Long,
    val adsbIconResolveLatencySampleCount: Long,
    val adsbIconResolveLatencyLastMs: Long?,
    val adsbIconResolveLatencyMaxMs: Long?,
    val adsbIconResolveLatencyAverageMs: Long?,
    val adsbDefaultMediumUnknownIconEnabled: Boolean
)
