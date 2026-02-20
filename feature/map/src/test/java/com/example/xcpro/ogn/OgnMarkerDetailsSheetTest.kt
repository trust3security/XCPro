package com.example.xcpro.ogn

import org.junit.Assert.assertEquals
import org.junit.Test

class OgnMarkerDetailsSheetTest {

    @Test
    fun sanitizeOgnRawText_returnsFallbackForNullOrBlank() {
        assertEquals("--", sanitizeOgnRawText(null, maxLen = 10))
        assertEquals("--", sanitizeOgnRawText("   ", maxLen = 10))
    }

    @Test
    fun sanitizeOgnRawText_replacesControlCharacters() {
        val raw = "abc\tdef\n\u0001"
        val sanitized = sanitizeOgnRawText(raw, maxLen = 32)
        assertEquals("abc def ?", sanitized)
    }

    @Test
    fun sanitizeOgnRawText_truncatesAtMaxLength() {
        val raw = "ABCDEFGHIJKL"
        val sanitized = sanitizeOgnRawText(raw, maxLen = 8)
        assertEquals("ABCDEFGH...", sanitized)
    }
}
