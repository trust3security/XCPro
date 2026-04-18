package com.trust3.xcpro.profiles

import com.trust3.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryIdentityTimeOwnershipTest {

    @Test
    fun createProfile_usesInjectedClockAndIdGeneratorForOwnerMetadata() = runTest {
        val harness = createScopedProfileRepositoryTestHarness(
            scope = backgroundScope,
            clock = FakeClock(wallMs = 123_456L),
            profileIdGenerator = ProfileIdGenerator.fixed("generated-profile-id")
        )
        harness.repository.bootstrapComplete.first { it }

        val created = harness.repository.createProfile(
            ProfileCreationRequest(
                name = "Owned Metadata",
                aircraftType = AircraftType.GLIDER
            )
        ).getOrThrow()

        assertEquals("generated-profile-id", created.id)
        assertEquals(123_456L, created.createdAt)
        assertEquals(123_456L, created.lastUsed)
    }

    @Test
    fun completeFirstLaunch_usesInjectedClockForDefaultProfileMetadata() = runTest {
        val harness = createScopedProfileRepositoryTestHarness(
            scope = backgroundScope,
            clock = FakeClock(wallMs = 456_789L)
        )

        harness.repository.bootstrapComplete.first { it }

        val defaultProfile = harness.repository.completeFirstLaunch(AircraftType.SAILPLANE).getOrThrow()
        assertEquals(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID, defaultProfile.id)
        assertEquals(AircraftType.SAILPLANE, defaultProfile.aircraftType)
        assertEquals(456_789L, defaultProfile.createdAt)
        assertEquals(456_789L, defaultProfile.lastUsed)
    }
}
