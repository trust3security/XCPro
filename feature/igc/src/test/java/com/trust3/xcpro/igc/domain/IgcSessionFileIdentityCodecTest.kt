package com.trust3.xcpro.igc.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcSessionFileIdentityCodecTest {

    @Test
    fun resolveUtcDate_prefersFirstValidFix() {
        val resolved = IgcSessionFileIdentityCodec.resolveUtcDate(
            firstValidFixWallTimeMs = 1_741_564_802_000L,
            sessionStartWallTimeMs = 1_741_483_200_000L
        )

        assertEquals(LocalDate.of(2025, 3, 10), resolved.utcDate)
        assertFalse(resolved.usedFallbackDate)
    }

    @Test
    fun buildSessionPrefix_normalizesManufacturerAndSerial() {
        val prefix = IgcSessionFileIdentityCodec.buildSessionPrefix(
            utcDate = LocalDate.of(2025, 3, 9),
            manufacturerId = " xcp ",
            sessionSerial = "42"
        )

        assertEquals("2025-03-09-XCP-000042", prefix)
    }

    @Test
    fun buildSessionPrefix_usesDefaultPlaceholdersWhenValuesAreBlank() {
        val prefix = IgcSessionFileIdentityCodec.buildSessionPrefix(
            utcDate = LocalDate.of(2025, 3, 9),
            manufacturerId = "!",
            sessionSerial = "abc"
        )

        assertTrue(prefix.endsWith("-XCP-000000"))
    }
}
