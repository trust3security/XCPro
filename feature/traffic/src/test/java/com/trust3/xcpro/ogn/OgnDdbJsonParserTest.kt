package com.trust3.xcpro.ogn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OgnDdbJsonParserTest {

    @Test
    fun parse_parsesMinimalDevicesPayload() {
        val json = """
            {
              "devices": [
                {
                  "device_type": "F",
                  "device_id": "0123BC",
                  "aircraft_model": "LS-4",
                  "registration": "X-0123",
                  "cn": "23",
                  "tracked": "Y",
                  "identified": "Y",
                  "aircraft_type": "1"
                }
              ]
            }
        """.trimIndent()

        val parsed = OgnDdbJsonParser.parse(json)
        val entry = parsed.singleOrNull()
        val identity = entry?.identity

        assertNotNull(entry)
        assertEquals(OgnAddressType.FLARM, entry?.addressType)
        assertEquals("0123BC", entry?.deviceIdHex)
        assertEquals("X-0123", identity?.registration)
        assertEquals("23", identity?.competitionNumber)
        assertEquals("LS-4", identity?.aircraftModel)
        assertTrue(identity?.tracked == true)
        assertTrue(identity?.identified == true)
        assertEquals(1, identity?.aircraftTypeCode)
    }

    @Test
    fun parse_readsPrivacyFlags() {
        val json = """
            {
              "devices": [
                {
                  "device_id": "ABC123",
                  "tracked": "N",
                  "identified": "N"
                }
              ]
            }
        """.trimIndent()

        val parsed = OgnDdbJsonParser.parse(json)
        val entry = parsed.singleOrNull()
        val identity = entry?.identity

        assertNotNull(entry)
        assertEquals(OgnAddressType.UNKNOWN, entry?.addressType)
        assertEquals("ABC123", entry?.deviceIdHex)
        assertFalse(identity?.tracked == true)
        assertFalse(identity?.identified == true)
    }
}
