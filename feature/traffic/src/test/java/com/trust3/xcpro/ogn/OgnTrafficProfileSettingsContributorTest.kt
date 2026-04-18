package com.trust3.xcpro.ogn

import com.trust3.xcpro.core.common.profiles.ProfileSettingsSectionContract
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OgnTrafficProfileSettingsContributorTest {

    @Test
    fun captureSection_serializesRepositoryState() = runTest {
        val repository = mock<OgnTrafficPreferencesRepository>()
        whenever(repository.enabledFlow).thenReturn(flowOf(true))
        whenever(repository.iconSizePxFlow).thenReturn(flowOf(140))
        whenever(repository.receiveRadiusKmFlow).thenReturn(flowOf(180))
        whenever(repository.autoReceiveRadiusEnabledFlow).thenReturn(flowOf(true))
        whenever(repository.displayUpdateModeFlow).thenReturn(flowOf(OgnDisplayUpdateMode.BALANCED))
        whenever(repository.showSciaEnabledFlow).thenReturn(flowOf(true))
        whenever(repository.showThermalsEnabledFlow).thenReturn(flowOf(false))
        whenever(repository.thermalRetentionHoursFlow).thenReturn(flowOf(36))
        whenever(repository.hotspotsDisplayPercentFlow).thenReturn(flowOf(90))
        whenever(repository.targetEnabledFlow).thenReturn(flowOf(true))
        whenever(repository.targetAircraftKeyFlow).thenReturn(flowOf("ABC123"))
        whenever(repository.ownFlarmHexFlow).thenReturn(flowOf("123ABC"))
        whenever(repository.ownIcaoHexFlow).thenReturn(flowOf("ABC123"))
        whenever(repository.clientCallsignFlow).thenReturn(flowOf("TEST123"))
        val contributor = OgnTrafficProfileSettingsContributor(repository)

        val payload = contributor.captureSection(
            sectionId = ProfileSettingsSectionContract.OGN_TRAFFIC_PREFERENCES,
            profileIds = emptySet()
        )

        assertNotNull(payload)
        val json = payload!!.asJsonObject
        assertEquals(true, json.get("enabled").asBoolean)
        assertEquals(140, json.get("iconSizePx").asInt)
        assertEquals(180, json.get("receiveRadiusKm").asInt)
        assertEquals("balanced", json.get("displayUpdateMode").asString)
        assertEquals("TEST123", json.get("clientCallsign").asString)
    }

    @Test
    fun applySection_updatesRepositoryState() = runTest {
        val repository = mock<OgnTrafficPreferencesRepository>()
        val contributor = OgnTrafficProfileSettingsContributor(repository)
        val payload = com.google.gson.JsonParser.parseString(
            """
            {
              "enabled": true,
              "iconSizePx": 140,
              "receiveRadiusKm": 180,
              "autoReceiveRadiusEnabled": true,
              "displayUpdateMode": "balanced",
              "showSciaEnabled": true,
              "showThermalsEnabled": false,
              "thermalRetentionHours": 36,
              "hotspotsDisplayPercent": 90,
              "targetEnabled": true,
              "targetAircraftKey": "ABC123",
              "ownFlarmHex": "123ABC",
              "ownIcaoHex": "ABC123",
              "clientCallsign": "TEST123"
            }
            """.trimIndent()
        )

        contributor.applySection(
            sectionId = ProfileSettingsSectionContract.OGN_TRAFFIC_PREFERENCES,
            payload = payload,
            importedProfileIdMap = emptyMap()
        )

        verify(repository).setEnabled(true)
        verify(repository).setIconSizePx(140)
        verify(repository).setReceiveRadiusKm(180)
        verify(repository).setAutoReceiveRadiusEnabled(true)
        verify(repository).setDisplayUpdateMode(OgnDisplayUpdateMode.BALANCED)
        verify(repository).setShowSciaEnabled(true)
        verify(repository).setShowThermalsEnabled(false)
        verify(repository).setThermalRetentionHours(36)
        verify(repository).setHotspotsDisplayPercent(90)
        verify(repository).setTargetSelection(enabled = true, aircraftKey = "ABC123")
        verify(repository).setOwnFlarmHex("123ABC")
        verify(repository).setOwnIcaoHex("ABC123")
        verify(repository).setClientCallsign("TEST123")
    }
}
