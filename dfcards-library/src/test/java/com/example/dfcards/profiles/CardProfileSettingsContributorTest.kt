package com.example.dfcards.profiles

import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightTemplate
import com.example.dfcards.dfcards.CardState
import com.trust3.xcpro.core.common.profiles.ProfileSettingsSectionContract
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CardProfileSettingsContributorTest {

    @Test
    fun captureSection_serializesOwnerStateAndNormalizesDefaultAlias() = runTest {
        val preferences = mock<CardPreferences>()
        whenever(preferences.getAllTemplates()).thenReturn(
            flowOf(
                listOf(
                    FlightTemplate(
                        id = "id01",
                        name = "Cruise",
                        description = "Cruise cards",
                        cardIds = listOf("agl", "gps_alt"),
                        isPreset = true,
                        createdAt = 101L
                    )
                )
            )
        )
        whenever(preferences.getAllProfileTemplateCards()).thenReturn(
            flowOf(
                mapOf(
                    "default" to mapOf("id01" to listOf("agl")),
                    "default-profile" to mapOf("id01" to listOf("should_not_win")),
                    "pilot-1" to mapOf("id01" to listOf("gps_alt"))
                )
            )
        )
        whenever(preferences.getAllProfileFlightModeTemplates()).thenReturn(
            flowOf(
                mapOf(
                    "default" to mapOf("CRUISE" to "id01"),
                    "pilot-1" to mapOf("THERMAL" to "id02")
                )
            )
        )
        whenever(preferences.getProfileAllFlightModeVisibilities(any())).thenReturn(
            flowOf(mapOf("CRUISE" to true, "THERMAL" to false, "FINAL_GLIDE" to true))
        )
        whenever(preferences.getProfileCardPositions(any(), any())).thenReturn(
            flowOf(
                mapOf(
                    "agl" to CardPreferences.CardPosition(
                        x = 1f,
                        y = 2f,
                        width = 100f,
                        height = 50f
                    )
                )
            )
        )
        whenever(preferences.getCardsAcrossPortrait()).thenReturn(flowOf(4))
        whenever(preferences.getCardsAnchorPortrait()).thenReturn(
            flowOf(CardPreferences.CardAnchor.BOTTOM)
        )
        whenever(preferences.getLastActiveTemplate()).thenReturn(flowOf("id01"))
        whenever(preferences.getVarioSmoothingAlpha()).thenReturn(flowOf(0.42f))

        val contributor = CardProfileSettingsContributor(preferences)

        val payload = contributor.captureSection(
            sectionId = ProfileSettingsSectionContract.CARD_PREFERENCES,
            profileIds = linkedSetOf("default-profile", "pilot-1")
        )

        assertNotNull(payload)
        val json = payload!!.asJsonObject
        assertEquals(1, json.getAsJsonArray("templates").size())
        assertEquals(
            "Cruise",
            json.getAsJsonArray("templates")[0].asJsonObject.get("name").asString
        )
        assertEquals(
            "agl",
            json.getAsJsonObject("profileTemplateCards")
                .getAsJsonObject("default-profile")
                .getAsJsonArray("id01")[0]
                .asString
        )
        assertEquals(
            "id02",
            json.getAsJsonObject("profileFlightModeTemplates")
                .getAsJsonObject("pilot-1")
                .get("THERMAL")
                .asString
        )
        assertEquals(
            false,
            json.getAsJsonObject("profileFlightModeVisibilities")
                .getAsJsonObject("default-profile")
                .get("THERMAL")
                .asBoolean
        )
        assertEquals(
            1f,
            json.getAsJsonObject("profileCardPositions")
                .getAsJsonObject("default-profile")
                .getAsJsonObject("CRUISE")
                .getAsJsonObject("agl")
                .get("x")
                .asFloat
        )
        assertEquals(4, json.get("cardsAcrossPortrait").asInt)
        assertEquals("BOTTOM", json.get("cardsAnchorPortrait").asString)
        assertEquals("id01", json.get("lastActiveTemplate").asString)
        assertEquals(0.42f, json.get("varioSmoothingAlpha").asFloat)
    }

    @Test
    fun applySection_restoresCardStateAndMapsImportedProfiles() = runTest {
        val preferences = mock<CardPreferences>()
        val contributor = CardProfileSettingsContributor(preferences)
        val payload = com.google.gson.JsonParser.parseString(
            """
            {
              "templates": [
                {
                  "id": "id01",
                  "name": "Cruise",
                  "description": "Cruise cards",
                  "cardIds": ["agl", "gps_alt"],
                  "isPreset": true,
                  "createdAt": 101
                }
              ],
              "profileTemplateCards": {
                "default": { "id01": ["agl"] },
                "source-pilot": { "id01": ["gps_alt"] }
              },
              "profileFlightModeTemplates": {
                "default": { "CRUISE": "id01" },
                "source-pilot": { "THERMAL": "id02" }
              },
              "profileFlightModeVisibilities": {
                "source-pilot": { "THERMAL": false }
              },
              "profileCardPositions": {
                "source-pilot": {
                  "THERMAL": {
                    "agl": {
                      "x": 10.0,
                      "y": 20.0,
                      "width": 100.0,
                      "height": 50.0
                    }
                  }
                }
              },
              "cardsAcrossPortrait": 5,
              "cardsAnchorPortrait": "BOTTOM",
              "lastActiveTemplate": "id01",
              "varioSmoothingAlpha": 0.33
            }
            """.trimIndent()
        )

        contributor.applySection(
            sectionId = ProfileSettingsSectionContract.CARD_PREFERENCES,
            payload = payload,
            importedProfileIdMap = mapOf(
                "default-profile" to "target-default",
                "source-pilot" to "target-pilot"
            )
        )

        verify(preferences).saveAllTemplates(
            argThat { size == 1 && first().id == "id01" && first().createdAt == 101L }
        )
        verify(preferences).setCardsAcrossPortrait(5)
        verify(preferences).setCardsAnchorPortrait(CardPreferences.CardAnchor.BOTTOM)
        verify(preferences).saveLastActiveTemplate("id01")
        verify(preferences).saveVarioSmoothingAlpha(0.33f)
        verify(preferences).saveProfileTemplateCards("target-default", "id01", listOf("agl"))
        verify(preferences).saveProfileTemplateCards("target-pilot", "id01", listOf("gps_alt"))
        verify(preferences).saveProfileFlightModeTemplate("target-default", "CRUISE", "id01")
        verify(preferences).saveProfileFlightModeTemplate("target-pilot", "THERMAL", "id02")
        verify(preferences).saveProfileFlightModeVisibility("target-pilot", "THERMAL", false)
        verify(preferences).saveProfileCardPositions(
            eq("target-pilot"),
            eq("THERMAL"),
            argThat<List<CardState>> {
                size == 1 &&
                    first().id == "agl" &&
                    first().x == 10f &&
                    first().y == 20f &&
                    first().width == 100f &&
                    first().height == 50f
            }
        )
    }
}

