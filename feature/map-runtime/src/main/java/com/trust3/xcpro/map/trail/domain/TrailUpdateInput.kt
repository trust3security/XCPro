package com.trust3.xcpro.map.trail.domain

import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.weather.wind.model.WindState

/**
 * Inputs required to update the trail pipeline.
 */
data class TrailUpdateInput(
    val data: CompleteFlightData,
    val windState: WindState?,
    val isFlying: Boolean,
    val isReplay: Boolean,
    val windDriftEnabled: Boolean = false,
    val replayRetentionMode: TrailReplayRetentionMode = TrailReplayRetentionMode.DEFAULT
)
