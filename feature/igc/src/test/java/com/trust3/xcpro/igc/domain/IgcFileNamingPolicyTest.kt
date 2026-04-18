package com.trust3.xcpro.igc.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcFileNamingPolicyTest {

    private val policy = IgcFileNamingPolicy()

    @Test
    fun resolve_usesFirstValidFixUtcDateAndLowestFreeIndex() {
        val result = policy.resolve(
            IgcFileNamingPolicy.Request(
                firstValidFixWallTimeMs = 1_741_564_802_000L,
                sessionStartWallTimeMs = 1_741_564_798_000L,
                manufacturerId = "xcp",
                sessionSerial = "42",
                existingFileNames = setOf(
                    "2025-03-10-XCP-000042-01.IGC",
                    "2025-03-10-XCP-000042-03.IGC"
                )
            )
        )
        assertTrue(result is IgcFileNamingPolicy.Result.Success)
        result as IgcFileNamingPolicy.Result.Success
        assertEquals("2025-03-10-XCP-000042-02.IGC", result.fileName)
        assertEquals(2, result.dayFlightIndex)
        assertEquals(false, result.usedFallbackDate)
    }

    @Test
    fun resolve_fallsBackToSessionStartDateWhenNoValidFix() {
        val result = policy.resolve(
            IgcFileNamingPolicy.Request(
                firstValidFixWallTimeMs = null,
                sessionStartWallTimeMs = 1_741_483_200_000L,
                manufacturerId = "XCP",
                sessionSerial = "000777",
                existingFileNames = emptySet()
            )
        )
        assertTrue(result is IgcFileNamingPolicy.Result.Success)
        result as IgcFileNamingPolicy.Result.Success
        assertEquals("2025-03-09-XCP-000777-01.IGC", result.fileName)
        assertEquals(true, result.usedFallbackDate)
    }

    @Test
    fun resolve_returnsFailureWhenDayIndexSpaceIsExhausted() {
        val existing = (1..99).map { index ->
            "2025-03-10-XCP-000001-${index.toString().padStart(2, '0')}.IGC"
        }.toSet()
        val result = policy.resolve(
            IgcFileNamingPolicy.Request(
                firstValidFixWallTimeMs = 1_741_564_802_000L,
                sessionStartWallTimeMs = 1_741_564_798_000L,
                manufacturerId = "XCP",
                sessionSerial = "1",
                existingFileNames = existing
            )
        )
        assertTrue(result is IgcFileNamingPolicy.Result.Failure)
        result as IgcFileNamingPolicy.Result.Failure
        assertEquals(IgcFileNamingPolicy.FailureCode.NAME_SPACE_EXHAUSTED, result.code)
    }
}
