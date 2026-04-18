package com.trust3.xcpro.igc.data

import com.trust3.xcpro.igc.domain.IgcSecuritySignatureProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IgcStagedRecoveryMetadataParserTest {

    @Test
    fun parse_usesDteDateAndFirstBTime() {
        val metadata = IgcStagedRecoveryMetadataParser.parse(
            listOf(
                "AXCP000055",
                "HFDTEDATE:090326,01",
                "B1200003746494N12225164WA0012300145"
            )
        )

        assertNotNull(metadata)
        requireNotNull(metadata)
        assertEquals("XCP", metadata.manufacturerId)
        assertEquals("000055", metadata.sessionSerial)
        assertEquals(1_773_014_400_000L, metadata.sessionStartWallTimeMs)
        assertEquals(1_773_057_600_000L, metadata.firstValidFixWallTimeMs)
    }

    @Test
    fun parse_supportsShortHfdte() {
        val metadata = IgcStagedRecoveryMetadataParser.parse(
            listOf(
                "AXCP000056",
                "HFDTE090326",
                "B1200003746494N12225164WA0012300145"
            )
        )

        assertNotNull(metadata)
        requireNotNull(metadata)
        assertEquals("XCP", metadata.manufacturerId)
        assertEquals("000056", metadata.sessionSerial)
        assertEquals(1_773_014_400_000L, metadata.sessionStartWallTimeMs)
        assertEquals(1_773_057_600_000L, metadata.firstValidFixWallTimeMs)
    }

    @Test
    fun parse_detectsXcsSignatureProfileFromManufacturer() {
        val metadata = IgcStagedRecoveryMetadataParser.parse(
            listOf(
                "AXCS000057",
                "HFDTEDATE:090326,01",
                "B1200003746494N12225164WA0012300145"
            )
        )

        assertNotNull(metadata)
        requireNotNull(metadata)
        assertEquals(IgcSecuritySignatureProfile.XCS, metadata.signatureProfile)
    }
}
