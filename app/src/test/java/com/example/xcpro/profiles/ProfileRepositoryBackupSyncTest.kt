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

        override suspend fun buildSnapshot(profileIds: Set<String>): ProfileSettingsSnapshot {
            requestedProfileIds.value = requestedProfileIds.value + listOf(profileIds)
            return ProfileSettingsSnapshot(
                sections = mapOf(
                    ProfileSettingsSectionIds.CARD_PREFERENCES to JsonPrimitive("captured")
                )
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
        val backupSink = RecordingProfileBackupSink()
        val snapshotProvider = RecordingSnapshotProvider()
        val repository = ProfileRepository(
            storage = storage,
            profileBackupSink = backupSink,
            profileSettingsSnapshotProvider = snapshotProvider,
            profileSettingsRestoreApplier = NoOpProfileSettingsRestoreApplier(),
            internalScope = scope
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
        val repository = ProfileRepository(
            storage = storage,
            profileBackupSink = backupSink,
            profileSettingsSnapshotProvider = snapshotProvider,
            profileSettingsRestoreApplier = NoOpProfileSettingsRestoreApplier(),
            internalScope = scope
        )
    }

    @Test
    fun bootstrap_emptyState_syncsDefaultProfileBackup() = runTest {
        val harness = Harness(backgroundScope)

        advanceUntilIdle()

        val calls = harness.backupSink.calls.first { it.isNotEmpty() }
        val latest = calls.last()
        val profiles = latest.profiles
        val activeProfileId = latest.activeProfileId
        assertEquals(1, profiles.size)
        assertEquals("default-profile", profiles.first().id)
        assertEquals("default-profile", activeProfileId)
        assertTrue(
            latest.settingsSnapshot.sections.containsKey(ProfileSettingsSectionIds.CARD_PREFERENCES)
        )
        val requestedIds = harness.snapshotProvider.requestedProfileIds.first { it.isNotEmpty() }.last()
        assertEquals(setOf("default-profile"), requestedIds)
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
    fun bootstrap_parseFailureWithoutFallback_recoversButSkipsBackupSyncUntilStable() = runTest {
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
        val activeProfileId = harness.repository.activeProfile.value?.id
        assertEquals(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID, activeProfileId)
    }
}
