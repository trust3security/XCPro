package com.trust3.xcpro.map.trail.domain

import com.trust3.xcpro.map.trail.TrailGeoPoint
import com.trust3.xcpro.map.trail.TrailPoint

/**
 * Snapshot for rendering the trail without UI/map types.
 */
data class TrailRenderState(
    val points: List<TrailPoint>,
    val currentLocation: TrailGeoPoint,
    val currentTimeMillis: Long,
    val windSpeedMs: Double,
    val windDirectionFromDeg: Double,
    val isCircling: Boolean,
    val isTurnSmoothing: Boolean,
    val isReplay: Boolean,
    val timeBase: TrailTimeBase
)
