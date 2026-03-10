package com.example.xcpro.replay

import com.example.xcpro.core.time.FakeClock
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

        val parser = IgcParser(FakeClock(wallMs = 0L))
        val log = parser.parse(ByteArrayInputStream(igc.toByteArray()))

        assertEquals(2, log.points.size)
        val first = log.points[0].timestampMillis
        val second = log.points[1].timestampMillis
        assertTrue("Expected monotonic timestamps, got $first then $second", second > first)
        assertEquals(2_000L, second - first)
    }

    @Test
    fun parse_reads_ias_tas_extensions() {
        val igc = """
            HFDTE071225
            I023638IAS3941TAS
            B1200003745123N12230123EA0123401234123145
        """.trimIndent()

        val parser = IgcParser(FakeClock(wallMs = 0L))
        val log = parser.parse(ByteArrayInputStream(igc.toByteArray()))

        assertEquals(1, log.points.size)
        val point = log.points.first()
        assertEquals(123.0, point.indicatedAirspeedKmh ?: Double.NaN, 0.001)
        assertEquals(145.0, point.trueAirspeedKmh ?: Double.NaN, 0.001)
    }

    @Test
    fun parse_rejectsIExtensionsBelowWriterFloor() {
        val igc = """
            HFDTE071225
            I020838IAS3941TAS
            B1200003745123N12230123EA0123401234123145
        """.trimIndent()

        val parser = IgcParser(FakeClock(wallMs = 0L))
        val log = parser.parse(ByteArrayInputStream(igc.toByteArray()))

        assertEquals(1, log.points.size)
        val point = log.points.first()
        assertEquals(null, point.indicatedAirspeedKmh)
        assertEquals(null, point.trueAirspeedKmh)
    }
}
