package com.trust3.xcpro.sensors.domain

import com.trust3.xcpro.weather.wind.model.WindState

object LiveWindValidityPolicy {
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
