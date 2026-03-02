package com.example.xcpro.screens.navdrawer

import com.example.xcpro.weather.rain.WeatherRadarFrameMode
import com.example.xcpro.weather.rain.WeatherRadarStatusCode
import com.example.xcpro.weather.rain.WeatherOverlayRuntimeState
import com.example.xcpro.weather.rain.WEATHER_RAIN_ATTRIBUTION_LINK_URL
import com.example.xcpro.weather.rain.WeatherRainAnimationWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherSettingsScreenPolicyTest {

    @Test
    fun isFrameSourceControlEnabled_requiresOverlayEnabledAndCycleDisabled() {
        assertTrue(
            isFrameSourceControlEnabled(
                rainOverlayEnabled = true,
                animatePastWindow = false
            )
        )
        assertFalse(
            isFrameSourceControlEnabled(
                rainOverlayEnabled = false,
                animatePastWindow = false
            )
        )
        assertFalse(
            isFrameSourceControlEnabled(
                rainOverlayEnabled = true,
                animatePastWindow = true
            )
        )
    }

    @Test
    fun shouldShowManualFrameControls_requiresManualModeAndFrames() {
        assertTrue(
            shouldShowManualFrameControls(
                rainOverlayEnabled = true,
                animatePastWindow = false,
                frameMode = WeatherRadarFrameMode.MANUAL,
                availableFrameCount = 4
            )
        )
        assertFalse(
            shouldShowManualFrameControls(
                rainOverlayEnabled = true,
                animatePastWindow = false,
                frameMode = WeatherRadarFrameMode.LATEST,
                availableFrameCount = 4
            )
        )
        assertFalse(
            shouldShowManualFrameControls(
                rainOverlayEnabled = true,
                animatePastWindow = true,
                frameMode = WeatherRadarFrameMode.MANUAL,
                availableFrameCount = 4
            )
        )
        assertFalse(
            shouldShowManualFrameControls(
                rainOverlayEnabled = true,
                animatePastWindow = false,
                frameMode = WeatherRadarFrameMode.MANUAL,
                availableFrameCount = 0
            )
        )
    }

    @Test
    fun weatherAttributionLink_isHttpsRainViewerUrl() {
        assertTrue(WEATHER_RAIN_ATTRIBUTION_LINK_URL.startsWith("https://"))
        assertTrue(WEATHER_RAIN_ATTRIBUTION_LINK_URL.contains("rainviewer.com"))
    }

    @Test
    fun weatherMetadataStatusLine_usesSharedStatusMapping() {
        val text = weatherMetadataStatusLine(
            WeatherOverlayRuntimeState(
                metadataStatus = WeatherRadarStatusCode.RATE_LIMIT
            )
        )

        assertEquals("Metadata: Rate limited", text)
    }

    @Test
    fun weatherFreshnessLine_reflectsLiveAndStaleStates() {
        val liveText = weatherFreshnessLine(
            WeatherOverlayRuntimeState(
                metadataFreshnessAgeMs = 20_000L,
                metadataStale = false
            )
        )
        val staleText = weatherFreshnessLine(
            WeatherOverlayRuntimeState(
                metadataFreshnessAgeMs = 90_000L,
                metadataStale = true
            )
        )

        assertEquals("Last update age: 20s (Live)", liveText)
        assertEquals("Last update age: 1m 30s (Stale)", staleText)
    }

    @Test
    fun weatherAgeLines_fallbackToNaWhenMissing() {
        val state = WeatherOverlayRuntimeState(
            metadataContentAgeMs = null,
            selectedFrameAgeMs = null
        )

        assertEquals("Content age: n/a", weatherContentAgeLine(state))
        assertEquals("Visible frame age: n/a", weatherVisibleFrameAgeLine(state))
    }

    @Test
    fun weatherAnimationWindowSummaryLabel_formatsWindowAndMaxFrames() {
        assertEquals(
            "30 min window - up to 4 frames",
            weatherAnimationWindowSummaryLabel(WeatherRainAnimationWindow.THIRTY_MINUTES)
        )
        assertEquals(
            "120 min window - up to 13 frames",
            weatherAnimationWindowSummaryLabel(WeatherRainAnimationWindow.ONE_HUNDRED_TWENTY_MINUTES)
        )
    }
}
