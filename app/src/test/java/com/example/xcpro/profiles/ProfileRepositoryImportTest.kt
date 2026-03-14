package com.example.xcpro.profiles

import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryImportTest {

    private val harness = createProfileRepositoryTestHarness()
    private val repository
        get() = harness.repository
    private val clock
        get() = harness.clock
    private val writeProfilesCalls
        get() = harness.writeProfilesCalls
    private val writeActiveCalls
        get() = harness.writeActiveCalls
    private val writeStateCalls
        get() = harness.writeStateCalls

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
            id = "incoming-imported-pilot",
            name = "Imported Pilot",
            aircraftType = AircraftType.GLIDER,
            createdAt = 1_000L,
            lastUsed = 2_000L
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
            id = "preference-import",
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
            UserProfile(
                id = "import-a",
                name = "Import A",
                aircraftType = AircraftType.SAILPLANE,
                createdAt = 1_000L,
                lastUsed = 2_000L
            ),
            UserProfile(
                id = "import-b",
                name = "Import B",
                aircraftType = AircraftType.GLIDER,
                createdAt = 1_000L,
                lastUsed = 2_000L
            )
        )

        repository.importProfiles(ProfileImportRequest(profiles = imports)).getOrThrow()

        assertEquals(baselineStateWrites + 1, writeStateCalls)
        assertEquals(baselineProfilesWrites, writeProfilesCalls)
        assertEquals(baselineActiveWrites, writeActiveCalls)
    }

    @Test
    fun importProfiles_skipsInvalidEntriesAndReportsFailure() = runTest {
        val imports = listOf(
            UserProfile(
                id = "invalid-blank",
                name = " ",
                aircraftType = AircraftType.SAILPLANE,
                createdAt = 1_000L,
                lastUsed = 2_000L
            ),
            UserProfile(
                id = "valid-import",
                name = "Valid Import",
                aircraftType = AircraftType.GLIDER,
                createdAt = 1_000L,
                lastUsed = 2_000L
            )
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

        val firstImported = UserProfile(
            id = "imported-one",
            name = "Imported One",
            aircraftType = AircraftType.GLIDER,
            createdAt = 1_000L,
            lastUsed = 2_000L
        )
        val secondImported = UserProfile(
            id = "imported-two",
            name = "Imported Two",
            aircraftType = AircraftType.PARAGLIDER,
            createdAt = 1_000L,
            lastUsed = 2_000L
        )

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
                    UserProfile(
                        id = "pilot-import-1",
                        name = "Pilot",
                        aircraftType = AircraftType.GLIDER,
                        createdAt = 1_000L,
                        lastUsed = 2_000L
                    ),
                    UserProfile(
                        id = "pilot-import-2",
                        name = "Pilot",
                        aircraftType = AircraftType.PARAGLIDER,
                        createdAt = 1_000L,
                        lastUsed = 2_000L
                    )
                )
            )
        ).getOrThrow()

        val names = repository.profiles.value.map { it.name }
        val pilotNames = names.filter { it.startsWith("Pilot") }
        assertTrue(pilotNames.contains("Pilot"))
        val importedPilotNames = pilotNames.filter { it.startsWith("Pilot (Imported") }
        assertEquals(2, importedPilotNames.distinct().size)
    }

    @Test
    fun importProfiles_replaceExistingPolicy_reusesExistingIdAndAvoidsDuplicate() = runTest {
        val existing = repository.createProfile(
            ProfileCreationRequest(
                name = "Replace Me",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()
        repository.setActiveProfile(existing).getOrThrow()
        val beforeCount = repository.profiles.value.size

        val incoming = UserProfile(
            id = "incoming-replace-id",
            name = "Replace Me",
            aircraftType = AircraftType.GLIDER,
            description = "Updated",
            createdAt = 1_000L,
            lastUsed = 2_000L
        )

        val result = repository.importProfiles(
            ProfileImportRequest(
                profiles = listOf(incoming),
                nameCollisionPolicy = ProfileNameCollisionPolicy.REPLACE_EXISTING
            )
        ).getOrThrow()

        val afterProfiles = repository.profiles.value
        assertEquals(beforeCount, afterProfiles.size)
        assertEquals(1, afterProfiles.count { it.name == "Replace Me" })
        val replaced = afterProfiles.first { it.name == "Replace Me" }
        assertEquals(existing.id, replaced.id)
        assertEquals(AircraftType.GLIDER, replaced.aircraftType)
        assertEquals(existing.id, result.importedProfileIdMap["incoming-replace-id"])
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
                    UserProfile(
                        id = "invalid-blank-two",
                        name = " ",
                        aircraftType = AircraftType.GLIDER,
                        createdAt = 1_000L,
                        lastUsed = 2_000L
                    ),
                    UserProfile(
                        id = "invalid-tab",
                        name = "\t",
                        aircraftType = AircraftType.PARAGLIDER,
                        createdAt = 1_000L,
                        lastUsed = 2_000L
                    )
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
        clock.setWallMs(90_000L)
        val imported = UserProfile(
            id = "defaults-import",
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
        assertEquals(90_000L, stored.createdAt)
        assertEquals(0L, stored.lastUsed)
    }

    @Test
    fun importProfiles_duplicateIdsAreRegeneratedWithoutFailure() = runTest {
        val duplicateId = "duplicate-id"
        val first = UserProfile(
            id = duplicateId,
            name = "Duplicate One",
            aircraftType = AircraftType.SAILPLANE,
            createdAt = 1_000L,
            lastUsed = 2_000L
        )
        val second = UserProfile(
            id = duplicateId,
            name = "Duplicate Two",
            aircraftType = AircraftType.GLIDER,
            createdAt = 1_000L,
            lastUsed = 2_000L
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
    fun importProfiles_usesInjectedIdGeneratorWhenDuplicateIdNeedsReplacement() = runTest {
        val harness = createScopedProfileRepositoryTestHarness(
            scope = backgroundScope,
            clock = FakeClock(wallMs = 789_000L),
            profileIdGenerator = ProfileIdGenerator.fixed(
                "existing-profile-id",
                "replacement-profile-id"
            )
        )
        harness.repository.bootstrapComplete.first { it }
        val existing = harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Existing",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()

        val incoming = UserProfile(
            id = existing.id,
            name = "Imported Duplicate",
            aircraftType = AircraftType.GLIDER,
            createdAt = 1L,
            lastUsed = 2L
        )

        val result = harness.repository.importProfiles(
            ProfileImportRequest(
                profiles = listOf(incoming),
                preserveImportedPreferences = false
            )
        ).getOrThrow()

        val imported = harness.repository.profiles.value.first { it.name == "Imported Duplicate" }
        assertEquals(1, result.importedCount)
        assertEquals("replacement-profile-id", imported.id)
        assertEquals(789_000L, imported.createdAt)
        assertEquals(0L, imported.lastUsed)
    }
}
