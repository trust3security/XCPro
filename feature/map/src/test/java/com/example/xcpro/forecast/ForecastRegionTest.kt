package com.example.xcpro.forecast

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastRegionTest {

    @Test
    fun forecastRegionZoneId_returnsConfiguredZone() {
        assertEquals(
            "America/Los_Angeles",
            forecastRegionZoneId("WEST_US").id
        )
    }

    @Test
    fun forecastRegionLocalDayBucket_usesRegionLocalDate() {
        val utcMs = Instant.parse("2026-02-16T23:30:00Z").toEpochMilli()

        val westUsExpected = Instant.ofEpochMilli(utcMs)
            .atZone(ZoneId.of("America/Los_Angeles"))
            .toLocalDate()
            .toEpochDay()
        val eastAusExpected = Instant.ofEpochMilli(utcMs)
            .atZone(ZoneId.of("Australia/Sydney"))
            .toLocalDate()
            .toEpochDay()

        assertEquals(
            westUsExpected,
            forecastRegionLocalDayBucket(utcMs = utcMs, regionCode = "WEST_US")
        )
        assertEquals(
            eastAusExpected,
            forecastRegionLocalDayBucket(utcMs = utcMs, regionCode = "EAST_AUS")
        )
    }
}
