package com.example.xcpro.map.trail.domain

import com.example.xcpro.map.trail.TrailGeoPoint
import com.example.xcpro.map.trail.TrailPoint

/**
 * Snapshot for rendering the trail without UI/map types.
 */
internal data class TrailRenderState(
    val points: List<TrailPoint>,
    val currentLocation: TrailGeoPoint,
    val currentTimeMillis: Long,
    val windSpeedMs: Double,
    val windDirectionFromDeg: Double,
    val isCircling: Boolean,
    val isReplay: Boolean,
    val timeBase: TrailTimeBase
)
