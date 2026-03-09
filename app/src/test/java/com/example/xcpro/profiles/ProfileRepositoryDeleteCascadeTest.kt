package com.example.xcpro.profiles

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryDeleteCascadeTest {

    private class RecordingCleaner(
        private val shouldFail: Boolean = false
    ) : ProfileScopedDataCleaner {
        val deletedProfileIds = mutableListOf<String>()

        override suspend fun clearProfileData(profileId: String) {
            if (shouldFail) error("cleanup failed")
            deletedProfileIds += profileId
        }
    }

    private class Harness(
        scope: CoroutineScope,
        cleaner: ProfileScopedDataCleaner
    ) {
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
        val repository = ProfileRepository(
            storage = storage,
            profileBackupSink = NoOpProfileBackupSink(),
            profileSettingsSnapshotProvider = NoOpProfileSettingsSnapshotProvider(),
            profileSettingsRestoreApplier = NoOpProfileSettingsRestoreApplier(),
            profileScopedDataCleaner = cleaner,
            internalScope = scope
        )
    }

    @Test
    fun deleteProfile_triggersProfileScopedDataCleanup() = runTest {
        val cleaner = RecordingCleaner()
        val harness = Harness(backgroundScope, cleaner)
        harness.repository.bootstrapComplete.first { it }
        val created = harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Pilot Delete",
                aircraftType = AircraftType.GLIDER
            )
        ).getOrThrow()

        val deleteResult = harness.repository.deleteProfile(created.id)

        assertTrue(deleteResult.isSuccess)
        assertEquals(listOf(created.id), cleaner.deletedProfileIds)
    }

    @Test
    fun deleteProfile_whenCleanupFails_doesNotDeleteProfile() = runTest {
        val harness = Harness(backgroundScope, RecordingCleaner(shouldFail = true))
        harness.repository.bootstrapComplete.first { it }
        val created = harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Pilot Keep",
                aircraftType = AircraftType.GLIDER
            )
        ).getOrThrow()

        val deleteResult = harness.repository.deleteProfile(created.id)
        val profilesAfter = harness.repository.profiles.value

        assertTrue(deleteResult.isFailure)
        assertTrue(profilesAfter.any { it.id == created.id })
    }
}
