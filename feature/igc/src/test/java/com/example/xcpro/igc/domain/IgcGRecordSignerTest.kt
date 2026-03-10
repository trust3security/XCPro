package com.example.xcpro.igc.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class IgcGRecordSignerTest {

    private val signer = IgcGRecordSigner()

    @Test
    fun signatureLines_matchesReferenceFixture_64a() {
        assertSignatureFixture(
            lines = listOf(
                "AXCSAAA",
                "HFFTYFRTYPE:XCSOAR,XCSOAR Linux 6.4.6 Jan 23 2013"
            ),
            expectedSignatureLines = listOf(
                "G0e84a84cab101a9f",
                "G65598e0478043380",
                "G4df6de454dcbcf08",
                "Gd727b9cf71f32ea0",
                "Gb16a9d57f016942d",
                "G2f6b868eaca5d173",
                "Gc8c2a9484dba41f4",
                "G3e5bb4f1e839ec95"
            )
        )
    }

    @Test
    fun signatureLines_matchesReferenceFixture_64b() {
        assertSignatureFixture(
            lines = listOf(
                "AXCSAAA",
                "HFFTYFRTYPE:XCSOAR XCSOAR Linux 6.4.6 Jan 23 2013"
            ),
            expectedSignatureLines = listOf(
                "Gd3aa0cfda5e30cd0",
                "G337ac95cc4f228ed",
                "Ga840bb6f125814ca",
                "G8f31112e82272b13",
                "G8db66b0968c2224d",
                "G7ef748203b03ccea",
                "G962a36f360dff706",
                "G24a1dd0ca7fca316"
            )
        )
    }

    @Test
    fun signatureLines_matchesReferenceFixture_65a() {
        assertSignatureFixture(
            lines = listOf(
                "AXCSAAA",
                "HFFTYFRTYPE:XCSOAR,XCSOAR Android 6.5 Mar  8 2013"
            ),
            expectedSignatureLines = listOf(
                "Gc0a3f8ffad5ed663",
                "G680242ec7b5da911",
                "Gc78a96b36bd1c70c",
                "G91e28911d970d8d7",
                "Gb9b1cbb1ef95114b",
                "G42fcefa5896c0797",
                "G9883313f5de2338e",
                "G98b94a12a08e8c66"
            )
        )
    }

    @Test
    fun signatureLines_matchesReferenceFixture_65b() {
        assertSignatureFixture(
            lines = listOf(
                "AXCSAAA",
                "HFFTYFRTYPE:XCSOAR XCSOAR Android 6.5 Mar  8 2013"
            ),
            expectedSignatureLines = listOf(
                "G4344ba0741b42afa",
                "G07675a8294b1b1be",
                "G119050de14479fb1",
                "G244ac2478ff39a88",
                "Gfe1acfdf51d5a897",
                "Gde736824f2d4cfad",
                "G5b0a3a1f372f9a13",
                "G3d96c0e61d181030"
            )
        )
    }

    @Test
    fun sign_replacesExistingGRecordsWithoutDuplicatingSignatureBlock() {
        val unsignedLines = listOf(
            "AXCSAAA",
            "HFFTYFRTYPE:XCSOAR XCSOAR Android 6.5 Mar  8 2013"
        )
        val signed = signer.sign(
            lines = unsignedLines,
            profile = IgcSecuritySignatureProfile.XCS
        )

        val reSigned = signer.sign(
            lines = signed,
            profile = IgcSecuritySignatureProfile.XCS
        )

        assertEquals(signed, reSigned)
        assertEquals(10, reSigned.size)
    }

    private fun assertSignatureFixture(
        lines: List<String>,
        expectedSignatureLines: List<String>
    ) {
        assertEquals(
            expectedSignatureLines,
            signer.signatureLines(lines, IgcSecuritySignatureProfile.XCS)
        )
        assertEquals(
            lines + expectedSignatureLines,
            signer.sign(lines, IgcSecuritySignatureProfile.XCS)
        )
    }
}
