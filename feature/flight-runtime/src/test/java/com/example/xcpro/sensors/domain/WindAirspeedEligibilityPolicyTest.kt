package com.example.xcpro.sensors.domain

import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindAirspeedEligibilityPolicyTest {

    private val policy = WindAirspeedEligibilityPolicy()

    @Test
    fun reject_when_wind_state_missing() {
        val result = policy.evaluate(
            windState = null,
            gpsSpeedMs = 20.0,
            windSourceAlreadySelected = false
        )
        assertFalse(result.eligible)
        assertEquals(WindAirspeedDecisionCode.NO_WIND_STATE, result.code)
    }

    @Test
    fun reject_when_vector_missing() {
        val result = policy.evaluate(
            windState = WindState(
                vector = null,
                source = WindSource.MANUAL,
                quality = 5,
                stale = false,
                confidence = 1.0
            ),
            gpsSpeedMs = 20.0,
            windSourceAlreadySelected = false
        )
        assertFalse(result.eligible)
        assertEquals(WindAirspeedDecisionCode.NO_WIND_VECTOR, result.code)
    }

    @Test
    fun reject_when_wind_not_available() {
        val result = policy.evaluate(
            windState = WindState(
                vector = WindVector(east = 2.0, north = 1.0),
                source = WindSource.MANUAL,
                quality = 5,
                stale = true,
                confidence = 1.0
            ),
            gpsSpeedMs = 20.0,
            windSourceAlreadySelected = true
        )
        assertFalse(result.eligible)
        assertEquals(WindAirspeedDecisionCode.WIND_NOT_AVAILABLE, result.code)
    }

    @Test
    fun reject_when_confidence_below_threshold() {
        val result = policy.evaluate(
            windState = WindState(
                vector = WindVector(east = 2.0, north = 1.0),
                source = WindSource.MANUAL,
                quality = 5,
                stale = false,
                confidence = 0.05
            ),
            gpsSpeedMs = 20.0,
            windSourceAlreadySelected = false
        )
        assertFalse(result.eligible)
        assertEquals(WindAirspeedDecisionCode.WIND_LOW_CONFIDENCE, result.code)
    }

    @Test
    fun reject_when_gps_speed_too_low() {
        val result = policy.evaluate(
            windState = WindState(
                vector = WindVector(east = 2.0, north = 1.0),
                source = WindSource.MANUAL,
                quality = 5,
                stale = false,
                confidence = 1.0
            ),
            gpsSpeedMs = 4.9,
            windSourceAlreadySelected = false
        )
        assertFalse(result.eligible)
        assertEquals(WindAirspeedDecisionCode.GPS_SPEED_TOO_LOW, result.code)
    }

    @Test
    fun accept_when_all_conditions_pass() {
        val result = policy.evaluate(
            windState = WindState(
                vector = WindVector(east = 2.0, north = 1.0),
                source = WindSource.MANUAL,
                quality = 5,
                stale = false,
                confidence = 1.0
            ),
            gpsSpeedMs = 20.0,
            windSourceAlreadySelected = false
        )
        assertTrue(result.eligible)
        assertEquals(WindAirspeedDecisionCode.WIND_ACCEPTED, result.code)
    }

    @Test
    fun enter_rejects_confidence_between_enter_and_exit_thresholds() {
        val result = policy.evaluate(
            windState = WindState(
                vector = WindVector(east = 2.0, north = 1.0),
                source = WindSource.MANUAL,
                quality = 5,
                stale = false,
                confidence = 0.10
            ),
            gpsSpeedMs = 20.0,
            windSourceAlreadySelected = false
        )
        assertFalse(result.eligible)
        assertEquals(WindAirspeedDecisionCode.WIND_LOW_CONFIDENCE, result.code)
    }

    @Test
    fun exit_accepts_confidence_between_enter_and_exit_thresholds() {
        val result = policy.evaluate(
            windState = WindState(
                vector = WindVector(east = 2.0, north = 1.0),
                source = WindSource.MANUAL,
                quality = 5,
                stale = false,
                confidence = 0.10
            ),
            gpsSpeedMs = 20.0,
            windSourceAlreadySelected = true
        )
        assertTrue(result.eligible)
        assertEquals(WindAirspeedDecisionCode.WIND_ACCEPTED, result.code)
    }

    @Test
    fun enter_allows_confidence_at_boundary() {
        val result = policy.evaluate(
            windState = WindState(
                vector = WindVector(east = 2.0, north = 1.0),
                source = WindSource.MANUAL,
                quality = 5,
                stale = false,
                confidence = FlightMetricsConstants.WIND_AIRSPEED_ENTER_CONF_MIN
            ),
            gpsSpeedMs = 20.0,
            windSourceAlreadySelected = false
        )
        assertTrue(result.eligible)
    }

    @Test
    fun reject_when_gps_speed_at_boundary() {
        val result = policy.evaluate(
            windState = WindState(
                vector = WindVector(east = 2.0, north = 1.0),
                source = WindSource.MANUAL,
                quality = 5,
                stale = false,
                confidence = 1.0
            ),
            gpsSpeedMs = FlightMetricsConstants.WIND_AIRSPEED_MIN_GPS_SPEED_MS,
            windSourceAlreadySelected = false
        )
        assertFalse(result.eligible)
        assertEquals(WindAirspeedDecisionCode.GPS_SPEED_TOO_LOW, result.code)
    }
}
