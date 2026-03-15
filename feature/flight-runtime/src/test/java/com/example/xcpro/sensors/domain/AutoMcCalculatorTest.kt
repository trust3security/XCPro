package com.example.xcpro.sensors.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMcCalculatorTest {

    @Test
    fun updates_only_on_thermal_exit() {
        val calculator = AutoMcCalculator()

        val initial = calculator.update(
            AutoMcInput(
                currentTimeMillis = 0L,
                isCircling = true,
                currentThermalLiftRate = 2.0,
                currentThermalValid = true
            )
        )
        assertFalse(initial.valid)

        val exitUpdate = calculator.update(
            AutoMcInput(
                currentTimeMillis = 1_000L,
                isCircling = false,
                currentThermalLiftRate = 2.0,
                currentThermalValid = true
            )
        )
        assertTrue(exitUpdate.valid)
        assertEquals(2.0, exitUpdate.valueMs, 1e-6)

        val cruise = calculator.update(
            AutoMcInput(
                currentTimeMillis = 2_000L,
                isCircling = false,
                currentThermalLiftRate = 3.0,
                currentThermalValid = true
            )
        )
        assertEquals(exitUpdate.valueMs, cruise.valueMs, 1e-6)

        calculator.update(
            AutoMcInput(
                currentTimeMillis = 3_000L,
                isCircling = true,
                currentThermalLiftRate = 3.0,
                currentThermalValid = true
            )
        )

        val nextExit = calculator.update(
            AutoMcInput(
                currentTimeMillis = 603_000L,
                isCircling = false,
                currentThermalLiftRate = 3.0,
                currentThermalValid = true
            )
        )
        assertTrue(nextExit.valid)
        assertEquals(2.5, nextExit.valueMs, 0.05)
    }
}
