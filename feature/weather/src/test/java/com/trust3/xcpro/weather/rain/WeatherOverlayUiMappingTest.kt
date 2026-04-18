package com.trust3.xcpro.weather.rain

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherOverlayUiMappingTest {

    @Test
    fun weatherRadarStatusLabel_returnsExpectedText() {
        assertEquals("OK", weatherRadarStatusLabel(WeatherRadarStatusCode.OK))
        assertEquals("No metadata", weatherRadarStatusLabel(WeatherRadarStatusCode.NO_METADATA))
        assertEquals("No frames", weatherRadarStatusLabel(WeatherRadarStatusCode.NO_FRAMES))
        assertEquals("Rate limited", weatherRadarStatusLabel(WeatherRadarStatusCode.RATE_LIMIT))
        assertEquals("Network error", weatherRadarStatusLabel(WeatherRadarStatusCode.NETWORK_ERROR))
        assertEquals("Parse error", weatherRadarStatusLabel(WeatherRadarStatusCode.PARSE_ERROR))
    }
}
