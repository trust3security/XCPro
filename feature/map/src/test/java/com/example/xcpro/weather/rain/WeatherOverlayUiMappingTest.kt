package com.example.xcpro.weather.rain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherOverlayUiMappingTest {

    @Test
    fun resolveWeatherMapConfidenceState_hiddenWhenOverlayDisabled() {
        val state = resolveWeatherMapConfidenceState(
            WeatherOverlayRuntimeState(enabled = false)
        )

        assertFalse(state.visible)
    }

    @Test
    fun resolveWeatherMapConfidenceState_liveWhenFreshFrameAndNotStale() {
        val state = resolveWeatherMapConfidenceState(
            WeatherOverlayRuntimeState(
                enabled = true,
                selectedFrame = sampleFrame(),
                metadataStatus = WeatherRadarStatusCode.OK,
                metadataStale = false
            )
        )

        assertTrue(state.visible)
        assertEquals(WeatherMapConfidenceLevel.LIVE, state.level)
        assertEquals("Rain Live", state.label)
    }

    @Test
    fun resolveWeatherMapConfidenceState_staleWhenFrameExistsAndMarkedStale() {
        val state = resolveWeatherMapConfidenceState(
            WeatherOverlayRuntimeState(
                enabled = true,
                selectedFrame = sampleFrame(),
                metadataStatus = WeatherRadarStatusCode.OK,
                metadataStale = true
            )
        )

        assertTrue(state.visible)
        assertEquals(WeatherMapConfidenceLevel.STALE, state.level)
        assertEquals("Rain Stale", state.label)
    }

    @Test
    fun resolveWeatherMapConfidenceState_errorWhenNoFrameAvailable() {
        val state = resolveWeatherMapConfidenceState(
            WeatherOverlayRuntimeState(
                enabled = true,
                selectedFrame = null,
                metadataStatus = WeatherRadarStatusCode.NO_METADATA,
                metadataStale = true
            )
        )

        assertTrue(state.visible)
        assertEquals(WeatherMapConfidenceLevel.ERROR, state.level)
        assertEquals("Rain Error", state.label)
    }

    @Test
    fun resolveWeatherMapConfidenceState_errorOnHardStatusEvenWhenFrameExists() {
        val state = resolveWeatherMapConfidenceState(
            WeatherOverlayRuntimeState(
                enabled = true,
                selectedFrame = sampleFrame(),
                metadataStatus = WeatherRadarStatusCode.PARSE_ERROR,
                metadataStale = false
            )
        )

        assertTrue(state.visible)
        assertEquals(WeatherMapConfidenceLevel.ERROR, state.level)
        assertEquals("Rain Error", state.label)
    }

    @Test
    fun resolveWeatherMapConfidenceState_errorOnNoFramesEvenWhenFrameExists() {
        val state = resolveWeatherMapConfidenceState(
            WeatherOverlayRuntimeState(
                enabled = true,
                selectedFrame = sampleFrame(),
                metadataStatus = WeatherRadarStatusCode.NO_FRAMES,
                metadataStale = false
            )
        )

        assertTrue(state.visible)
        assertEquals(WeatherMapConfidenceLevel.ERROR, state.level)
        assertEquals("Rain Error", state.label)
    }

    @Test
    fun resolveWeatherMapConfidenceState_liveForFreshRateLimitedMetadata() {
        val state = resolveWeatherMapConfidenceState(
            WeatherOverlayRuntimeState(
                enabled = true,
                selectedFrame = sampleFrame(),
                metadataStatus = WeatherRadarStatusCode.RATE_LIMIT,
                metadataStale = false
            )
        )

        assertTrue(state.visible)
        assertEquals(WeatherMapConfidenceLevel.LIVE, state.level)
        assertEquals("Rain Live", state.label)
    }

    @Test
    fun weatherRadarStatusLabel_returnsExpectedText() {
        assertEquals("OK", weatherRadarStatusLabel(WeatherRadarStatusCode.OK))
        assertEquals("No metadata", weatherRadarStatusLabel(WeatherRadarStatusCode.NO_METADATA))
        assertEquals("No frames", weatherRadarStatusLabel(WeatherRadarStatusCode.NO_FRAMES))
        assertEquals("Rate limited", weatherRadarStatusLabel(WeatherRadarStatusCode.RATE_LIMIT))
        assertEquals("Network error", weatherRadarStatusLabel(WeatherRadarStatusCode.NETWORK_ERROR))
        assertEquals("Parse error", weatherRadarStatusLabel(WeatherRadarStatusCode.PARSE_ERROR))
    }

    private fun sampleFrame(): WeatherRainFrameSelection =
        WeatherRainFrameSelection(
            hostUrl = "https://tile.example.test",
            framePath = "/v2/radar/123/256/{z}/{x}/{y}/2/1_1.png",
            frameTimeEpochSec = 1_700_000_000L,
            renderOptions = WeatherRadarRenderOptions()
        )
}
