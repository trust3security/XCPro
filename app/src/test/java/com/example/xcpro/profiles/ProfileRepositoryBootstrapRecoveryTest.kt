package com.example.xcpro.profiles

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryBootstrapRecoveryTest {

    @Test
    fun bootstrap_marksRepositoryHydrated() = runTest {
        val harness = createScopedProfileRepositoryTestHarness(backgroundScope)
        assertTrue(harness.repository.bootstrapComplete.first { it })
    }

    @Test
    fun bootstrap_emptyState_provisionsDefaultProfileAndActiveId() = runTest {
        val harness = createScopedProfileRepositoryTestHarness(backgroundScope)

        val persisted = harness.snapshotState.first {
            !it.profilesJson.isNullOrBlank() && !it.activeProfileId.isNullOrBlank()
        }

        val profiles = harness.repository.profiles.first { it.isNotEmpty() }
        assertEquals(1, profiles.size)
        assertEquals(profiles.first().id, harness.repository.activeProfile.value?.id)
        assertEquals(profiles.first().id, persisted.activeProfileId)
        assertEquals(1, harness.writeStateCalls)
        assertTrue((persisted.profilesJson ?: "").contains(profiles.first().id))
    }

    @Test
    fun bootstrap_nonEmptySnapshot_withoutCanonicalDefault_insertsCanonicalDefault() = runTest {
        val harness = createScopedProfileRepositoryTestHarness(
            scope = backgroundScope,
            initialSnapshot = ProfileStorageSnapshot(
                profilesJson = """
                    [
                      {"id":"p1","name":"Pilot One","aircraftType":"GLIDER"}
                    ]
                """.trimIndent(),
                activeProfileId = "p1",
                readStatus = ProfileStorageReadStatus.OK
            )
        )

        val hydrated = harness.repository.profiles.first { profiles ->
            profiles.any { it.id == ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID } &&
                profiles.any { it.id == "p1" }
        }

        assertEquals(2, hydrated.size)
        assertEquals("p1", harness.repository.activeProfile.value?.id)
        assertTrue(harness.writeStateCalls > 0)
        assertTrue(
            (harness.snapshotState.value.profilesJson ?: "")
                .contains(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID)
        )
    }

    @Test
    fun parseFailure_preservesLastKnownGoodProfiles() = runTest {
        val harness = createScopedProfileRepositoryTestHarness(backgroundScope)
        val created = harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Pilot A",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()
        harness.repository.setActiveProfile(created).getOrThrow()

        val beforeProfiles = harness.repository.profiles.value
        val beforeActiveId = harness.repository.activeProfile.value?.id
        val baselineActiveWrites = harness.writeActiveCalls
        harness.snapshotState.value = harness.snapshotState.value.copy(
            profilesJson = "{invalid-json",
            readStatus = ProfileStorageReadStatus.OK
        )

        val error = harness.repository.bootstrapError.first { it != null }
        assertNotNull(error)
        assertEquals(beforeProfiles, harness.repository.profiles.value)
        assertEquals(beforeActiveId, harness.repository.activeProfile.value?.id)
        assertEquals(baselineActiveWrites, harness.writeActiveCalls)
    }

    @Test
    fun parseFailure_withoutLastKnownGood_recoversDefaultProfileAndPersistsState() = runTest {
        val harness = createScopedProfileRepositoryTestHarness(
            scope = backgroundScope,
            initialSnapshot = ProfileStorageSnapshot(
                profilesJson = "{invalid-json",
                activeProfileId = "missing-id",
                readStatus = ProfileStorageReadStatus.OK
            )
        )

        val hydrated = harness.repository.profiles.first { it.isNotEmpty() }
        assertEquals(1, hydrated.size)
        assertEquals(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID, hydrated.first().id)
        assertEquals(
            ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
            harness.repository.activeProfile.value?.id
        )
        assertTrue(harness.writeStateCalls > 0)
        assertTrue(
            (harness.snapshotState.value.profilesJson ?: "")
                .contains(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID)
        )
        assertTrue(harness.diagnosticsEvents.any { it.first == "profile_bootstrap_parse_failed" })
    }

    @Test
    fun missingActiveProfileId_fallsBackToFirstProfile() = runTest {
        val harness = createScopedProfileRepositoryTestHarness(backgroundScope)
        val created = harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Pilot B",
                aircraftType = AircraftType.GLIDER
            )
        ).getOrThrow()
        harness.repository.setActiveProfile(created).getOrThrow()
        val baselineActiveWrites = harness.writeActiveCalls

        harness.snapshotState.value = harness.snapshotState.value.copy(
            activeProfileId = null,
            readStatus = ProfileStorageReadStatus.OK
        )

        val repairedSnapshot = harness.snapshotState.first { !it.activeProfileId.isNullOrBlank() }
        assertEquals(created.id, repairedSnapshot.activeProfileId)
        assertTrue(harness.writeActiveCalls > baselineActiveWrites)
        val active = harness.repository.activeProfile.first()
        assertEquals(created.id, active?.id)
        assertEquals(created.id, harness.snapshotState.value.activeProfileId)
    }

    @Test
    fun ioReadError_preservesLastKnownGoodState() = runTest {
        val harness = createScopedProfileRepositoryTestHarness(backgroundScope)
        val created = harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Pilot C",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()
        harness.repository.setActiveProfile(created).getOrThrow()

        val beforeProfiles = harness.repository.profiles.value
        val beforeActiveId = harness.repository.activeProfile.value?.id
        val baselineActiveWrites = harness.writeActiveCalls
        harness.snapshotState.value = ProfileStorageSnapshot(
            profilesJson = null,
            activeProfileId = null,
            readStatus = ProfileStorageReadStatus.IO_ERROR
        )

        val error = harness.repository.bootstrapError.first { it?.contains("I/O") == true }
        assertNotNull(error)
        assertEquals(beforeProfiles, harness.repository.profiles.value)
        assertEquals(beforeActiveId, harness.repository.activeProfile.value?.id)
        assertEquals(baselineActiveWrites, harness.writeActiveCalls)
    }

    @Test
    fun unknownReadError_marksHydratedAndReportsError() = runTest {
        val failingSnapshotState = MutableStateFlow(
            ProfileStorageSnapshot(
                profilesJson = null,
                activeProfileId = null,
                readStatus = ProfileStorageReadStatus.UNKNOWN_ERROR
            )
        )
        val failingStorage = object : ProfileStorage {
            override val snapshotFlow = failingSnapshotState
            override suspend fun writeProfilesJson(json: String?) = Unit
            override suspend fun writeActiveProfileId(id: String?) = Unit
            override suspend fun writeState(profilesJson: String?, activeProfileId: String?) = Unit
        }

        val localRepository = ProfileRepository(failingStorage, backgroundScope)
        assertTrue(localRepository.bootstrapComplete.first { it })
        assertNotNull(localRepository.bootstrapError.first { it != null })
    }

    @Test
    fun invalidEntriesAreIgnoredDuringHydration() = runTest {
        val harness = createScopedProfileRepositoryTestHarness(backgroundScope)
        val jsonWithInvalidEntries = """
            [
              {"id":"p1","name":"","aircraftType":"SAILPLANE"},
              {"id":"p2","name":"Pilot Two","aircraftType":"GLIDER"},
              {"id":"p2","name":"Duplicate","aircraftType":"GLIDER"}
            ]
        """.trimIndent()

        harness.snapshotState.value = harness.snapshotState.value.copy(
            profilesJson = jsonWithInvalidEntries,
            activeProfileId = "p2",
            readStatus = ProfileStorageReadStatus.OK
        )
        assertTrue(harness.repository.bootstrapComplete.first { it })

        val hydrated = harness.repository.profiles.first { it.isNotEmpty() }
        assertEquals(2, hydrated.size)
        assertTrue(hydrated.any { it.id == "p2" })
        assertTrue(hydrated.any { it.id == ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID })
    }

    @Test
    fun nullEntriesAreIgnoredDuringHydration() = runTest {
        val harness = createScopedProfileRepositoryTestHarness(backgroundScope)
        val jsonWithNullEntry = """
            [
              null,
              {"id":"p3","name":"Pilot Three","aircraftType":"GLIDER"}
            ]
        """.trimIndent()

        harness.snapshotState.value = harness.snapshotState.value.copy(
            profilesJson = jsonWithNullEntry,
            activeProfileId = "p3",
            readStatus = ProfileStorageReadStatus.OK
        )

        val hydrated = harness.repository.profiles.first { profiles -> profiles.any { it.id == "p3" } }
        assertEquals(2, hydrated.size)
        assertTrue(hydrated.any { it.id == "p3" })
        assertTrue(hydrated.any { it.id == ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID })
    }

    @Test
    fun recoverWithDefaultProfile_afterReadError_provisionsCanonicalDefaultAndClearsError() = runTest {
        val harness = createScopedProfileRepositoryTestHarness(
            scope = backgroundScope,
            initialSnapshot = ProfileStorageSnapshot(
                profilesJson = null,
                activeProfileId = null,
                readStatus = ProfileStorageReadStatus.IO_ERROR
            )
        )
        assertTrue(harness.repository.bootstrapComplete.first { it })
        assertTrue(harness.repository.profiles.value.isEmpty())
        assertEquals(null, harness.repository.activeProfile.value)
        assertNotNull(harness.repository.bootstrapError.value)

        val recovery = harness.repository.recoverWithDefaultProfile()

        assertTrue(recovery.isSuccess)
        val recoveredProfiles = harness.repository.profiles.value
        val recoveredActive = harness.repository.activeProfile.value
        assertNotNull(recoveredActive)
        assertEquals(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID, recoveredActive?.id)
        assertTrue(recoveredProfiles.any { it.id == ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID })
        assertEquals(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID, harness.snapshotState.value.activeProfileId)
        assertEquals(null, harness.repository.bootstrapError.value)
        assertTrue(harness.diagnosticsEvents.any { it.first == "profile_recovery_start" })
        assertTrue(harness.diagnosticsEvents.any { it.first == "profile_recovery_success" })
    }

    @Test
    fun recoverWithDefaultProfile_preservesExistingProfilesAndSetsDefaultActive() = runTest {
        val harness = createScopedProfileRepositoryTestHarness(backgroundScope)
        val created = harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Recovery Pilot",
                aircraftType = AircraftType.GLIDER
            )
        ).getOrThrow()
        harness.repository.setActiveProfile(created).getOrThrow()
        val baselineProfileCount = harness.repository.profiles.value.size

        val recovery = harness.repository.recoverWithDefaultProfile()

        assertTrue(recovery.isSuccess)
        assertEquals(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID, harness.repository.activeProfile.value?.id)
        assertTrue(harness.repository.profiles.value.any { it.id == created.id })
        assertEquals(baselineProfileCount, harness.repository.profiles.value.size)
    }
}
