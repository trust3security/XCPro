package com.example.xcpro.weglide.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolveWeGlideAircraftForProfileUseCaseTest {

    private val repository = FakeRepository()
    private val useCase = ResolveWeGlideAircraftForProfileUseCase(repository)

    @Test
    fun returnsMappingMissingWhenProfileHasNoRemoteAircraft() {
        val result = runSuspend { useCase("profile-a") }

        assertEquals(
            WeGlideAircraftMappingResolution.Status.MAPPING_MISSING,
            result.status
        )
        assertNull(result.mapping)
        assertNull(result.aircraft)
    }

    @Test
    fun returnsAircraftMissingWhenMappingExistsButAircraftCacheDoesNot() {
        repository.mappingByProfile["profile-a"] = WeGlideAircraftMapping(
            localProfileId = "profile-a",
            weglideAircraftId = 44L,
            weglideAircraftName = "JS1C 18m",
            updatedAtEpochMs = 10L
        )

        val result = runSuspend { useCase("profile-a") }

        assertEquals(
            WeGlideAircraftMappingResolution.Status.AIRCRAFT_MISSING,
            result.status
        )
        assertEquals(44L, result.mapping?.weglideAircraftId)
        assertNull(result.aircraft)
    }

    @Test
    fun returnsMappedWhenMappingAndAircraftExist() {
        repository.mappingByProfile["profile-a"] = WeGlideAircraftMapping(
            localProfileId = "profile-a",
            weglideAircraftId = 44L,
            weglideAircraftName = "JS1C 18m",
            updatedAtEpochMs = 10L
        )
        repository.aircraftById[44L] = WeGlideAircraft(
            aircraftId = 44L,
            name = "JS1C 18m",
            kind = "sailplane",
            scoringClass = "18m"
        )

        val result = runSuspend { useCase("profile-a") }

        assertEquals(WeGlideAircraftMappingResolution.Status.MAPPED, result.status)
        assertEquals("profile-a", result.profileId)
        assertEquals(44L, result.mapping?.weglideAircraftId)
        assertEquals("JS1C 18m", result.aircraft?.name)
    }

    private class FakeRepository : WeGlideAircraftMappingReadRepository {
        val mappingByProfile = LinkedHashMap<String, WeGlideAircraftMapping>()
        val aircraftById = LinkedHashMap<Long, WeGlideAircraft>()

        override suspend fun getMapping(profileId: String): WeGlideAircraftMapping? {
            return mappingByProfile[profileId]
        }

        override suspend fun getAircraftById(aircraftId: Long): WeGlideAircraft? {
            return aircraftById[aircraftId]
        }
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    return kotlinx.coroutines.runBlocking { block() }
}
