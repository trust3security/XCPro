package com.example.xcpro.ogn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OgnClientCallsignTest {

    @Test
    fun normalize_acceptsAndUppercasesValidCallsign() {
        val normalized = normalizeOgnClientCallsignOrNull(" xcpa1b2c3 ")
        assertTrue(normalized == "XCPA1B2C3")
    }

    @Test
    fun normalize_rejectsInvalidCallsign() {
        assertNull(normalizeOgnClientCallsignOrNull("bad-callsign"))
        assertNull(normalizeOgnClientCallsignOrNull("12ABC"))
        assertNull(normalizeOgnClientCallsignOrNull("AB"))
    }

    @Test
    fun generate_producesValidCallsign() {
        val generated = generateOgnClientCallsign()
        assertNotNull(generated)
        assertTrue(generated.matches(Regex("^[A-Z][A-Z0-9]{2,8}$")))
        assertFalse(generated == OGN_LEGACY_CLIENT_CALLSIGN)
    }
}
