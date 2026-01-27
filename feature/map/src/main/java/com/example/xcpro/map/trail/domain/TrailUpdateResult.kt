package com.example.xcpro.map.trail.domain

/**
 * Output from trail updates, including render snapshot and change flags.
 */
internal data class TrailUpdateResult(
    val renderState: TrailRenderState,
    val sampleAdded: Boolean,
    val storeReset: Boolean,
    val modeChanged: Boolean
)
