package com.example.xcpro.sensors.domain

import com.example.xcpro.glider.SpeedBoundsMs
import com.example.xcpro.glider.StillAirSinkProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevoNettoCalculatorTest {

    private val sinkProvider = object : StillAirSinkProvider {
        override fun sinkAtSpeed(airspeedMs: Double): Double? = 1.0
        override fun iasBoundsMs(): SpeedBoundsMs? = null
    }

    @Test
    fun levoNetto_gated_while_circling_or_turning() {
        val calculator = LevoNettoCalculator(sinkProvider)
        val circling = calculator.update(
            LevoNettoInput(
                wMeasMs = 1.0,
                iasMs = 30.0,
                tasMs = 30.0,
                deltaTimeSeconds = 1.0,
                isFlying = true,
                isCircling = true,
                isTurning = false,
                hasWind = true,
                windConfidence = 1.0,
                hasPolar = true
            )
        )
        assertFalse(circling.valid)

        val turning = calculator.update(
            LevoNettoInput(
                wMeasMs = 1.0,
                iasMs = 30.0,
                tasMs = 30.0,
                deltaTimeSeconds = 1.0,
                isFlying = true,
                isCircling = false,
                isTurning = true,
                hasWind = true,
                windConfidence = 1.0,
                hasPolar = true
            )
        )
        assertFalse(turning.valid)
    }

    @Test
    fun replay_still_air_converges_near_zero() {
        val calculator = LevoNettoCalculator(sinkProvider)
        var result = LevoNettoResult(0.0, false, false, false, 0.0)
        repeat(10) {
            result = calculator.update(
                LevoNettoInput(
                    wMeasMs = 1.0,
                    iasMs = 30.0,
                    tasMs = 30.0,
                    deltaTimeSeconds = 1.0,
                    isFlying = true,
                    isCircling = false,
                    isTurning = false,
                    hasWind = true,
                    windConfidence = 1.0,
                    hasPolar = true
                )
            )
        }
        assertTrue(result.valid)
        assertTrue(kotlin.math.abs(result.valueMs) < 0.1)
    }

    @Test
    fun replay_sustained_sink_and_lift_have_expected_sign() {
        val sinkCalculator = LevoNettoCalculator(sinkProvider)
        val sinkResult = sinkCalculator.update(
            LevoNettoInput(
                wMeasMs = 0.0,
                iasMs = 30.0,
                tasMs = 30.0,
                deltaTimeSeconds = 1.0,
                isFlying = true,
                isCircling = false,
                isTurning = false,
                hasWind = true,
                windConfidence = 1.0,
                hasPolar = true
            )
        )
        assertTrue(sinkResult.valid)
        assertTrue(sinkResult.valueMs < 0.0)

        val liftCalculator = LevoNettoCalculator(sinkProvider)
        val liftResult = liftCalculator.update(
            LevoNettoInput(
                wMeasMs = 2.0,
                iasMs = 30.0,
                tasMs = 30.0,
                deltaTimeSeconds = 1.0,
                isFlying = true,
                isCircling = false,
                isTurning = false,
                hasWind = true,
                windConfidence = 1.0,
                hasPolar = true
            )
        )
        assertTrue(liftResult.valid)
        assertTrue(liftResult.valueMs > 0.0)
    }
}
