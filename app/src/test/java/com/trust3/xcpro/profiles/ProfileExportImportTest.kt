package com.trust3.xcpro.profiles

import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileExportImportTest {

    @Test
    fun parseBundleV2_roundTripsProfilesActiveAndSettings() {
        val pilot = UserProfile(
            id = "pilot-1",
            name = "Pilot 1",
            aircraftType = AircraftType.SAILPLANE,
            createdAt = 1_000L,
            lastUsed = 2_000L
        )
        val defaultProfile = UserProfile(
            id = ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
            name = "Default",
            aircraftType = AircraftType.PARAGLIDER,
            createdAt = 1_000L,
            lastUsed = 2_000L
        )
        val json = ProfileBundleCodec.serialize(
            ProfileBundleDocument(
                exportedAtWallMs = 123_456L,
                activeProfileId = pilot.id,
                profiles = listOf(defaultProfile, pilot),
                settings = ProfileSettingsSnapshot(
                    sections = mapOf(
                        ProfileSettingsSectionIds.CARD_PREFERENCES to JsonPrimitive("ok")
                    )
                )
            )
        )

        val parsed = ProfileBundleCodec.parse(json).getOrThrow()

        assertEquals(ProfileBundleSourceFormat.BUNDLE_V2, parsed.sourceFormat)
        assertEquals(pilot.id, parsed.activeProfileId)
        assertEquals(2, parsed.profiles.size)
        assertEquals("2.0", parsed.schemaVersion)
        assertEquals(123_456L, parsed.exportedAtWallMs)
        assertTrue(
            parsed.settingsSnapshot.sections.containsKey(ProfileSettingsSectionIds.CARD_PREFERENCES)
        )
    }

    @Test
    fun parseManagedBundleLatest_importsAsBundleV2() {
        val bundleJson = """
            {
              "version": "2.0",
              "exportedAtWallMs": 123456,
              "activeProfileId": "pilot-1",
              "profiles": [
                {
                  "id": "pilot-1",
                  "name": "Pilot Managed",
                  "aircraftType": "SAILPLANE"
                }
              ],
              "settings": {
                "version": "1.0",
                "sections": {
                  "tier_a.card_preferences": "captured"
                }
              }
            }
        """.trimIndent()

        val parsed = ProfileBundleCodec.parse(bundleJson).getOrThrow()

        assertEquals(ProfileBundleSourceFormat.BUNDLE_V2, parsed.sourceFormat)
        assertEquals("pilot-1", parsed.activeProfileId)
        assertEquals(1, parsed.profiles.size)
        assertEquals("pilot-1", parsed.profiles.first().id)
        assertEquals(123_456L, parsed.exportedAtWallMs)
        assertNotNull(parsed.settingsSnapshot.sections[ProfileSettingsSectionIds.CARD_PREFERENCES])
    }

    @Test
    fun parseLegacyProfileExport_importsProfilesAsLegacyFormat() {
        val json = """
            {
              "version": "1.0",
              "exportDate": "2026-03-07",
              "profiles": [
                {
                  "id": "legacy-1",
                  "name": "Legacy Pilot",
                  "aircraftType": "GLIDER"
                }
              ]
            }
        """.trimIndent()

        val parsed = ProfileBundleCodec.parse(json).getOrThrow()

        assertEquals(ProfileBundleSourceFormat.LEGACY_PROFILE_EXPORT_V1, parsed.sourceFormat)
        assertEquals(1, parsed.profiles.size)
        assertEquals("legacy-1", parsed.profiles.first().id)
        assertEquals(AircraftType.SAILPLANE, parsed.profiles.first().aircraftType)
        assertEquals("1.0", parsed.schemaVersion)
        assertEquals("2026-03-07", parsed.exportedAtLabel)
        assertTrue(parsed.settingsSnapshot.sections.isEmpty())
        assertEquals(null, parsed.activeProfileId)
    }

    @Test
    fun parseManagedProfileBackupDocument_importsSingleProfile() {
        val json = """
            {
              "version": "1.0",
              "generatedAtWallMs": 1000,
              "sequenceNumber": 2,
              "isActive": true,
              "profile": {
                "id": "managed-1",
                "name": "Managed Pilot",
                "aircraftType": "SAILPLANE"
              }
            }
        """.trimIndent()

        val parsed = ProfileBundleCodec.parse(json).getOrThrow()

        assertEquals(
            ProfileBundleSourceFormat.BACKUP_PROFILE_DOCUMENT_V1,
            parsed.sourceFormat
        )
        assertEquals(1, parsed.profiles.size)
        assertEquals("managed-1", parsed.profiles.first().id)
        assertEquals("managed-1", parsed.activeProfileId)
        assertTrue(parsed.settingsSnapshot.sections.isEmpty())
    }

    @Test
    fun parseSettingsSnapshotAliasKey_supportsSettingsSnapshotField() {
        val json = """
            {
              "version": "2.0",
              "activeProfileId": "pilot-1",
              "profiles": [
                {
                  "id": "pilot-1",
                  "name": "Pilot 1",
                  "aircraftType": "GLIDER"
                }
              ],
              "settingsSnapshot": {
                "version": "1.0",
                "sections": {
                  "tier_a.card_preferences": "ok"
                }
              }
            }
        """.trimIndent()

        val parsed = ProfileBundleCodec.parse(json).getOrThrow()

        assertEquals(ProfileBundleSourceFormat.BUNDLE_V2, parsed.sourceFormat)
        assertEquals(AircraftType.SAILPLANE, parsed.profiles.first().aircraftType)
        assertNotNull(parsed.settingsSnapshot.sections[ProfileSettingsSectionIds.CARD_PREFERENCES])
    }

    @Test
    fun parseIndexOnlyBackup_rejectsWithActionableMessage() {
        val json = """
            {
              "version": "1.0",
              "profileFiles": []
            }
        """.trimIndent()

        val result = ProfileBundleCodec.parse(json)

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue(message.contains("Index-only backup file selected"))
        assertTrue(message.contains("*_bundle_latest.json"))
    }

    @Test
    fun parseIndexOnlyBackup_withBundleFileName_rejectsWithNamedHint() {
        val json = """
            {
              "version": "1.0",
              "bundleFileName": "com.trust3.xcpro.debug_bundle_latest.json",
              "profileFiles": []
            }
        """.trimIndent()

        val result = ProfileBundleCodec.parse(json)

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue(message.contains("Index-only backup file selected"))
        assertTrue(message.contains("com.trust3.xcpro.debug_bundle_latest.json"))
    }

    @Test
    fun parseManagedIndexPointer_extractsBundleFileName() {
        val json = """
            {
              "version": "1.0",
              "bundleFileName": "com.trust3.xcpro.debug_bundle_latest.json",
              "profileFiles": []
            }
        """.trimIndent()

        val pointer = ProfileBundleCodec.parseManagedIndexPointer(json)

        assertNotNull(pointer)
        assertEquals("com.trust3.xcpro.debug_bundle_latest.json", pointer?.bundleFileName)
    }

    @Test
    fun parseManagedIndexPointer_returnsNullForNonIndexPayload() {
        val json = """
            {
              "version": "2.0",
              "profiles": []
            }
        """.trimIndent()

        val pointer = ProfileBundleCodec.parseManagedIndexPointer(json)

        assertNull(pointer)
    }

    @Test
    fun parseBundleV1_isAcceptedAsCompatibleMigrationInput() {
        val json = """
            {
              "version": "1.5",
              "exportedAtWallMs": 123456,
              "activeProfileId": "pilot-legacy",
              "profiles": [
                {
                  "id": "pilot-legacy",
                  "name": "Legacy Bundle Pilot",
                  "aircraftType": "GLIDER"
                }
              ],
              "settings": {
                "version": "1.0",
                "sections": {
                  "tier_a.units_preferences": "ok"
                }
              }
            }
        """.trimIndent()

        val parsed = ProfileBundleCodec.parse(json).getOrThrow()
        assertEquals(ProfileBundleSourceFormat.BUNDLE_V2, parsed.sourceFormat)
        assertEquals("pilot-legacy", parsed.activeProfileId)
        assertEquals(1, parsed.profiles.size)
        assertEquals(AircraftType.SAILPLANE, parsed.profiles.first().aircraftType)
    }

    @Test
    fun parseBundleV3_isRejectedWithActionableError() {
        val json = """
            {
              "version": "3.0",
              "activeProfileId": "pilot-future",
              "profiles": [
                {
                  "id": "pilot-future",
                  "name": "Future Pilot",
                  "aircraftType": "GLIDER"
                }
              ],
              "settings": {
                "version": "1.0",
                "sections": {}
              }
            }
        """.trimIndent()

        val result = ProfileBundleCodec.parse(json)
        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue(message.contains("Unsupported profile bundle version"))
        assertTrue(message.contains("1.x, 2.x"))
    }

    @Test
    fun parseUnsupportedSettingsSnapshotVersion_isRejected() {
        val json = """
            {
              "version": "2.0",
              "activeProfileId": "pilot-1",
              "profiles": [
                {
                  "id": "pilot-1",
                  "name": "Pilot",
                  "aircraftType": "GLIDER"
                }
              ],
              "settings": {
                "version": "2.0",
                "sections": {}
              }
            }
        """.trimIndent()

        val result = ProfileBundleCodec.parse(json)
        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue(message.contains("Unsupported profile settings snapshot version"))
        assertTrue(message.contains("1.x"))
    }

    @Test
    fun parseStarterAircraftProfileExamples_areAcceptedAsBundleV2() {
        val sailplaneJson = ProfileExampleFiles.readString(
            "xcpro-aircraft-profile-sailplane-asg-29-2026-03-10.json"
        )
        val hangGliderJson = ProfileExampleFiles.readString(
            "xcpro-aircraft-profile-hang-glider-moyes-litespeed-rs-2026-03-10.json"
        )

        val sailplane = ProfileBundleCodec.parse(sailplaneJson).getOrThrow()
        val hangGlider = ProfileBundleCodec.parse(hangGliderJson).getOrThrow()

        assertEquals(ProfileBundleSourceFormat.BUNDLE_V2, sailplane.sourceFormat)
        assertEquals("Sample Sailplane", sailplane.profiles.single().name)
        assertEquals(AircraftType.SAILPLANE, sailplane.profiles.single().aircraftType)
        assertTrue(sailplane.settingsSnapshot.sections.isEmpty())

        assertEquals(ProfileBundleSourceFormat.BUNDLE_V2, hangGlider.sourceFormat)
        assertEquals("Sample Hang Glider", hangGlider.profiles.single().name)
        assertEquals(AircraftType.HANG_GLIDER, hangGlider.profiles.single().aircraftType)
        assertTrue(hangGlider.settingsSnapshot.sections.isEmpty())
    }
}
