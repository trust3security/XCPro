package com.example.xcpro.weather.rain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRainAttributionTest {

    @Test
    fun tileAttribution_isExplicitLinkForm() {
        val attribution = weatherRainTileAttributionHtml()
        assertTrue(attribution.contains("<a href=\"https://"))
        assertTrue(attribution.contains("rainviewer.com"))
        assertTrue(attribution.contains(WEATHER_RAIN_ATTRIBUTION_TEXT))
    }

    @Test
    fun attributionLink_constantStable() {
        assertEquals("https://www.rainviewer.com", WEATHER_RAIN_ATTRIBUTION_LINK_URL)
    }
}
