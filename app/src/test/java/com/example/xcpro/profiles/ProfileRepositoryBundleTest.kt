package com.example.xcpro.profiles

import com.google.gson.JsonPrimitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryBundleTest {

    private class RecordingSnapshotProvider : ProfileSettingsSnapshotProvider {
        val requestedProfileIds = mutableListOf<Set<String>>()

        override suspend fun buildSnapshot(profileIds: Set<String>): ProfileSettingsSnapshot {
            requestedProfileIds += profileIds
            return ProfileSettingsSnapshot(
                sections = mapOf(
                    ProfileSettingsSectionIds.CARD_PREFERENCES to JsonPrimitive("snapshot")
                )
            )
        }
    }

    private class RecordingRestoreApplier : ProfileSettingsRestoreApplier {
        data class Call(
            val settingsSnapshot: ProfileSettingsSnapshot,
            val importedProfileIdMap: Map<String, String>
        )

        val calls = mutableListOf<Call>()

        override suspend fun apply(
            settingsSnapshot: ProfileSettingsSnapshot,
            importedProfileIdMap: Map<String, String>
        ): ProfileSettingsRestoreResult {
            calls += Call(settingsSnapshot, importedProfileIdMap)
            return ProfileSettingsRestoreResult(
                appliedSections = settingsSnapshot.sections.keys
            )
        }
    }

    private class Harness(scope: CoroutineScope) {
        val snapshotState = MutableStateFlow(
            ProfileStorageSnapshot(
                profilesJson = null,
                activeProfileId = null,
                readStatus = ProfileStorageReadStatus.OK
            )
        )
        val storage = object : ProfileStorage {
            override val snapshotFlow = snapshotState

            override suspend fun writeProfilesJson(json: String?) {
                snapshotState.value = snapshotState.value.copy(
                    profilesJson = json,
                    readStatus = ProfileStorageReadStatus.OK
                )
            }

            override suspend fun writeActiveProfileId(id: String?) {
                snapshotState.value = snapshotState.value.copy(
                    activeProfileId = id,
                    readStatus = ProfileStorageReadStatus.OK
                )
            }

            override suspend fun writeState(profilesJson: String?, activeProfileId: String?) {
                snapshotState.value = snapshotState.value.copy(
                    profilesJson = profilesJson,
                    activeProfileId = activeProfileId,
                    readStatus = ProfileStorageReadStatus.OK
                )
            }
        }
        val snapshotProvider = RecordingSnapshotProvider()
        val restoreApplier = RecordingRestoreApplier()
        val diagnosticsEvents = mutableListOf<Pair<String, Map<String, String>>>()
        private val diagnosticsReporter = object : ProfileDiagnosticsReporter {
            override fun report(event: String, attributes: Map<String, String>) {
                diagnosticsEvents += event to attributes
            }
        }
        val repository = ProfileRepository(
            storage = storage,
            profileBackupSink = NoOpProfileBackupSink(),
            profileSettingsSnapshotProvider = snapshotProvider,
            profileSettingsRestoreApplier = restoreApplier,
            profileDiagnosticsReporter = diagnosticsReporter,
            internalScope = scope
        )
    }

    @Test
    fun exportBundle_exportsSelectedProfileAndSettingsSnapshot() = runTest {
        val harness = Harness(backgroundScope)
        advanceUntilIdle()
        val created = harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Pilot Bundle",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()
        advanceUntilIdle()

        val json = harness.repository.exportBundle(setOf(created.id)).getOrThrow()
        val parsed = ProfileBundleCodec.parse(json).getOrThrow()

        assertEquals(1, parsed.profiles.size)
        assertEquals(created.id, parsed.profiles.first().id)
        assertEquals(created.id, parsed.activeProfileId)
        assertTrue(
            parsed.settingsSnapshot.sections.containsKey(ProfileSettingsSectionIds.CARD_PREFERENCES)
        )
        assertTrue(harness.snapshotProvider.requestedProfileIds.last().contains(created.id))
        assertTrue(harness.diagnosticsEvents.any { it.first == "profile_bundle_export_success" })
    }

    @Test
    fun importBundle_usesPreferredImportedActiveAndAppliesSettings() = runTest {
        val harness = Harness(backgroundScope)
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
            aircraftType = AircraftType.PARAGLIDER
        )
        val bundleJson = ProfileBundleCodec.serialize(
            ProfileBundleDocument(
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
        val harness = Harness(backgroundScope)
        advanceUntilIdle()

        val invalid = UserProfile(
            id = "invalid-1",
            name = " ",
            aircraftType = AircraftType.GLIDER
        )
        val bundleJson = ProfileBundleCodec.serialize(
            ProfileBundleDocument(
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
        val harness = Harness(backgroundScope)
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
            aircraftType = AircraftType.GLIDER
        )
        val bundleJson = ProfileBundleCodec.serialize(
            ProfileBundleDocument(
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
        assertEquals(AircraftType.GLIDER, replaced.aircraftType)
        assertEquals(existing.id, result.profileImportResult.importedProfileIdMap["incoming-replace"])
    }
}
