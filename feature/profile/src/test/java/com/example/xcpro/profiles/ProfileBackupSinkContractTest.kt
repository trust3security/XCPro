package com.example.xcpro.profiles

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileBackupSinkContractTest {

    @Test
    fun buildManagedBackupPayload_includesBundleLatestAndAdvertisesItInIndex() {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val namespacePrefix = "com.example.openxcpro.debug"
        val indexFileName = "${namespacePrefix}_profiles_index.json"
        val settingsFileName = "${namespacePrefix}_profile_settings.json"
        val bundleFileName = "${namespacePrefix}_bundle_latest.json"
        val profile = UserProfile(
            id = "pilot-1",
            name = "Pilot One",
            aircraftType = AircraftType.SAILPLANE
        )
        val settingsSnapshot = ProfileSettingsSnapshot(
            sections = mapOf(
                ProfileSettingsSectionIds.CARD_PREFERENCES to JsonPrimitive("captured")
            )
        )

        val payload = buildManagedBackupPayload(
            gson = gson,
            generatedAtWallMs = 1234L,
            sequenceNumber = 7L,
            indexFileName = indexFileName,
            settingsFileName = settingsFileName,
            bundleFileName = bundleFileName,
            profiles = listOf(profile),
            activeProfileId = profile.id,
            settingsSnapshot = settingsSnapshot,
            profileFileNameForId = { id -> "${namespacePrefix}_profile_${id}.json" }
        )

        val expectedManaged = payload.expectedManagedFiles()
        assertTrue(expectedManaged.contains(indexFileName))
        assertTrue(expectedManaged.contains(settingsFileName))
        assertTrue(expectedManaged.contains(bundleFileName))
        assertTrue(expectedManaged.contains("${namespacePrefix}_profile_pilot-1.json"))

        val indexRoot = JsonParser.parseString(payload.indexJson).asJsonObject
        assertEquals(bundleFileName, indexRoot["bundleFileName"].asString)

        val parsedBundle = ProfileBundleCodec.parse(payload.bundleJson).getOrThrow()
        assertEquals(ProfileBundleSourceFormat.BUNDLE_V2, parsedBundle.sourceFormat)
        assertEquals(profile.id, parsedBundle.activeProfileId)
        assertEquals(1, parsedBundle.profiles.size)
        assertTrue(
            parsedBundle.settingsSnapshot.sections.containsKey(
                ProfileSettingsSectionIds.CARD_PREFERENCES
            )
        )
    }
}
