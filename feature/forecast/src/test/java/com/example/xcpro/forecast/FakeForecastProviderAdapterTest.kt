package com.example.xcpro.forecast

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeForecastProviderAdapterTest {

    private val adapter = FakeForecastProviderAdapter()

    @Test
    fun timeSlots_areHourlyForTwentyFiveSteps() {
        val nowUtcMs = 1_700_000_123_456L

        val slots = adapter.getTimeSlots(
            nowUtcMs = nowUtcMs,
            regionCode = DEFAULT_FORECAST_REGION_CODE
        )

        assertEquals(25, slots.size)
        val first = slots.first().validTimeUtcMs
        val second = slots[1].validTimeUtcMs
        assertEquals(3_600_000L, second - first)
        assertEquals(0L, first % 3_600_000L)
    }

    @Test
    fun pointValue_isDeterministicForSameInput() = runTest {
        val slot = ForecastTimeSlot(validTimeUtcMs = 1_700_000_400_000L)
        val parameterId = ForecastParameterId("THERMAL")

        val first = adapter.getValue(
            latitude = -34.1234,
            longitude = 149.5678,
            parameterId = parameterId,
            timeSlot = slot,
            regionCode = DEFAULT_FORECAST_REGION_CODE
        )
        val second = adapter.getValue(
            latitude = -34.1234,
            longitude = 149.5678,
            parameterId = parameterId,
            timeSlot = slot,
            regionCode = DEFAULT_FORECAST_REGION_CODE
        )

        assertEquals(first, second)
        assertTrue(first.value in 0.0..6.0)
    }
}
