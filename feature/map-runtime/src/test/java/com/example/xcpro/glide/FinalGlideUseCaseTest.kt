package com.example.xcpro.glide

import com.example.dfcards.calculations.ConfidenceLevel
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.glider.SpeedBoundsMs
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.tasks.core.RacingAltitudeReference
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalGlideUseCaseTest {

    @Test
    fun solve_returns_finite_finish_glide_outputs_for_valid_route() {
        val useCase = FinalGlideUseCase(fakePolarSinkProvider())
        val solution = useCase.solve(
            completeData = completeFlightData(navAltitudeMeters = 1_250.0, macCready = 2.0),
            windState = null,
            target = validFinishTarget()
        )

        assertTrue(solution.valid)
        assertTrue(solution.requiredGlideRatio.isFinite())
        assertTrue(solution.requiredGlideRatio > 0.0)
        assertTrue(solution.requiredAltitudeMeters > 900.0)
        assertTrue(solution.arrivalHeightMc0Meters >= solution.arrivalHeightMeters)
        assertTrue(solution.distanceRemainingMeters > 0.0)
    }

    @Test
    fun solve_rejects_qnh_finish_constraint_when_qnh_is_not_calibrated() {
        val useCase = FinalGlideUseCase(fakePolarSinkProvider())
        val solution = useCase.solve(
            completeData = completeFlightData(
                navAltitudeMeters = 1_250.0,
                macCready = 1.5,
                isQnhCalibrated = false
            ),
            windState = null,
            target = validFinishTarget(altitudeReference = RacingAltitudeReference.QNH)
        )

        assertFalse(solution.valid)
        assertEquals(GlideInvalidReason.NO_ALTITUDE, solution.invalidReason)
    }

    @Test
    fun solve_requires_active_polar_bounds() {
        val useCase = FinalGlideUseCase(
            object : StillAirSinkProvider {
                override fun sinkAtSpeed(airspeedMs: Double): Double? = null
                override fun iasBoundsMs(): SpeedBoundsMs? = null
            }
        )

        val solution = useCase.solve(
            completeData = completeFlightData(navAltitudeMeters = 1_250.0, macCready = 1.0),
            windState = WindState(
                vector = WindVector(east = -5.0, north = 0.0),
                source = WindSource.MANUAL,
                quality = 5,
                confidence = 1.0
            ),
            target = validFinishTarget()
        )

        assertFalse(solution.valid)
        assertEquals(GlideInvalidReason.NO_POLAR, solution.invalidReason)
    }

    private fun validFinishTarget(
        altitudeReference: RacingAltitudeReference = RacingAltitudeReference.MSL
    ): GlideTargetSnapshot = GlideTargetSnapshot(
        kind = GlideTargetKind.TASK_FINISH,
        label = "Finish",
        remainingWaypoints = listOf(
            GlideRoutePoint(lat = 0.0, lon = 0.1, label = "Finish")
        ),
        finishConstraint = GlideFinishConstraint(
            requiredAltitudeMeters = 900.0,
            altitudeReference = altitudeReference
        ),
        valid = true
    )

    private fun completeFlightData(
        navAltitudeMeters: Double,
        macCready: Double,
        isQnhCalibrated: Boolean = true
    ): CompleteFlightData = CompleteFlightData(
        gps = GPSData(
            position = GeoPoint(latitude = 0.0, longitude = 0.0),
            altitude = AltitudeM(navAltitudeMeters),
            speed = SpeedMs(30.0),
            bearing = 90.0,
            accuracy = 5f,
            timestamp = 1_000L,
            monotonicTimestampMillis = 1_000L
        ),
        baro = null,
        compass = null,
        baroAltitude = AltitudeM(navAltitudeMeters),
        qnh = PressureHpa(1013.25),
        isQNHCalibrated = isQnhCalibrated,
        verticalSpeed = VerticalSpeedMs(0.0),
        bruttoVario = VerticalSpeedMs(0.0),
        pressureAltitude = AltitudeM(navAltitudeMeters),
        navAltitude = AltitudeM(navAltitudeMeters),
        baroGpsDelta = null,
        baroConfidence = ConfidenceLevel.HIGH,
        qnhCalibrationAgeSeconds = 0L,
        agl = AltitudeM(200.0),
        thermalAverage = VerticalSpeedMs(0.0),
        currentLD = 30f,
        netto = VerticalSpeedMs(0.0),
        timestamp = 1_000L,
        dataQuality = "TEST",
        macCready = macCready
    )

    private fun fakePolarSinkProvider(): StillAirSinkProvider = object : StillAirSinkProvider {
        override fun sinkAtSpeed(airspeedMs: Double): Double {
            val centered = airspeedMs - 17.0
            return 0.55 + (centered * centered * 0.01)
        }

        override fun iasBoundsMs(): SpeedBoundsMs = SpeedBoundsMs(minMs = 12.0, maxMs = 25.0)
    }
}
