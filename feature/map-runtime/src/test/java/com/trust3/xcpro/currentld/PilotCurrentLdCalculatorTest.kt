package com.trust3.xcpro.currentld

import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.PressureHpa
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.common.units.VerticalSpeedMs
import com.trust3.xcpro.core.flight.calculations.ConfidenceLevel
import com.trust3.xcpro.glider.SpeedBoundsMs
import com.trust3.xcpro.glider.StillAirSinkProvider
import com.trust3.xcpro.navigation.WaypointNavigationSnapshot
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.sensors.domain.FlyingState
import com.trust3.xcpro.weather.wind.model.WindState
import com.trust3.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PilotCurrentLdCalculatorTest {

    @Test
    fun matched_window_uses_integrated_distance_over_height_loss() {
        val calculator = PilotCurrentLdCalculator(FakeStillAirSinkProvider())
        var state = PilotCurrentLdState()

        state = calculator.update(state, input(sampleTimeMillis = 0L, trueAirspeedMs = 10.0, teVario = -1.0))
        state = calculator.update(state, input(sampleTimeMillis = 2_000L, trueAirspeedMs = 30.0, teVario = -1.0))
        state = calculator.update(state, input(sampleTimeMillis = 8_000L, trueAirspeedMs = 30.0, teVario = -3.0))

        assertTrue(state.snapshot.pilotCurrentLDValid)
        assertEquals(PilotCurrentLdSource.FUSED_ZERO_WIND, state.snapshot.pilotCurrentLDSource)
        assertEquals(220.0 / 14.0, state.snapshot.pilotCurrentLD.toDouble(), 1e-3)
    }

    @Test
    fun publishes_after_minimum_fill_without_wind() {
        val calculator = PilotCurrentLdCalculator(FakeStillAirSinkProvider())
        var state = PilotCurrentLdState()

        state = calculator.update(state, input(sampleTimeMillis = 0L))
        state = calculator.update(state, input(sampleTimeMillis = 4_000L))
        assertFalse(state.snapshot.pilotCurrentLDValid)

        state = calculator.update(state, input(sampleTimeMillis = 8_000L))

        assertTrue(state.snapshot.pilotCurrentLDValid)
        assertEquals(PilotCurrentLdSource.FUSED_ZERO_WIND, state.snapshot.pilotCurrentLDSource)
        assertEquals(30.0, state.snapshot.pilotCurrentLD.toDouble(), 1e-6)
    }

    @Test
    fun valid_wind_projection_changes_value_in_expected_direction() {
        val headwindValue = runStraightWindow(
            windState = windState(east = -5.0, north = 0.0),
            waypointBearingDeg = 90.0
        ).pilotCurrentLD
        val tailwindValue = runStraightWindow(
            windState = windState(east = 5.0, north = 0.0),
            waypointBearingDeg = 90.0
        ).pilotCurrentLD

        assertTrue(tailwindValue > headwindValue)
        assertEquals(25.0, headwindValue.toDouble(), 1e-6)
        assertEquals(35.0, tailwindValue.toDouble(), 1e-6)
    }

    @Test
    fun missing_direction_with_valid_wind_degrades_to_zero_wind() {
        val calculator = PilotCurrentLdCalculator(FakeStillAirSinkProvider())
        var state = PilotCurrentLdState()

        state = calculator.update(
            state,
            input(sampleTimeMillis = 0L, gpsBearingDeg = Double.NaN, includeGps = false, windState = windState(east = 5.0, north = 0.0))
        )
        state = calculator.update(
            state,
            input(sampleTimeMillis = 4_000L, gpsBearingDeg = Double.NaN, includeGps = false, windState = windState(east = 5.0, north = 0.0))
        )
        state = calculator.update(
            state,
            input(sampleTimeMillis = 8_000L, gpsBearingDeg = Double.NaN, includeGps = false, windState = windState(east = 5.0, north = 0.0))
        )

        assertTrue(state.snapshot.pilotCurrentLDValid)
        assertEquals(PilotCurrentLdSource.FUSED_ZERO_WIND, state.snapshot.pilotCurrentLDSource)
        assertEquals(30.0, state.snapshot.pilotCurrentLD.toDouble(), 1e-6)
    }

    @Test
    fun circling_holds_last_valid_value_then_expires() {
        val calculator = PilotCurrentLdCalculator(FakeStillAirSinkProvider())
        var state = PilotCurrentLdState()

        state = calculator.update(state, input(sampleTimeMillis = 0L))
        state = calculator.update(state, input(sampleTimeMillis = 4_000L))
        state = calculator.update(state, input(sampleTimeMillis = 8_000L))
        assertTrue(state.snapshot.pilotCurrentLDValid)

        state = calculator.update(state, input(sampleTimeMillis = 9_000L, isCircling = true))
        assertTrue(state.snapshot.pilotCurrentLDValid)
        assertEquals(PilotCurrentLdSource.HELD, state.snapshot.pilotCurrentLDSource)

        state = calculator.update(state, input(sampleTimeMillis = 29_001L, isCircling = true))
        assertFalse(state.snapshot.pilotCurrentLDValid)
        assertEquals(PilotCurrentLdSource.NONE, state.snapshot.pilotCurrentLDSource)
    }

    @Test
    fun turning_uses_same_hold_path_as_circling() {
        val calculator = PilotCurrentLdCalculator(FakeStillAirSinkProvider())
        var state = PilotCurrentLdState()

        state = calculator.update(state, input(sampleTimeMillis = 0L))
        state = calculator.update(state, input(sampleTimeMillis = 4_000L))
        state = calculator.update(state, input(sampleTimeMillis = 8_000L))
        assertTrue(state.snapshot.pilotCurrentLDValid)

        state = calculator.update(state, input(sampleTimeMillis = 9_000L, isTurning = true))

        assertTrue(state.snapshot.pilotCurrentLDValid)
        assertEquals(PilotCurrentLdSource.HELD, state.snapshot.pilotCurrentLDSource)
    }

    @Test
    fun resumes_with_fresh_window_after_thermal() {
        val calculator = PilotCurrentLdCalculator(FakeStillAirSinkProvider())
        var state = PilotCurrentLdState()

        state = calculator.update(state, input(sampleTimeMillis = 0L))
        state = calculator.update(state, input(sampleTimeMillis = 4_000L))
        state = calculator.update(state, input(sampleTimeMillis = 8_000L))
        assertTrue(state.snapshot.pilotCurrentLDValid)

        state = calculator.update(state, input(sampleTimeMillis = 9_000L, isCircling = true))
        assertEquals(PilotCurrentLdSource.HELD, state.snapshot.pilotCurrentLDSource)

        state = calculator.update(state, input(sampleTimeMillis = 10_000L))
        assertEquals(PilotCurrentLdSource.HELD, state.snapshot.pilotCurrentLDSource)
        state = calculator.update(state, input(sampleTimeMillis = 14_000L))
        assertEquals(PilotCurrentLdSource.HELD, state.snapshot.pilotCurrentLDSource)
        state = calculator.update(state, input(sampleTimeMillis = 18_000L))

        assertTrue(state.snapshot.pilotCurrentLDValid)
        assertEquals(PilotCurrentLdSource.FUSED_ZERO_WIND, state.snapshot.pilotCurrentLDSource)
        assertEquals(30.0, state.snapshot.pilotCurrentLD.toDouble(), 1e-6)
    }

    @Test
    fun uses_ground_fallback_when_airdata_is_not_authoritative() {
        val calculator = PilotCurrentLdCalculator(FakeStillAirSinkProvider())

        val state = calculator.update(
            PilotCurrentLdState(),
            input(
                sampleTimeMillis = 1_000L,
                tasValid = false,
                currentLD = 28f,
                currentLDValid = true
            )
        )

        assertTrue(state.snapshot.pilotCurrentLDValid)
        assertEquals(28f, state.snapshot.pilotCurrentLD)
        assertEquals(PilotCurrentLdSource.GROUND_FALLBACK, state.snapshot.pilotCurrentLDSource)
    }

    @Test
    fun polar_fill_supports_short_te_gaps_but_not_long_ones() {
        val calculator = PilotCurrentLdCalculator(FakeStillAirSinkProvider(sinkAtSpeedMs = 1.2))
        var state = PilotCurrentLdState()

        state = calculator.update(state, input(sampleTimeMillis = 0L, teVario = -1.0))
        state = calculator.update(state, input(sampleTimeMillis = 4_000L, teVario = -1.0))
        state = calculator.update(state, input(sampleTimeMillis = 8_000L, teVario = null, currentLDValid = false))
        assertTrue(state.snapshot.pilotCurrentLDValid)
        assertEquals(PilotCurrentLdSource.POLAR_FILL, state.snapshot.pilotCurrentLDSource)

        state = calculator.update(state, input(sampleTimeMillis = 12_001L, teVario = null, currentLDValid = false))
        assertTrue(state.snapshot.pilotCurrentLDValid)
        assertEquals(PilotCurrentLdSource.HELD, state.snapshot.pilotCurrentLDSource)
    }

    @Test
    fun setup_support_only_changes_polar_fill_path() {
        val measuredLowPolar = runMeasuredWindow(FakeStillAirSinkProvider(sinkAtSpeedMs = 0.8)).pilotCurrentLD
        val measuredHighPolar = runMeasuredWindow(FakeStillAirSinkProvider(sinkAtSpeedMs = 1.4)).pilotCurrentLD
        assertEquals(measuredLowPolar.toDouble(), measuredHighPolar.toDouble(), 1e-6)

        val polarFillLow = runPolarFillWindow(FakeStillAirSinkProvider(sinkAtSpeedMs = 0.8)).pilotCurrentLD
        val polarFillHigh = runPolarFillWindow(FakeStillAirSinkProvider(sinkAtSpeedMs = 1.4)).pilotCurrentLD
        assertNotEquals(polarFillLow, polarFillHigh)
    }

    private fun runStraightWindow(
        windState: WindState,
        waypointBearingDeg: Double
    ): PilotCurrentLdSnapshot {
        val calculator = PilotCurrentLdCalculator(FakeStillAirSinkProvider())
        var state = PilotCurrentLdState()
        state = calculator.update(state, input(sampleTimeMillis = 0L, windState = windState, waypointBearingDeg = waypointBearingDeg))
        state = calculator.update(state, input(sampleTimeMillis = 4_000L, windState = windState, waypointBearingDeg = waypointBearingDeg))
        state = calculator.update(state, input(sampleTimeMillis = 8_000L, windState = windState, waypointBearingDeg = waypointBearingDeg))
        return state.snapshot
    }

    private fun runMeasuredWindow(sinkProvider: StillAirSinkProvider): PilotCurrentLdSnapshot {
        val calculator = PilotCurrentLdCalculator(sinkProvider)
        var state = PilotCurrentLdState()
        state = calculator.update(state, input(sampleTimeMillis = 0L, teVario = -1.0))
        state = calculator.update(state, input(sampleTimeMillis = 4_000L, teVario = -1.0))
        state = calculator.update(state, input(sampleTimeMillis = 8_000L, teVario = -1.0))
        return state.snapshot
    }

    private fun runPolarFillWindow(sinkProvider: StillAirSinkProvider): PilotCurrentLdSnapshot {
        val calculator = PilotCurrentLdCalculator(sinkProvider)
        var state = PilotCurrentLdState()
        state = calculator.update(state, input(sampleTimeMillis = 0L, teVario = -1.0))
        state = calculator.update(state, input(sampleTimeMillis = 4_000L, teVario = -1.0))
        state = calculator.update(state, input(sampleTimeMillis = 8_000L, teVario = null, currentLDValid = false))
        return state.snapshot
    }

    private fun input(
        sampleTimeMillis: Long,
        trueAirspeedMs: Double = 30.0,
        indicatedAirspeedMs: Double = 28.0,
        teVario: Double? = -1.0,
        tasValid: Boolean = true,
        airspeedSource: String = "SENSOR",
        currentLD: Float = 0f,
        currentLDValid: Boolean = false,
        isCircling: Boolean = false,
        isTurning: Boolean = false,
        gpsBearingDeg: Double = 90.0,
        includeGps: Boolean = true,
        windState: WindState = WindState(),
        waypointBearingDeg: Double = Double.NaN
    ): PilotCurrentLdInput {
        val gps = if (includeGps) {
            GPSData(
                position = GeoPoint(latitude = 47.0, longitude = 13.0),
                altitude = AltitudeM(1_000.0),
                speed = SpeedMs(30.0),
                bearing = gpsBearingDeg,
                accuracy = 5f,
                timestamp = sampleTimeMillis,
                monotonicTimestampMillis = sampleTimeMillis
            )
        } else {
            null
        }
        return PilotCurrentLdInput(
            completeData = CompleteFlightData(
                gps = gps,
                baro = null,
                compass = null,
                baroAltitude = AltitudeM(1_000.0),
                qnh = PressureHpa(1013.25),
                isQNHCalibrated = true,
                verticalSpeed = VerticalSpeedMs(0.0),
                bruttoVario = VerticalSpeedMs(0.0),
                teVario = teVario?.let(::VerticalSpeedMs),
                pressureAltitude = AltitudeM(1_000.0),
                baroGpsDelta = null,
                baroConfidence = ConfidenceLevel.HIGH,
                qnhCalibrationAgeSeconds = 0L,
                agl = AltitudeM(200.0),
                thermalAverage = VerticalSpeedMs(0.0),
                currentLD = currentLD,
                currentLDValid = currentLDValid,
                currentLDAir = 13f,
                currentLDAirValid = true,
                netto = VerticalSpeedMs(0.0),
                trueAirspeed = SpeedMs(trueAirspeedMs),
                indicatedAirspeed = SpeedMs(indicatedAirspeedMs),
                airspeedSource = airspeedSource,
                tasValid = tasValid,
                isCircling = isCircling,
                isTurning = isTurning,
                timestamp = sampleTimeMillis,
                dataQuality = "TEST"
            ),
            windState = windState,
            flightState = FlyingState(isFlying = true, onGround = false),
            waypointNavigation = WaypointNavigationSnapshot(
                bearingTrueDegrees = waypointBearingDeg,
                valid = waypointBearingDeg.isFinite()
            )
        )
    }

    private fun windState(east: Double, north: Double): WindState = WindState(
        vector = WindVector(east = east, north = north),
        quality = 5,
        stale = false,
        confidence = 0.7
    )

    private class FakeStillAirSinkProvider(
        private val sinkAtSpeedMs: Double = 1.0,
        private val bestLd: Double = 40.0
    ) : StillAirSinkProvider {
        override fun sinkAtSpeed(indicatedAirspeedMs: Double): Double = sinkAtSpeedMs

        override fun iasBoundsMs(): SpeedBoundsMs = SpeedBoundsMs(minMs = 12.0, maxMs = 60.0)

        override fun bestLd(): Double = bestLd
    }
}
