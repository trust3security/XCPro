package com.example.xcpro.map.trail.domain

import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.weather.wind.model.WindState

/**
 * Inputs required to update the trail pipeline.
 */
data class TrailUpdateInput(
    val data: CompleteFlightData,
    val windState: WindState?,
    val isFlying: Boolean,
    val isReplay: Boolean
)
