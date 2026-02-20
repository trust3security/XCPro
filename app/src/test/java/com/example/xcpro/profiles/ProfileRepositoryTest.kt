package com.example.xcpro.profiles

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryTest {

    private val snapshotState = MutableStateFlow(
        ProfileStorageSnapshot(
            profilesJson = null,
            activeProfileId = null,
            readStatus = ProfileStorageReadStatus.OK
        )
    )
    private var writeProfilesCalls = 0
    private var writeActiveCalls = 0
    private var writeStateCalls = 0

    private val storage = object : ProfileStorage {
        override val snapshotFlow = snapshotState

        override suspend fun writeProfilesJson(json: String?) {
            writeProfilesCalls++
            snapshotState.value = snapshotState.value.copy(
                profilesJson = json,
                readStatus = ProfileStorageReadStatus.OK
            )
        }

        override suspend fun writeActiveProfileId(id: String?) {
            writeActiveCalls++
            snapshotState.value = snapshotState.value.copy(
                activeProfileId = id,
                readStatus = ProfileStorageReadStatus.OK
            )
        }

        override suspend fun writeState(profilesJson: String?, activeProfileId: String?) {
            writeStateCalls++
            snapshotState.value = snapshotState.value.copy(
                profilesJson = profilesJson,
                activeProfileId = activeProfileId,
                readStatus = ProfileStorageReadStatus.OK
            )
        }
    }

    private val repository = ProfileRepository(storage)

    @Test
    fun createAndSelectProfile_updatesActiveProfile() = runTest {
        val request = ProfileCreationRequest(
            name = "Test Pilot",
            aircraftType = AircraftType.SAILPLANE
        )

        val created = repository.createProfile(request).getOrThrow()
        assertEquals("Test Pilot", created.name)

        repository.setActiveProfile(created).getOrThrow()

        val active = repository.activeProfile.first()
        assertNotNull(active)
        assertEquals(created.id, active?.id)
    }

    @Test
    fun setActiveProfile_mergesProfileIfMissing() = runTest {
        val orphan = UserProfile(
            name = "Imported",
            aircraftType = AircraftType.GLIDER
        )

        repository.setActiveProfile(orphan).getOrThrow()

        val profiles = repository.profiles.first()
        assertTrue(profiles.any { it.id == orphan.id })
        val active = repository.activeProfile.first()
        assertEquals(orphan.id, active?.id)
    }

    @Test
    fun bootstrap_marksRepositoryHydrated() = runTest {
        assertTrue(repository.bootstrapComplete.first { it })
    }

    @Test
    fun parseFailure_preservesLastKnownGoodProfiles() = runTest {
        val created = repository.createProfile(
            ProfileCreationRequest(
                name = "Pilot A",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()
        repository.setActiveProfile(created).getOrThrow()

        val beforeProfiles = repository.profiles.value
        val beforeActiveId = repository.activeProfile.value?.id
        val baselineActiveWrites = writeActiveCalls
        snapshotState.value = snapshotState.value.copy(
            profilesJson = "{invalid-json",
            readStatus = ProfileStorageReadStatus.OK
        )

        val error = repository.bootstrapError.first { it != null }
        assertNotNull(error)
        assertEquals(beforeProfiles, repository.profiles.value)
        assertEquals(beforeActiveId, repository.activeProfile.value?.id)
        assertEquals(baselineActiveWrites, writeActiveCalls)
    }

    @Test
    fun missingActiveProfileId_fallsBackToFirstProfile() = runTest {
        val created = repository.createProfile(
            ProfileCreationRequest(
                name = "Pilot B",
                aircraftType = AircraftType.GLIDER
            )
        ).getOrThrow()
        repository.setActiveProfile(created).getOrThrow()
        val baselineActiveWrites = writeActiveCalls

        snapshotState.value = snapshotState.value.copy(
            activeProfileId = null,
            readStatus = ProfileStorageReadStatus.OK
        )

        val repairedSnapshot = withTimeout(5_000) {
            snapshotState.first { !it.activeProfileId.isNullOrBlank() }
        }
        assertEquals(created.id, repairedSnapshot.activeProfileId)
        assertTrue(writeActiveCalls > baselineActiveWrites)
        val active = repository.activeProfile.first()
        assertEquals(created.id, active?.id)
        assertEquals(created.id, snapshotState.value.activeProfileId)
    }

    @Test
    fun createFirstProfile_usesAtomicStorageWrite() = runTest {
        val baselineProfilesWrites = writeProfilesCalls
        val baselineActiveWrites = writeActiveCalls
        val baselineStateWrites = writeStateCalls

        repository.createProfile(
            ProfileCreationRequest(
                name = "Atomic",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()

        assertEquals(baselineStateWrites + 1, writeStateCalls)
        assertEquals(baselineProfilesWrites, writeProfilesCalls)
        assertEquals(baselineActiveWrites, writeActiveCalls)
    }

    @Test
    fun ioReadError_preservesLastKnownGoodState() = runTest {
        val created = repository.createProfile(
            ProfileCreationRequest(
                name = "Pilot C",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()
        repository.setActiveProfile(created).getOrThrow()

        val beforeProfiles = repository.profiles.value
        val beforeActiveId = repository.activeProfile.value?.id
        val baselineActiveWrites = writeActiveCalls
        snapshotState.value = ProfileStorageSnapshot(
            profilesJson = null,
            activeProfileId = null,
            readStatus = ProfileStorageReadStatus.IO_ERROR
        )

        val error = repository.bootstrapError.first { it?.contains("I/O") == true }
        assertNotNull(error)
        assertEquals(beforeProfiles, repository.profiles.value)
        assertEquals(beforeActiveId, repository.activeProfile.value?.id)
        assertEquals(baselineActiveWrites, writeActiveCalls)
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

        val localRepository = ProfileRepository(failingStorage)
        assertTrue(localRepository.bootstrapComplete.first { it })
        assertNotNull(localRepository.bootstrapError.first { it != null })
    }

    @Test
    fun invalidEntriesAreIgnoredDuringHydration() = runTest {
        val jsonWithInvalidEntries = """
            [
              {"id":"p1","name":"","aircraftType":"SAILPLANE"},
              {"id":"p2","name":"Pilot Two","aircraftType":"GLIDER"},
              {"id":"p2","name":"Duplicate","aircraftType":"GLIDER"}
            ]
        """.trimIndent()

        snapshotState.value = snapshotState.value.copy(
            profilesJson = jsonWithInvalidEntries,
            activeProfileId = "p2",
            readStatus = ProfileStorageReadStatus.OK
        )

        val hydrated = withTimeout(5_000) {
            repository.profiles.first { profiles -> profiles.any { it.id == "p2" } }
        }
        assertEquals(1, hydrated.size)
        assertEquals("p2", hydrated.first().id)
        val warning = repository.bootstrapError.first { it != null }
        assertNotNull(warning)
    }

    @Test
    fun nullEntriesAreIgnoredDuringHydration() = runTest {
        val jsonWithNullEntry = """
            [
              null,
              {"id":"p3","name":"Pilot Three","aircraftType":"GLIDER"}
            ]
        """.trimIndent()

        snapshotState.value = snapshotState.value.copy(
            profilesJson = jsonWithNullEntry,
            activeProfileId = "p3",
            readStatus = ProfileStorageReadStatus.OK
        )

        val hydrated = withTimeout(5_000) {
            repository.profiles.first { profiles -> profiles.any { it.id == "p3" } }
        }
        assertEquals(1, hydrated.size)
        assertEquals("p3", hydrated.first().id)
    }
}
