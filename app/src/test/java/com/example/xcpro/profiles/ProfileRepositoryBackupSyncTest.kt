package com.example.xcpro.profiles

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryBackupSyncTest {

    private data class SyncCall(
        val profiles: List<UserProfile>,
        val activeProfileId: String?,
        val settingsSnapshot: ProfileSettingsSnapshot,
        val sequenceNumber: Long
    )

    private class RecordingProfileBackupSink : ProfileBackupSink {
        val calls = MutableStateFlow<List<SyncCall>>(emptyList())

        override suspend fun syncSnapshot(
            profiles: List<UserProfile>,
            activeProfileId: String?,
            settingsSnapshot: ProfileSettingsSnapshot,
            sequenceNumber: Long
        ) {
            calls.value = calls.value + SyncCall(
                profiles = profiles,
                activeProfileId = activeProfileId,
                settingsSnapshot = settingsSnapshot,
                sequenceNumber = sequenceNumber
            )
        }
    }

    private class RecordingSnapshotProvider : ProfileSettingsSnapshotProvider {
        val requestedProfileIds = MutableStateFlow<List<Set<String>>>(emptyList())
        val requestedSectionIds = MutableStateFlow<List<Set<String>>>(emptyList())
        val requestedSectionOrders = MutableStateFlow<List<List<String>>>(emptyList())

        override suspend fun buildSnapshot(
            profileIds: Set<String>,
            sectionIds: Set<String>
        ): ProfileSettingsSnapshot {
            requestedProfileIds.value = requestedProfileIds.value + listOf(profileIds)
            requestedSectionIds.value = requestedSectionIds.value + listOf(sectionIds)
            val sectionOrder = sectionIds.toList()
            requestedSectionOrders.value = requestedSectionOrders.value + listOf(sectionOrder)
            val sections = linkedMapOf<String, JsonPrimitive>()
            for (sectionId in sectionOrder) {
                sections[sectionId] = JsonPrimitive("captured:$sectionId")
            }
            return ProfileSettingsSnapshot(sections = sections)
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
        val backupSink = RecordingProfileBackupSink()
        val snapshotProvider = RecordingSnapshotProvider()
        val repository = createTestProfileRepository(
            storage = storage,
            scope = scope,
            profileBackupSink = backupSink,
            profileSettingsSnapshotProvider = snapshotProvider,
            profileSettingsRestoreApplier = NoOpProfileSettingsRestoreApplier()
        )
    }

    private class SnapshotHarness(
        scope: CoroutineScope,
        initialSnapshot: ProfileStorageSnapshot
    ) {
        val snapshotState = MutableStateFlow(initialSnapshot)
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
        val backupSink = RecordingProfileBackupSink()
        val snapshotProvider = RecordingSnapshotProvider()
        val repository = createTestProfileRepository(
            storage = storage,
            scope = scope,
            profileBackupSink = backupSink,
            profileSettingsSnapshotProvider = snapshotProvider,
            profileSettingsRestoreApplier = NoOpProfileSettingsRestoreApplier()
        )
    }

    @Test
    fun bootstrap_emptyState_skipsBackupSyncUntilFirstLaunchCompletes() = runTest {
        val harness = Harness(backgroundScope)

        advanceUntilIdle()

        assertTrue(harness.backupSink.calls.value.isEmpty())
        assertTrue(harness.snapshotProvider.requestedProfileIds.value.isEmpty())
    }

    @Test
    fun completeFirstLaunch_syncsCanonicalDefaultProfileBackup() = runTest {
        val harness = Harness(backgroundScope)

        advanceUntilIdle()
        harness.repository.completeFirstLaunch(AircraftType.HANG_GLIDER).getOrThrow()
        advanceUntilIdle()

        val latest = harness.backupSink.calls.first { it.isNotEmpty() }.last()
        val profiles = latest.profiles
        val activeProfileId = latest.activeProfileId
        assertEquals(1, profiles.size)
        assertEquals("default-profile", profiles.first().id)
        assertEquals(AircraftType.HANG_GLIDER, profiles.first().aircraftType)
        assertEquals("default-profile", activeProfileId)
        assertTrue(
            latest.settingsSnapshot.sections.containsKey(ProfileSettingsSectionIds.CARD_PREFERENCES)
        )
        val requestedIds = harness.snapshotProvider.requestedProfileIds.first { it.isNotEmpty() }.last()
        assertEquals(setOf("default-profile"), requestedIds)
        val requestedSections = harness.snapshotProvider.requestedSectionIds
            .first { it.isNotEmpty() }
            .last()
        assertEquals(ProfileSettingsSectionSets.CAPTURED_SECTION_IDS, requestedSections)
        val requestedSectionOrder = harness.snapshotProvider.requestedSectionOrders
            .first { it.isNotEmpty() }
            .last()
        assertEquals(ProfileSettingsSectionSets.CAPTURED_SECTION_ORDER, requestedSectionOrder)
        assertEquals(
            ProfileSettingsSectionSets.CAPTURED_SECTION_ORDER,
            latest.settingsSnapshot.sections.keys.toList()
        )
    }

    @Test
    fun bootstrap_existingCanonicalSnapshot_syncsBackupForRestoredState() = runTest {
        val harness = SnapshotHarness(
            scope = backgroundScope,
            initialSnapshot = ProfileStorageSnapshot(
                profilesJson = """
                    [
                      {"id":"default-profile","name":"Default","aircraftType":"PARAGLIDER"},
                      {"id":"p-restored","name":"Restored Pilot","aircraftType":"SAILPLANE"}
                    ]
                """.trimIndent(),
                activeProfileId = "p-restored",
                readStatus = ProfileStorageReadStatus.OK
            )
        )

        advanceUntilIdle()

        val latest = harness.backupSink.calls.first { it.isNotEmpty() }.last()
        assertEquals("p-restored", latest.activeProfileId)
        assertEquals(2, latest.profiles.size)
        assertTrue(latest.profiles.any { it.id == "p-restored" })
        assertTrue(latest.profiles.any { it.id == "default-profile" })
    }

    @Test
    fun createProfile_syncsLatestProfileSnapshotToBackupFolder() = runTest {
        val harness = Harness(backgroundScope)
        advanceUntilIdle()

        val created = harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Pilot Backup",
                aircraftType = AircraftType.GLIDER
            )
        ).getOrThrow()

        advanceUntilIdle()

        val calls = harness.backupSink.calls.first { it.isNotEmpty() }
        val latest = calls.last()
        val profiles = latest.profiles
        val activeProfileId = latest.activeProfileId
        assertTrue(profiles.any { it.id == created.id })
        assertEquals(created.id, activeProfileId)
        assertTrue(
            latest.settingsSnapshot.sections.containsKey(ProfileSettingsSectionIds.CARD_PREFERENCES)
        )
    }

    @Test
    fun bootstrap_parseFailureWithoutFallback_skipsBackupSyncAndLeavesRecoveryState() = runTest {
        val harness = SnapshotHarness(
            scope = backgroundScope,
            initialSnapshot = ProfileStorageSnapshot(
                profilesJson = "{invalid-json",
                activeProfileId = null,
                readStatus = ProfileStorageReadStatus.OK
            )
        )

        advanceUntilIdle()
        harness.repository.bootstrapComplete.first { it }

        assertTrue(harness.backupSink.calls.value.isEmpty())
        assertEquals(null, harness.repository.activeProfile.value)
        assertTrue(harness.repository.profiles.value.isEmpty())
    }
}
