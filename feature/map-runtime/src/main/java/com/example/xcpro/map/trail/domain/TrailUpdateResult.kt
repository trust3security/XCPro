package com.example.xcpro.map.trail.domain

/**
 * Output from trail updates, including render snapshot and change flags.
 */
data class TrailUpdateResult(
    val renderState: TrailRenderState,
    val sampleAdded: Boolean,
    val storeReset: Boolean,
    val modeChanged: Boolean,
    val requiresFullRender: Boolean,
    val invalidationReason: TrailRenderInvalidationReason? = null
)

enum class TrailRenderInvalidationReason {
    SAMPLE_ADDED,
    STORE_RESET,
    MODE_CHANGED,
    CIRCLING_CHANGED,
    WIND_CHANGED
}
