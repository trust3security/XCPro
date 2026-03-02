package com.example.xcpro.sensors.domain

import com.example.xcpro.weather.wind.model.WindState

internal object LiveWindValidityPolicy {
    fun isWindAirspeedSource(airspeedSourceLabel: String): Boolean =
        airspeedSourceLabel == AirspeedSource.WIND_VECTOR.label

    fun isLiveWindUsable(
        windState: WindState?,
        airspeedSourceLabel: String
    ): Boolean {
        if (!isWindAirspeedSource(airspeedSourceLabel)) {
            return false
        }
        val state = windState ?: return false
        val vector = state.vector ?: return false
        if (!state.isAvailable) return false
        return vector.speed.isFinite() && vector.speed > FlightMetricsConstants.LIVE_WIND_VALID_MIN_SPEED_MS
    }
}
