package com.example.xcpro.profiles

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun createProfile_allowsMultipleProfilesForSameAircraftType() = runTest {
        repository.createProfile(
            ProfileCreationRequest(
                name = "Club Ship",
                aircraftType = AircraftType.SAILPLANE,
                aircraftModel = "LS4"
            )
        ).getOrThrow()

        repository.createProfile(
            ProfileCreationRequest(
                name = "Competition Ship",
                aircraftType = AircraftType.SAILPLANE,
                aircraftModel = "Ventus 3"
            )
        ).getOrThrow()

        val sailplanes = repository.profiles.value.filter { it.aircraftType == AircraftType.SAILPLANE }
        assertEquals(2, sailplanes.size)
        assertTrue(sailplanes.any { it.aircraftModel == "LS4" })
        assertTrue(sailplanes.any { it.aircraftModel == "Ventus 3" })
    }

    @Test
    fun updateProfile_persistsPolarSettingsPerProfile() = runTest {
        val created = repository.createProfile(
            ProfileCreationRequest(
                name = "Polar Pilot",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()

        val updated = created.copy(
            polar = ProfilePolarSettings(
                lowSpeedKmh = 85.0,
                lowSinkMs = 0.55,
                midSpeedKmh = 115.0,
                midSinkMs = 0.78,
                highSpeedKmh = 175.0,
                highSinkMs = 1.95
            )
        )

        repository.updateProfile(updated).getOrThrow()

        val stored = repository.profiles.value.first { it.id == created.id }
        assertEquals(85.0, stored.polar.lowSpeedKmh, 0.0)
        assertEquals(0.55, stored.polar.lowSinkMs, 0.0)
        assertEquals(115.0, stored.polar.midSpeedKmh, 0.0)
        assertEquals(0.78, stored.polar.midSinkMs, 0.0)
        assertEquals(175.0, stored.polar.highSpeedKmh, 0.0)
        assertEquals(1.95, stored.polar.highSinkMs, 0.0)
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
    fun importProfiles_preservesActiveProfile_whenKeepCurrentActive() = runTest {
        val existing = repository.createProfile(
            ProfileCreationRequest(
                name = "Existing Pilot",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()
        repository.setActiveProfile(existing).getOrThrow()

        val incoming = UserProfile(
            name = "Imported Pilot",
            aircraftType = AircraftType.GLIDER
        )
        val result = repository.importProfiles(
            ProfileImportRequest(
                profiles = listOf(incoming),
                keepCurrentActive = true
            )
        ).getOrThrow()

        assertEquals(1, result.importedCount)
        assertEquals(existing.id, repository.activeProfile.value?.id)
        assertEquals(existing.id, result.activeProfileAfter)
    }

    @Test
    fun importProfiles_preservesImportedPreferencesAndMetadata() = runTest {
        val imported = UserProfile(
            name = "Preference Import",
            aircraftType = AircraftType.GLIDER,
            preferences = ProfilePreferences(
                units = UnitSystem.IMPERIAL,
                autoSwitchModes = false,
                cardAnimations = false
            ),
            createdAt = 1234L,
            lastUsed = 5678L
        )

        repository.importProfiles(ProfileImportRequest(profiles = listOf(imported))).getOrThrow()

        val stored = repository.profiles.value.first { it.name == "Preference Import" }
        assertEquals(UnitSystem.IMPERIAL, stored.preferences.units)
        assertEquals(false, stored.preferences.autoSwitchModes)
        assertEquals(false, stored.preferences.cardAnimations)
        assertEquals(1234L, stored.createdAt)
        assertEquals(5678L, stored.lastUsed)
    }

    @Test
    fun importProfiles_usesSingleAtomicWrite() = runTest {
        val baselineProfilesWrites = writeProfilesCalls
        val baselineActiveWrites = writeActiveCalls
        val baselineStateWrites = writeStateCalls
        val imports = listOf(
            UserProfile(name = "Import A", aircraftType = AircraftType.SAILPLANE),
            UserProfile(name = "Import B", aircraftType = AircraftType.GLIDER)
        )

        repository.importProfiles(ProfileImportRequest(profiles = imports)).getOrThrow()

        assertEquals(baselineStateWrites + 1, writeStateCalls)
        assertEquals(baselineProfilesWrites, writeProfilesCalls)
        assertEquals(baselineActiveWrites, writeActiveCalls)
    }

    @Test
    fun importProfiles_skipsInvalidEntriesAndReportsFailure() = runTest {
        val imports = listOf(
            UserProfile(name = " ", aircraftType = AircraftType.SAILPLANE),
            UserProfile(name = "Valid Import", aircraftType = AircraftType.GLIDER)
        )

        val result = repository.importProfiles(ProfileImportRequest(profiles = imports)).getOrThrow()

        assertEquals(2, result.requestedCount)
        assertEquals(1, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(1, result.failures.size)
        assertTrue(repository.profiles.value.any { it.name == "Valid Import" })
    }

    @Test
    fun importProfiles_canSelectNewestImported_whenKeepCurrentActiveFalse() = runTest {
        val existing = repository.createProfile(
            ProfileCreationRequest(
                name = "Existing Pilot",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()
        repository.setActiveProfile(existing).getOrThrow()

        val firstImported = UserProfile(name = "Imported One", aircraftType = AircraftType.GLIDER)
        val secondImported = UserProfile(name = "Imported Two", aircraftType = AircraftType.PARAGLIDER)

        val result = repository.importProfiles(
            ProfileImportRequest(
                profiles = listOf(firstImported, secondImported),
                keepCurrentActive = false
            )
        ).getOrThrow()

        val active = repository.activeProfile.value
        assertNotNull(active)
        assertEquals("Imported Two", active?.name)
        assertEquals(active?.id, result.activeProfileAfter)
    }

    @Test
    fun importProfiles_appliesDeterministicNameCollisionSuffixPolicy() = runTest {
        repository.createProfile(
            ProfileCreationRequest(
                name = "Pilot",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()

        repository.importProfiles(
            ProfileImportRequest(
                profiles = listOf(
                    UserProfile(name = "Pilot", aircraftType = AircraftType.GLIDER),
                    UserProfile(name = "Pilot", aircraftType = AircraftType.PARAGLIDER)
                )
            )
        ).getOrThrow()

        val names = repository.profiles.value.map { it.name }
        assertTrue(names.contains("Pilot"))
        assertTrue(names.contains("Pilot (Imported)"))
        assertTrue(names.contains("Pilot (Imported 2)"))
    }

    @Test
    fun importProfiles_withAllInvalidItems_isNoOp() = runTest {
        val existing = repository.createProfile(
            ProfileCreationRequest(
                name = "Baseline",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()
        repository.setActiveProfile(existing).getOrThrow()
        val baselineStateWrites = writeStateCalls
        val baselineActiveId = repository.activeProfile.value?.id
        val baselineProfiles = repository.profiles.value

        val result = repository.importProfiles(
            ProfileImportRequest(
                profiles = listOf(
                    UserProfile(name = " ", aircraftType = AircraftType.GLIDER),
                    UserProfile(name = "\t", aircraftType = AircraftType.PARAGLIDER)
                )
            )
        ).getOrThrow()

        assertEquals(0, result.importedCount)
        assertEquals(2, result.skippedCount)
        assertEquals(2, result.failures.size)
        assertEquals(baselineStateWrites, writeStateCalls)
        assertEquals(baselineActiveId, repository.activeProfile.value?.id)
        assertEquals(baselineProfiles, repository.profiles.value)
    }

    @Test
    fun importProfiles_preserveImportedPreferencesFalse_appliesDefaults() = runTest {
        val imported = UserProfile(
            name = "Defaults Import",
            aircraftType = AircraftType.GLIDER,
            preferences = ProfilePreferences(
                units = UnitSystem.IMPERIAL,
                autoSwitchModes = false,
                cardAnimations = false
            ),
            createdAt = 1234L,
            lastUsed = 5678L
        )

        repository.importProfiles(
            ProfileImportRequest(
                profiles = listOf(imported),
                preserveImportedPreferences = false
            )
        ).getOrThrow()

        val stored = repository.profiles.value.first { it.name == "Defaults Import" }
        assertEquals(UnitSystem.METRIC, stored.preferences.units)
        assertTrue(stored.preferences.autoSwitchModes)
        assertTrue(stored.preferences.cardAnimations)
        assertFalse(stored.createdAt == 1234L)
        assertEquals(0L, stored.lastUsed)
    }

    @Test
    fun importProfiles_duplicateIdsAreRegeneratedWithoutFailure() = runTest {
        val duplicateId = "duplicate-id"
        val first = UserProfile(
            id = duplicateId,
            name = "Duplicate One",
            aircraftType = AircraftType.SAILPLANE
        )
        val second = UserProfile(
            id = duplicateId,
            name = "Duplicate Two",
            aircraftType = AircraftType.GLIDER
        )

        val result = repository.importProfiles(
            ProfileImportRequest(profiles = listOf(first, second))
        ).getOrThrow()

        val stored = repository.profiles.value.filter {
            it.name == "Duplicate One" || it.name == "Duplicate Two"
        }
        assertEquals(2, result.importedCount)
        assertEquals(0, result.failures.size)
        assertEquals(2, stored.size)
        assertTrue(stored.map { it.id }.toSet().size == 2)
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
