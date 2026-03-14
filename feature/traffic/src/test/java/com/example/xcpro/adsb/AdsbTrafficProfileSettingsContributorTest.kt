package com.example.xcpro.adsb

import com.example.xcpro.core.common.profiles.ProfileSettingsSectionContract
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AdsbTrafficProfileSettingsContributorTest {

    @Test
    fun captureSection_serializesRepositoryState() = runTest {
        val repository = mock<AdsbTrafficPreferencesRepository>()
        whenever(repository.enabledFlow).thenReturn(flowOf(true))
        whenever(repository.iconSizePxFlow).thenReturn(flowOf(144))
        whenever(repository.maxDistanceKmFlow).thenReturn(flowOf(55))
        whenever(repository.verticalAboveMetersFlow).thenReturn(flowOf(1200.0))
        whenever(repository.verticalBelowMetersFlow).thenReturn(flowOf(900.0))
        whenever(repository.emergencyFlashEnabledFlow).thenReturn(flowOf(false))
        whenever(repository.emergencyAudioEnabledFlow).thenReturn(flowOf(true))
        whenever(repository.emergencyAudioCooldownMsFlow).thenReturn(flowOf(12_000L))
        whenever(repository.emergencyAudioMasterEnabledFlow).thenReturn(flowOf(false))
        whenever(repository.emergencyAudioShadowModeFlow).thenReturn(flowOf(true))
        whenever(repository.emergencyAudioRollbackLatchedFlow).thenReturn(flowOf(true))
        whenever(repository.emergencyAudioRollbackReasonFlow).thenReturn(flowOf("rollout"))
        val contributor = AdsbTrafficProfileSettingsContributor(repository)

        val payload = contributor.captureSection(
            sectionId = ProfileSettingsSectionContract.ADSB_TRAFFIC_PREFERENCES,
            profileIds = emptySet()
        )

        assertNotNull(payload)
        val json = payload!!.asJsonObject
        assertEquals(true, json.get("enabled").asBoolean)
        assertEquals(144, json.get("iconSizePx").asInt)
        assertEquals(55, json.get("maxDistanceKm").asInt)
        assertEquals(1200.0, json.get("verticalAboveMeters").asDouble, 0.0)
        assertEquals(900.0, json.get("verticalBelowMeters").asDouble, 0.0)
        assertEquals(true, json.get("emergencyAudioRollbackLatched").asBoolean)
        assertEquals("rollout", json.get("emergencyAudioRollbackReason").asString)
    }

    @Test
    fun applySection_updatesRepositoryState() = runTest {
        val repository = mock<AdsbTrafficPreferencesRepository>()
        val contributor = AdsbTrafficProfileSettingsContributor(repository)
        val payload = com.google.gson.JsonParser.parseString(
            """
            {
              "enabled": true,
              "iconSizePx": 144,
              "maxDistanceKm": 55,
              "verticalAboveMeters": 1200.0,
              "verticalBelowMeters": 900.0,
              "emergencyFlashEnabled": false,
              "emergencyAudioEnabled": true,
              "emergencyAudioCooldownMs": 12000,
              "emergencyAudioMasterEnabled": false,
              "emergencyAudioShadowMode": true,
              "emergencyAudioRollbackLatched": true,
              "emergencyAudioRollbackReason": "rollout"
            }
            """.trimIndent()
        )

        contributor.applySection(
            sectionId = ProfileSettingsSectionContract.ADSB_TRAFFIC_PREFERENCES,
            payload = payload,
            importedProfileIdMap = emptyMap()
        )

        verify(repository).setEnabled(true)
        verify(repository).setIconSizePx(144)
        verify(repository).setMaxDistanceKm(55)
        verify(repository).setVerticalAboveMeters(1200.0)
        verify(repository).setVerticalBelowMeters(900.0)
        verify(repository).setEmergencyFlashEnabled(false)
        verify(repository).setEmergencyAudioEnabled(true)
        verify(repository).setEmergencyAudioCooldownMs(12_000L)
        verify(repository).setEmergencyAudioMasterEnabled(false)
        verify(repository).setEmergencyAudioShadowMode(true)
        verify(repository).latchEmergencyAudioRollback("rollout")
    }

    @Test
    fun applySection_clearsRollbackWhenNotLatched() = runTest {
        val repository = mock<AdsbTrafficPreferencesRepository>()
        val contributor = AdsbTrafficProfileSettingsContributor(repository)
        val payload = com.google.gson.JsonParser.parseString(
            """
            {
              "enabled": false,
              "iconSizePx": 124,
              "maxDistanceKm": 10,
              "verticalAboveMeters": 1000.0,
              "verticalBelowMeters": 800.0,
              "emergencyFlashEnabled": true,
              "emergencyAudioEnabled": false,
              "emergencyAudioCooldownMs": 45000,
              "emergencyAudioMasterEnabled": true,
              "emergencyAudioShadowMode": false,
              "emergencyAudioRollbackLatched": false,
              "emergencyAudioRollbackReason": null
            }
            """.trimIndent()
        )

        contributor.applySection(
            sectionId = ProfileSettingsSectionContract.ADSB_TRAFFIC_PREFERENCES,
            payload = payload,
            importedProfileIdMap = emptyMap()
        )

        verify(repository).clearEmergencyAudioRollback()
    }
}
