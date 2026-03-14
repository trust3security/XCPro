package com.example.xcpro.profiles

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryMutationTest {

    private val harness = createProfileRepositoryTestHarness()
    private val repository
        get() = harness.repository
    private val writeProfilesCalls
        get() = harness.writeProfilesCalls
    private val writeActiveCalls
        get() = harness.writeActiveCalls
    private val writeStateCalls
        get() = harness.writeStateCalls

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
    fun updateProfile_keepsNonAuthoritativeCompatibilityFieldsUnchanged() = runTest {
        val imported = UserProfile(
            id = "compatibility-baseline",
            name = "Compatibility Baseline",
            aircraftType = AircraftType.SAILPLANE,
            preferences = ProfilePreferences(
                units = UnitSystem.IMPERIAL,
                autoSwitchModes = false,
                cardAnimations = false
            ),
            createdAt = 1_000L,
            lastUsed = 2_000L,
            polar = ProfilePolarSettings(
                lowSpeedKmh = 85.0,
                lowSinkMs = 0.55,
                midSpeedKmh = 115.0,
                midSinkMs = 0.78,
                highSpeedKmh = 175.0,
                highSinkMs = 1.95
            )
        )
        repository.importProfiles(ProfileImportRequest(profiles = listOf(imported))).getOrThrow()
        val created = repository.profiles.value.first { it.name == "Compatibility Baseline" }

        val updated = created.copy(
            name = "Compatibility Updated",
            preferences = ProfilePreferences(),
            polar = ProfilePolarSettings()
        )
        repository.updateProfile(updated).getOrThrow()

        val stored = repository.profiles.value.first { it.id == created.id }
        assertEquals("Compatibility Updated", stored.name)
        assertEquals(UnitSystem.IMPERIAL, stored.preferences.units)
        assertEquals(false, stored.preferences.autoSwitchModes)
        assertEquals(false, stored.preferences.cardAnimations)
        assertEquals(85.0, stored.polar.lowSpeedKmh, 0.0)
        assertEquals(0.55, stored.polar.lowSinkMs, 0.0)
        assertEquals(115.0, stored.polar.midSpeedKmh, 0.0)
        assertEquals(0.78, stored.polar.midSinkMs, 0.0)
        assertEquals(175.0, stored.polar.highSpeedKmh, 0.0)
        assertEquals(1.95, stored.polar.highSinkMs, 0.0)
    }

    @Test
    fun createProfile_copyFromProfile_copiesCompatibilityFieldsAndOptionalMetadata() = runTest {
        val source = repository.importProfiles(
            ProfileImportRequest(
                profiles = listOf(
                    UserProfile(
                        id = "source-profile",
                        name = "Source",
                        aircraftType = AircraftType.SAILPLANE,
                        aircraftModel = "JS3",
                        description = "Source profile",
                        preferences = ProfilePreferences(
                            units = UnitSystem.IMPERIAL,
                            autoSwitchModes = false,
                            cardAnimations = false
                        ),
                        createdAt = 1_000L,
                        lastUsed = 2_000L,
                        polar = ProfilePolarSettings(
                            lowSpeedKmh = 90.0,
                            lowSinkMs = 0.6,
                            midSpeedKmh = 120.0,
                            midSinkMs = 0.8,
                            highSpeedKmh = 180.0,
                            highSinkMs = 1.9
                        )
                    )
                )
            )
        ).map {
            repository.profiles.value.first { profile -> profile.name == "Source" }
        }.getOrThrow()

        val created = repository.createProfile(
            ProfileCreationRequest(
                name = "Copied",
                aircraftType = AircraftType.GLIDER,
                copyFromProfile = source
            )
        ).getOrThrow()

        assertEquals("Copied", created.name)
        assertEquals(AircraftType.GLIDER, created.aircraftType)
        assertEquals("JS3", created.aircraftModel)
        assertEquals("Source profile", created.description)
        assertEquals(UnitSystem.IMPERIAL, created.preferences.units)
        assertEquals(false, created.preferences.autoSwitchModes)
        assertEquals(false, created.preferences.cardAnimations)
        assertEquals(90.0, created.polar.lowSpeedKmh, 0.0)
        assertEquals(1.9, created.polar.highSinkMs, 0.0)
    }

    @Test
    fun updateProfile_blankNameFailsValidation() = runTest {
        val created = repository.createProfile(
            ProfileCreationRequest(
                name = "Validate Update",
                aircraftType = AircraftType.SAILPLANE
            )
        ).getOrThrow()

        val result = repository.updateProfile(created.copy(name = "   "))
        assertTrue(result.isFailure)
        assertTrue((result.exceptionOrNull()?.message ?: "").contains("cannot be blank"))
    }

    @Test
    fun setActiveProfile_mergesProfileIfMissing() = runTest {
        val orphan = UserProfile(
            id = "orphan-imported",
            name = "Imported",
            aircraftType = AircraftType.GLIDER,
            createdAt = 1_000L,
            lastUsed = 2_000L
        )

        repository.setActiveProfile(orphan).getOrThrow()

        val profiles = repository.profiles.first()
        assertTrue(profiles.any { it.id == orphan.id })
        val active = repository.activeProfile.first()
        assertEquals(orphan.id, active?.id)
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
    fun deleteProfile_cannotDeleteProvisionedDefaultProfile() = runTest {
        val scopedHarness = createScopedProfileRepositoryTestHarness(backgroundScope)
        val defaultProfile = scopedHarness.repository.profiles.first { it.isNotEmpty() }.first()
        val second = scopedHarness.repository.createProfile(
            ProfileCreationRequest(
                name = "Pilot D",
                aircraftType = AircraftType.GLIDER
            )
        ).getOrThrow()
        scopedHarness.repository.setActiveProfile(second).getOrThrow()

        val deletion = scopedHarness.repository.deleteProfile(defaultProfile.id)
        assertTrue(deletion.isFailure)
        assertTrue((deletion.exceptionOrNull()?.message ?: "").contains("default profile"))
    }
}
