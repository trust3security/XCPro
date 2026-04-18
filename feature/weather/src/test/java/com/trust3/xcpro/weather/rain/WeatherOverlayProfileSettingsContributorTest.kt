package com.trust3.xcpro.weather.rain

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WeatherOverlayProfileSettingsContributorTest {

    @Test
    fun captureSection_serializesRepositoryState() = runTest {
        val repository = mock<WeatherOverlayPreferencesRepository>()
        whenever(repository.preferencesFlow).thenReturn(
            flowOf(
                WeatherOverlayPreferences(
                    enabled = true,
                    opacity = 0.62f,
                    animatePastWindow = true,
                    animationWindow = WeatherRainAnimationWindow.THIRTY_MINUTES,
                    animationSpeed = WeatherRainAnimationSpeed.FAST,
                    transitionQuality = WeatherRainTransitionQuality.BALANCED,
                    frameMode = WeatherRadarFrameMode.MANUAL,
                    manualFrameIndex = 3,
                    renderOptions = WeatherRadarRenderOptions(smooth = false, snow = true)
                )
            )
        )
        val contributor = WeatherOverlayProfileSettingsContributor(repository)

        val payload = contributor.captureSection(
            sectionId = com.trust3.xcpro.core.common.profiles.ProfileSettingsSectionContract.WEATHER_OVERLAY_PREFERENCES,
            profileIds = emptySet()
        )

        assertNotNull(payload)
        val json = payload!!.asJsonObject
        assertEquals(true, json.get("enabled").asBoolean)
        assertEquals(0.62f, json.get("opacity").asFloat)
        assertEquals("30m", json.get("animationWindow").asString)
        assertEquals("fast", json.get("animationSpeed").asString)
        assertEquals("balanced", json.get("transitionQuality").asString)
        assertEquals("manual", json.get("frameMode").asString)
        assertEquals(3, json.get("manualFrameIndex").asInt)
        assertEquals(false, json.get("smooth").asBoolean)
        assertEquals(true, json.get("snow").asBoolean)
    }

    @Test
    fun applySection_updatesRepositoryState() = runTest {
        val repository = mock<WeatherOverlayPreferencesRepository>()
        val contributor = WeatherOverlayProfileSettingsContributor(repository)
        val payload = com.google.gson.JsonParser.parseString(
            """
            {
              "enabled": true,
              "opacity": 0.62,
              "animatePastWindow": true,
              "animationWindow": "30m",
              "animationSpeed": "fast",
              "transitionQuality": "balanced",
              "frameMode": "manual",
              "manualFrameIndex": 3,
              "smooth": false,
              "snow": true
            }
            """.trimIndent()
        )

        contributor.applySection(
            sectionId = com.trust3.xcpro.core.common.profiles.ProfileSettingsSectionContract.WEATHER_OVERLAY_PREFERENCES,
            payload = payload,
            importedProfileIdMap = emptyMap()
        )

        verify(repository).setEnabled(true)
        verify(repository).setOpacity(0.62f)
        verify(repository).setAnimatePastWindow(true)
        verify(repository).setAnimationWindow(WeatherRainAnimationWindow.THIRTY_MINUTES)
        verify(repository).setAnimationSpeed(WeatherRainAnimationSpeed.FAST)
        verify(repository).setTransitionQuality(WeatherRainTransitionQuality.BALANCED)
        verify(repository).setFrameMode(WeatherRadarFrameMode.MANUAL)
        verify(repository).setManualFrameIndex(3)
        verify(repository).setSmoothEnabled(false)
        verify(repository).setSnowEnabled(true)
    }
}
