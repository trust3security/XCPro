package com.example.xcpro.profiles

import com.example.xcpro.FlightMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryTest {

    private val profilesFlow = MutableStateFlow<String?>(null)
    private val activeIdFlow = MutableStateFlow<String?>(null)

    private val storage = object : ProfileStorage {
        override val profilesJsonFlow = profilesFlow
        override val activeProfileIdFlow = activeIdFlow
        override suspend fun writeProfilesJson(json: String?) {
            profilesFlow.value = json
        }
        override suspend fun writeActiveProfileId(id: String?) {
            activeIdFlow.value = id
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
        assertTrue(created.flightTemplateIds.isNotEmpty())

        repository.setActiveProfile(created).getOrThrow()

        val active = repository.activeProfile.first()
        assertNotNull(active)
        assertEquals(created.id, active?.id)

        val configuration = repository.getCurrentProfileCardConfiguration(FlightMode.CRUISE)
        assertTrue(configuration.isNotEmpty())
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
}
