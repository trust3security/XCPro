package com.example.xcpro.profiles

import com.google.gson.JsonPrimitive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryBundleTest {

    @Test
    fun exportBundle_exportsSelectedProfileAndSettingsSnapshot() = runTest {
        val harness = ProfileRepositoryBundleHarness(backgroundScope)
        advanceUntilIdle()
        val created = harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Pilot Bundle",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()
        advanceUntilIdle()

        val exportArtifact = harness.repository.exportBundle(setOf(created.id)).getOrThrow()
        val parsed = ProfileBundleCodec.parse(exportArtifact.bundleJson).getOrThrow()

        assertEquals(1, parsed.profiles.size)
        assertEquals(created.id, parsed.profiles.first().id)
        assertEquals(created.id, parsed.activeProfileId)
        assertEquals(harness.clock.nowWallMs(), parsed.exportedAtWallMs)
        assertEquals(exportArtifact.exportedAtWallMs, parsed.exportedAtWallMs)
        assertEquals(ProfileSettingsSectionSets.AIRCRAFT_PROFILE_SECTION_IDS, parsed.settingsSnapshot.sections.keys)
        assertEquals(ProfileSettingsSectionSets.AIRCRAFT_PROFILE_SECTION_ORDER, parsed.settingsSnapshot.sections.keys.toList())
        assertTrue(parsed.settingsSnapshot.sections.containsKey(ProfileSettingsSectionIds.CARD_PREFERENCES))
        assertTrue(!parsed.settingsSnapshot.sections.containsKey(ProfileSettingsSectionIds.WEATHER_OVERLAY_PREFERENCES))
        assertTrue(harness.snapshotProvider.requestedProfileIds.last().contains(created.id))
        assertEquals(ProfileSettingsSectionSets.AIRCRAFT_PROFILE_SECTION_IDS, harness.snapshotProvider.requestedSectionIds.last())
        assertEquals(ProfileSettingsSectionSets.AIRCRAFT_PROFILE_SECTION_ORDER, harness.snapshotProvider.requestedSectionOrders.last())
        assertEquals(AircraftProfileFileNames.buildExportFileName(profile = created, nowWallMs = exportArtifact.exportedAtWallMs), exportArtifact.suggestedFileName)
        assertTrue(harness.diagnosticsEvents.any { it.first == "profile_bundle_export_success" })
    }

    @Test
    fun exportBundle_doesNotEmitLegacyGliderType() = runTest {
        val harness = ProfileRepositoryBundleHarness(backgroundScope)
        advanceUntilIdle()
        val created = harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Legacy Glider Input",
                aircraftType = AircraftType.GLIDER
            )
        ).getOrThrow()
        advanceUntilIdle()

        val exportArtifact = harness.repository.exportBundle(setOf(created.id)).getOrThrow()

        assertTrue(!exportArtifact.bundleJson.contains("\"GLIDER\""))
        assertTrue(exportArtifact.bundleJson.contains("\"SAILPLANE\""))
    }

    @Test
    fun previewBundle_reportsMetadataCollisionHintsAndIgnoredGlobalSections() = runTest {
        val harness = ProfileRepositoryBundleHarness(backgroundScope)
        advanceUntilIdle()
        harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Preview Match",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()
        advanceUntilIdle()

        val bundleJson = ProfileBundleCodec.serialize(
            ProfileBundleDocument(
                exportedAtWallMs = 123_456L,
                activeProfileId = "preview-1",
                profiles = listOf(
                    UserProfile(
                        id = "preview-1",
                        name = "Preview Match",
                        aircraftType = AircraftType.GLIDER,
                        aircraftModel = "LS8",
                        createdAt = 1_000L,
                        lastUsed = 2_000L
                    )
                ),
                settings = ProfileSettingsSnapshot(
                    sections = mapOf(
                        ProfileSettingsSectionIds.UNITS_PREFERENCES to JsonPrimitive("units"),
                        ProfileSettingsSectionIds.WEATHER_OVERLAY_PREFERENCES to JsonPrimitive("weather")
                    )
                )
            )
        )

        val preview = harness.repository.previewBundle(bundleJson).getOrThrow()

        assertEquals(ProfileBundleSourceFormat.BUNDLE_V2, preview.sourceFormat)
        assertEquals("2.0", preview.schemaVersion)
        assertEquals(123_456L, preview.exportedAtWallMs)
        assertEquals("Preview Match (LS8)", preview.preferredActiveProfileName)
        assertEquals(1, preview.profiles.size)
        assertEquals(AircraftType.SAILPLANE, preview.profiles.single().aircraftType)
        assertTrue(preview.profiles.single().matchesExistingProfileName)
        assertTrue(preview.aircraftProfileSectionIds.contains(ProfileSettingsSectionIds.UNITS_PREFERENCES))
        assertTrue(preview.ignoredGlobalSectionIds.contains(ProfileSettingsSectionIds.WEATHER_OVERLAY_PREFERENCES))
    }

    @Test
    fun importBundle_fullBundleScope_forExportedAircraftProfileDoesNotExposeGlobalSections() = runTest {
        val exportingHarness = ProfileRepositoryBundleHarness(backgroundScope)
        advanceUntilIdle()
        val created = exportingHarness.repository.createProfile(
            ProfileCreationRequest(
                name = "Export Source",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()
        advanceUntilIdle()

        val exportedJson = exportingHarness.repository.exportBundle(setOf(created.id)).getOrThrow().bundleJson

        val importingHarness = ProfileRepositoryBundleHarness(backgroundScope)
        advanceUntilIdle()
        importingHarness.repository.importBundle(
            ProfileBundleImportRequest(
                json = exportedJson,
                keepCurrentActive = false,
                settingsImportScope = ProfileSettingsImportScope.FULL_BUNDLE
            )
        ).getOrThrow()

        assertEquals(1, importingHarness.restoreApplier.calls.size)
        val restoredSections = importingHarness.restoreApplier.calls.first().settingsSnapshot.sections.keys
        assertEquals(ProfileSettingsSectionSets.AIRCRAFT_PROFILE_SECTION_IDS, restoredSections)
        assertEquals(ProfileSettingsSectionSets.AIRCRAFT_PROFILE_SECTION_ORDER, restoredSections.toList())
        assertTrue(!restoredSections.contains(ProfileSettingsSectionIds.WEATHER_OVERLAY_PREFERENCES))
        assertTrue(!restoredSections.contains(ProfileSettingsSectionIds.OGN_TRAFFIC_PREFERENCES))
    }

    @Test
    fun importBundle_usesPreferredImportedActiveAndAppliesSettings() = runTest {
        val harness = ProfileRepositoryBundleHarness(backgroundScope)
        advanceUntilIdle()
        val existing = harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Existing",
                aircraftType = AircraftType.GLIDER
            )
        ).getOrThrow()
        harness.repository.setActiveProfile(existing).getOrThrow()
        advanceUntilIdle()

        val imported = UserProfile(
            id = "default",
            name = "Imported Alias",
            aircraftType = AircraftType.PARAGLIDER,
            createdAt = 1_000L,
            lastUsed = 2_000L
        )
        val bundleJson = ProfileBundleCodec.serialize(
            ProfileBundleDocument(
                exportedAtWallMs = 1_500L,
                activeProfileId = "default",
                profiles = listOf(imported),
                settings = ProfileSettingsSnapshot(
                    sections = mapOf(
                        ProfileSettingsSectionIds.CARD_PREFERENCES to JsonPrimitive("settings")
                    )
                )
            )
        )

        val result = harness.repository.importBundle(
            ProfileBundleImportRequest(
                json = bundleJson,
                keepCurrentActive = false
            )
        ).getOrThrow()

        val mappedId = result.profileImportResult.importedProfileIdMap.getValue("default")
        assertEquals(
            mappedId,
            result.profileImportResult.importedProfileIdMap.getValue(
                ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID
            )
        )
        assertEquals(mappedId, result.profileImportResult.activeProfileAfter)
        assertEquals(mappedId, harness.repository.activeProfile.value?.id)
        assertEquals(1, harness.restoreApplier.calls.size)
        assertEquals(
            mappedId,
            harness.restoreApplier.calls.first().importedProfileIdMap["default"]
        )
        assertTrue(harness.diagnosticsEvents.any { it.first == "profile_bundle_import_success" })
    }

    @Test
    fun importBundle_doesNotApplySettingsWhenNoProfilesImported() = runTest {
        val harness = ProfileRepositoryBundleHarness(backgroundScope)
        advanceUntilIdle()

        val invalid = UserProfile(
            id = "invalid-1",
            name = " ",
            aircraftType = AircraftType.GLIDER,
            createdAt = 1_000L,
            lastUsed = 2_000L
        )
        val bundleJson = ProfileBundleCodec.serialize(
            ProfileBundleDocument(
                exportedAtWallMs = 1_500L,
                activeProfileId = invalid.id,
                profiles = listOf(invalid),
                settings = ProfileSettingsSnapshot(
                    sections = mapOf(
                        ProfileSettingsSectionIds.CARD_PREFERENCES to JsonPrimitive("settings")
                    )
                )
            )
        )

        val result = harness.repository.importBundle(
            ProfileBundleImportRequest(json = bundleJson)
        ).getOrThrow()

        assertEquals(0, result.profileImportResult.importedCount)
        assertTrue(harness.restoreApplier.calls.isEmpty())
    }

    @Test
    fun importBundle_replaceExistingPolicy_replacesMatchingNameWithoutDuplicate() = runTest {
        val harness = ProfileRepositoryBundleHarness(backgroundScope)
        advanceUntilIdle()
        val existing = harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Pilot Replace",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()
        harness.repository.setActiveProfile(existing).getOrThrow()
        val beforeCount = harness.repository.profiles.value.size

        val incoming = UserProfile(
            id = "incoming-replace",
            name = "Pilot Replace",
            aircraftType = AircraftType.GLIDER,
            createdAt = 1_000L,
            lastUsed = 2_000L
        )
        val bundleJson = ProfileBundleCodec.serialize(
            ProfileBundleDocument(
                exportedAtWallMs = 1_500L,
                activeProfileId = incoming.id,
                profiles = listOf(incoming),
                settings = ProfileSettingsSnapshot.empty()
            )
        )

        val result = harness.repository.importBundle(
            ProfileBundleImportRequest(
                json = bundleJson,
                keepCurrentActive = true,
                nameCollisionPolicy = ProfileNameCollisionPolicy.REPLACE_EXISTING
            )
        ).getOrThrow()

        val afterProfiles = harness.repository.profiles.value
        assertEquals(beforeCount, afterProfiles.size)
        assertEquals(1, afterProfiles.count { it.name == "Pilot Replace" })
        val replaced = afterProfiles.first { it.name == "Pilot Replace" }
        assertEquals(existing.id, replaced.id)
        assertEquals(AircraftType.SAILPLANE, replaced.aircraftType)
        assertEquals(existing.id, result.profileImportResult.importedProfileIdMap["incoming-replace"])
    }

    @Test
    fun importBundle_profilesOnlyScope_skipsSettingsRestore() = runTest {
        val harness = ProfileRepositoryBundleHarness(backgroundScope)
        advanceUntilIdle()
        val incoming = UserProfile(
            id = "incoming-1",
            name = "Scope Profiles Only",
            aircraftType = AircraftType.GLIDER,
            createdAt = 1_000L,
            lastUsed = 2_000L
        )
        val bundleJson = ProfileBundleCodec.serialize(
            ProfileBundleDocument(
                exportedAtWallMs = 1_500L,
                activeProfileId = incoming.id,
                profiles = listOf(incoming),
                settings = ProfileSettingsSnapshot(
                    sections = mapOf(
                        ProfileSettingsSectionIds.CARD_PREFERENCES to JsonPrimitive("settings")
                    )
                )
            )
        )

        val result = harness.repository.importBundle(
            ProfileBundleImportRequest(
                json = bundleJson,
                keepCurrentActive = false,
                settingsImportScope = ProfileSettingsImportScope.PROFILES_ONLY
            )
        ).getOrThrow()

        assertEquals(1, result.profileImportResult.importedCount)
        assertTrue(harness.restoreApplier.calls.isEmpty())
    }

    @Test
    fun importBundle_profileScopedScope_filtersOutGlobalSections() = runTest {
        val harness = ProfileRepositoryBundleHarness(backgroundScope)
        advanceUntilIdle()
        val incoming = UserProfile(
            id = "incoming-2",
            name = "Scope Profile Settings",
            aircraftType = AircraftType.GLIDER,
            createdAt = 1_000L,
            lastUsed = 2_000L
        )
        val bundleJson = ProfileBundleCodec.serialize(
            ProfileBundleDocument(
                exportedAtWallMs = 1_500L,
                activeProfileId = incoming.id,
                profiles = listOf(incoming),
                settings = ProfileSettingsSnapshot(
                    sections = mapOf(
                        ProfileSettingsSectionIds.UNITS_PREFERENCES to JsonPrimitive("units"),
                        ProfileSettingsSectionIds.MAP_STYLE_PREFERENCES to JsonPrimitive("map-style"),
                        ProfileSettingsSectionIds.SNAIL_TRAIL_PREFERENCES to JsonPrimitive("trail"),
                        ProfileSettingsSectionIds.ORIENTATION_PREFERENCES to JsonPrimitive("orientation"),
                        ProfileSettingsSectionIds.QNH_PREFERENCES to JsonPrimitive("qnh"),
                        ProfileSettingsSectionIds.WEATHER_OVERLAY_PREFERENCES to JsonPrimitive("weather")
                    )
                )
            )
        )

        harness.repository.importBundle(
            ProfileBundleImportRequest(
                json = bundleJson,
                keepCurrentActive = false,
                settingsImportScope = ProfileSettingsImportScope.PROFILE_SCOPED_SETTINGS
            )
        ).getOrThrow()

        assertEquals(1, harness.restoreApplier.calls.size)
        val restoredSections = harness.restoreApplier.calls.first().settingsSnapshot.sections.keys
        assertTrue(restoredSections.contains(ProfileSettingsSectionIds.UNITS_PREFERENCES))
        assertTrue(restoredSections.contains(ProfileSettingsSectionIds.MAP_STYLE_PREFERENCES))
        assertTrue(restoredSections.contains(ProfileSettingsSectionIds.SNAIL_TRAIL_PREFERENCES))
        assertTrue(restoredSections.contains(ProfileSettingsSectionIds.ORIENTATION_PREFERENCES))
        assertTrue(restoredSections.contains(ProfileSettingsSectionIds.QNH_PREFERENCES))
        assertTrue(!restoredSections.contains(ProfileSettingsSectionIds.WEATHER_OVERLAY_PREFERENCES))
    }

    @Test
    fun importBundle_strictRestore_failsWhenAnySectionFails() = runTest {
        val harness = ProfileRepositoryBundleHarness(backgroundScope)
        advanceUntilIdle()
        harness.restoreApplier.failSections += ProfileSettingsSectionIds.CARD_PREFERENCES
        val incoming = UserProfile(
            id = "incoming-3",
            name = "Strict Restore",
            aircraftType = AircraftType.GLIDER,
            createdAt = 1_000L,
            lastUsed = 2_000L
        )
        val bundleJson = ProfileBundleCodec.serialize(
            ProfileBundleDocument(
                exportedAtWallMs = 1_500L,
                activeProfileId = incoming.id,
                profiles = listOf(incoming),
                settings = ProfileSettingsSnapshot(
                    sections = mapOf(
                        ProfileSettingsSectionIds.CARD_PREFERENCES to JsonPrimitive("settings")
                    )
                )
            )
        )

        val result = harness.repository.importBundle(
            ProfileBundleImportRequest(
                json = bundleJson,
                keepCurrentActive = false,
                settingsImportScope = ProfileSettingsImportScope.FULL_BUNDLE,
                strictSettingsRestore = true
            )
        )

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull()?.message ?: "")
                .contains(ProfileSettingsSectionIds.CARD_PREFERENCES)
        )
    }

    @Test
    fun importStarterAircraftProfileExamples_canBeSwitchedBetween() = runTest {
        val harness = ProfileRepositoryBundleHarness(backgroundScope)
        advanceUntilIdle()

        val sailplaneJson = ProfileExampleFiles.readString(
            "xcpro-aircraft-profile-sailplane-asg-29-2026-03-10.json"
        )
        val hangGliderJson = ProfileExampleFiles.readString(
            "xcpro-aircraft-profile-hang-glider-moyes-litespeed-rs-2026-03-10.json"
        )

        val sailplaneImport = harness.repository.importBundle(
            ProfileBundleImportRequest(
                json = sailplaneJson,
                keepCurrentActive = false
            )
        ).getOrThrow()
        val hangGliderImport = harness.repository.importBundle(
            ProfileBundleImportRequest(
                json = hangGliderJson,
                keepCurrentActive = false
            )
        ).getOrThrow()

        val sailplaneId = sailplaneImport.profileImportResult.importedProfileIdMap
            .getValue("aircraft-sailplane-asg29")
        val hangGliderId = hangGliderImport.profileImportResult.importedProfileIdMap
            .getValue("aircraft-hangglider-moyes-litespeed-rs")

        assertEquals(hangGliderId, harness.repository.activeProfile.value?.id)
        assertTrue(harness.repository.profiles.value.any { it.aircraftModel == "ASG 29" })
        assertTrue(
            harness.repository.profiles.value.any { it.aircraftModel == "Moyes Litespeed RS" }
        )

        val sailplaneProfile = harness.repository.profiles.value.first { it.id == sailplaneId }
        harness.repository.setActiveProfile(sailplaneProfile).getOrThrow()
        assertEquals(sailplaneId, harness.repository.activeProfile.value?.id)

        val hangGliderProfile = harness.repository.profiles.value.first { it.id == hangGliderId }
        harness.repository.setActiveProfile(hangGliderProfile).getOrThrow()
        assertEquals(hangGliderId, harness.repository.activeProfile.value?.id)
    }
}
