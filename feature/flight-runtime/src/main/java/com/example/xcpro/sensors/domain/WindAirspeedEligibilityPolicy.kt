package com.example.xcpro.sensors.domain

import com.example.xcpro.weather.wind.model.WindState

internal enum class WindAirspeedDecisionCode {
    WIND_ACCEPTED,
    NO_WIND_STATE,
    NO_WIND_VECTOR,
    WIND_NOT_AVAILABLE,
    WIND_LOW_CONFIDENCE,
    GPS_SPEED_TOO_LOW
}

internal data class WindAirspeedDecision(
    val eligible: Boolean,
    val code: WindAirspeedDecisionCode
)

internal class WindAirspeedEligibilityPolicy(
    private val enterConfidenceMin: Double = FlightMetricsConstants.WIND_AIRSPEED_ENTER_CONF_MIN,
    private val exitConfidenceMin: Double = FlightMetricsConstants.WIND_AIRSPEED_EXIT_CONF_MIN,
    private val minGpsSpeedMs: Double = FlightMetricsConstants.WIND_AIRSPEED_MIN_GPS_SPEED_MS
) {
    fun evaluate(
        windState: WindState?,
        gpsSpeedMs: Double,
        windSourceAlreadySelected: Boolean
    ): WindAirspeedDecision {
        if (windState == null) {
            return WindAirspeedDecision(
                eligible = false,
                code = WindAirspeedDecisionCode.NO_WIND_STATE
            )
        }
        if (windState.vector == null) {
            return WindAirspeedDecision(
                eligible = false,
                code = WindAirspeedDecisionCode.NO_WIND_VECTOR
            )
        }
        if (!windState.isAvailable) {
            return WindAirspeedDecision(
                eligible = false,
                code = WindAirspeedDecisionCode.WIND_NOT_AVAILABLE
            )
        }
        val confidenceThreshold = if (windSourceAlreadySelected) {
            exitConfidenceMin
        } else {
            enterConfidenceMin
        }
        if (windState.confidence < confidenceThreshold) {
            return WindAirspeedDecision(
                eligible = false,
                code = WindAirspeedDecisionCode.WIND_LOW_CONFIDENCE
            )
        }
        if (!gpsSpeedMs.isFinite() || gpsSpeedMs <= minGpsSpeedMs) {
            return WindAirspeedDecision(
                eligible = false,
                code = WindAirspeedDecisionCode.GPS_SPEED_TOO_LOW
            )
        }
        return WindAirspeedDecision(
            eligible = true,
            code = WindAirspeedDecisionCode.WIND_ACCEPTED
        )
    }
}
