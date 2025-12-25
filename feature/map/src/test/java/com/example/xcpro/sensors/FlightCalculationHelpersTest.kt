package com.example.xcpro.sensors

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.dfcards.dfcards.calculations.SimpleAglCalculator
import com.example.xcpro.glider.StillAirSinkProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class FlightCalculationHelpersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun resetAll_clearsThermalTracking() {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = null
        }

        val helpers = FlightCalculationHelpers(
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            aglCalculator = SimpleAglCalculator(context),
            locationHistory = mutableListOf(),
            sinkProvider = sinkProvider
        )

        helpers.updateThermalState(
            timestampMillis = 1_000L,
            teAltitudeMeters = 100.0,
            verticalSpeedMs = 0.0,
            isCircling = true
        )
        helpers.updateThermalState(
            timestampMillis = 2_000L,
            teAltitudeMeters = 110.0,
            verticalSpeedMs = 10.0,
            isCircling = true
        )

        assertTrue(helpers.currentThermalValid)
        assertEquals(10.0f, helpers.thermalAverageCurrent, 1e-6f)

        helpers.resetAll()

        assertFalse(helpers.currentThermalValid)
        assertEquals(0.0f, helpers.thermalAverageCurrent, 1e-6f)
    }
}

