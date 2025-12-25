package com.example.xcpro.replay

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcParserTest {

    @Test
    fun parse_rolls_date_forward_across_midnight() {
        val igc = """
            HFDTE071225
            B2359593745123N12230123EA0123401234
            B0000013745123N12230123EA0123401234
        """.trimIndent()

        val log = IgcParser.parse(ByteArrayInputStream(igc.toByteArray()))

        assertEquals(2, log.points.size)
        val first = log.points[0].timestampMillis
        val second = log.points[1].timestampMillis
        assertTrue("Expected monotonic timestamps, got $first then $second", second > first)
        assertEquals(2_000L, second - first)
    }
}

