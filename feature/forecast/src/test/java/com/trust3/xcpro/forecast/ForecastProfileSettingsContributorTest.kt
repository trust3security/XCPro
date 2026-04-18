package com.trust3.xcpro.forecast

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ForecastProfileSettingsContributorTest {

    @Test
    fun captureSection_serializesRepositoryState() = runTest {
        val repository = mock<ForecastPreferencesRepository>()
        whenever(repository.preferencesFlow).thenReturn(
            flowOf(
                ForecastPreferences(
                    overlayEnabled = true,
                    opacity = 0.71f,
                    windOverlayScale = 1.4f,
                    windOverlayEnabled = false,
                    windDisplayMode = ForecastWindDisplayMode.BARB,
                    skySightSatelliteOverlayEnabled = true,
                    skySightSatelliteImageryEnabled = false,
                    skySightSatelliteRadarEnabled = true,
                    skySightSatelliteLightningEnabled = false,
                    skySightSatelliteAnimateEnabled = true,
                    skySightSatelliteHistoryFrames = 6,
                    selectedPrimaryParameterId = ForecastParameterId("thermal_velocity"),
                    selectedWindParameterId = ForecastParameterId("wind_850"),
                    selectedTimeUtcMs = 123_456L,
                    selectedRegion = "au",
                    followTimeOffsetMinutes = 45,
                    autoTimeEnabled = false
                )
            )
        )
        val contributor = ForecastProfileSettingsContributor(repository)

        val payload = contributor.captureSection(
            sectionId = com.trust3.xcpro.core.common.profiles.ProfileSettingsSectionContract.FORECAST_PREFERENCES,
            profileIds = emptySet()
        )

        assertNotNull(payload)
        val json = payload!!.asJsonObject
        assertEquals(true, json.get("overlayEnabled").asBoolean)
        assertEquals(0.71f, json.get("opacity").asFloat)
        assertEquals(1.4f, json.get("windOverlayScale").asFloat)
        assertEquals("BARB", json.get("windDisplayMode").asString)
        assertEquals("thermal_velocity", json.get("selectedPrimaryParameterId").asString)
        assertEquals("wind_850", json.get("selectedWindParameterId").asString)
        assertEquals(123_456L, json.get("selectedTimeUtcMs").asLong)
        assertEquals("au", json.get("selectedRegion").asString)
        assertEquals(45, json.get("followTimeOffsetMinutes").asInt)
    }

    @Test
    fun applySection_updatesRepositoryState() = runTest {
        val repository = mock<ForecastPreferencesRepository>()
        val contributor = ForecastProfileSettingsContributor(repository)
        val payload = com.google.gson.JsonParser.parseString(
            """
            {
              "overlayEnabled": true,
              "opacity": 0.71,
              "windOverlayScale": 1.4,
              "windOverlayEnabled": false,
              "windDisplayMode": "BARB",
              "skySightSatelliteOverlayEnabled": true,
              "skySightSatelliteImageryEnabled": false,
              "skySightSatelliteRadarEnabled": true,
              "skySightSatelliteLightningEnabled": false,
              "skySightSatelliteAnimateEnabled": true,
              "skySightSatelliteHistoryFrames": 6,
              "selectedPrimaryParameterId": "thermal_velocity",
              "selectedWindParameterId": "wind_850",
              "selectedTimeUtcMs": 123456,
              "selectedRegion": "au",
              "followTimeOffsetMinutes": 45,
              "autoTimeEnabled": false
            }
            """.trimIndent()
        )

        contributor.applySection(
            sectionId = com.trust3.xcpro.core.common.profiles.ProfileSettingsSectionContract.FORECAST_PREFERENCES,
            payload = payload,
            importedProfileIdMap = emptyMap()
        )

        verify(repository).setOverlayEnabled(true)
        verify(repository).setOpacity(0.71f)
        verify(repository).setWindOverlayScale(1.4f)
        verify(repository).setWindOverlayEnabled(false)
        verify(repository).setWindDisplayMode(ForecastWindDisplayMode.BARB)
        verify(repository).setSkySightSatelliteOverlayEnabled(true)
        verify(repository).setSkySightSatelliteImageryEnabled(false)
        verify(repository).setSkySightSatelliteRadarEnabled(true)
        verify(repository).setSkySightSatelliteLightningEnabled(false)
        verify(repository).setSkySightSatelliteAnimateEnabled(true)
        verify(repository).setSkySightSatelliteHistoryFrames(6)
        verify(repository).setSelectedPrimaryParameterId(ForecastParameterId("thermal_velocity"))
        verify(repository).setSelectedWindParameterId(ForecastParameterId("wind_850"))
        verify(repository).setSelectedTimeUtcMs(123_456L)
        verify(repository).setSelectedRegion("au")
        verify(repository).setFollowTimeOffsetMinutes(45)
        verify(repository).setAutoTimeEnabled(false)
    }
}
