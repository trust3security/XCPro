package com.trust3.xcpro.sensors.domain

import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.common.units.UnitsConverter
import com.trust3.xcpro.glider.SpeedBoundsMs
import com.trust3.xcpro.glider.StillAirSinkProvider
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedToFlyCalculatorTest {

    private val sinkProvider = object : StillAirSinkProvider {
        override fun sinkAtSpeed(airspeedMs: Double): Double? = 0.5 + 0.02 * airspeedMs
        override fun iasBoundsMs(): SpeedBoundsMs? = null
    }

    @Test
    fun respects_bounds_and_rate_limit() {
        val calculator = SpeedToFlyCalculator(sinkProvider)
        val bounds = SpeedBoundsMs(minMs = 20.0, maxMs = 50.0)

        val first = calculator.update(
            SpeedToFlyInput(
                currentTimeMillis = 0L,
                currentIasMs = 30.0,
                mcBaseMs = 0.0,
                mcSourceAuto = false,
                glideNettoMs = 0.0,
                glideNettoValid = false,
                windConfidence = 1.0,
                flightMode = FlightMode.CRUISE,
                iasBounds = bounds
            )
        )
        assertTrue(first.valid)
        assertTrue(first.targetIasMs in bounds.minMs..bounds.maxMs)

        val second = calculator.update(
            SpeedToFlyInput(
                currentTimeMillis = 1_000L,
                currentIasMs = 30.0,
                mcBaseMs = 4.0,
                mcSourceAuto = false,
                glideNettoMs = 0.0,
                glideNettoValid = false,
                windConfidence = 1.0,
                flightMode = FlightMode.CRUISE,
                iasBounds = bounds
            )
        )
        val maxStep = UnitsConverter.knotsToMs(2.0) + 1e-6
        assertTrue(kotlin.math.abs(second.targetIasMs - first.targetIasMs) <= maxStep + 1e-3)
    }

    @Test
    fun glide_netto_authority_drops_with_low_wind_confidence() {
        val bounds = SpeedBoundsMs(minMs = 20.0, maxMs = 50.0)

        val highConfidence = SpeedToFlyCalculator(sinkProvider).update(
            SpeedToFlyInput(
                currentTimeMillis = 0L,
                currentIasMs = 30.0,
                mcBaseMs = 2.0,
                mcSourceAuto = false,
                glideNettoMs = 1.0,
                glideNettoValid = true,
                windConfidence = 0.8,
                flightMode = FlightMode.CRUISE,
                iasBounds = bounds
            )
        )

        val lowConfidence = SpeedToFlyCalculator(sinkProvider).update(
            SpeedToFlyInput(
                currentTimeMillis = 0L,
                currentIasMs = 30.0,
                mcBaseMs = 2.0,
                mcSourceAuto = false,
                glideNettoMs = 1.0,
                glideNettoValid = true,
                windConfidence = 0.0,
                flightMode = FlightMode.CRUISE,
                iasBounds = bounds
            )
        )

        assertTrue(lowConfidence.targetIasMs >= highConfidence.targetIasMs - 1e-3)
    }
}
