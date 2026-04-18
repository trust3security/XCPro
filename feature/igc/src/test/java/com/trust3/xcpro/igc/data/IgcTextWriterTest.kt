package com.trust3.xcpro.igc.data

import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.igc.domain.IgcRecordFormatter
import com.trust3.xcpro.replay.IgcParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcTextWriterTest {

    private val writer = IgcTextWriter()
    private val formatter = IgcRecordFormatter()

    @Test
    fun writeLines_writesCanonicalCrlfOnly() {
        val lines = listOf(
            "AXCP00A1B2",
            "HFDTEDATE:090326,01",
            "B1234563746494N12225164WA0012300145"
        )

        val bytes = writer.toByteArray(lines)

        assertTrue("Expected trailing CRLF", endsWithCrlf(bytes))
        assertFalse("Expected no bare LF", hasBareLf(bytes))
        assertFalse("Expected no bare CR", hasBareCr(bytes))
        assertEquals(3, countCrlf(bytes))
    }

    @Test
    fun writeLines_isDeterministicAcrossRepeatedWrites() {
        val lines = listOf(
            "AXCP00A1B2",
            "HFDTEDATE:090326,01",
            "B1234563746494N12225164WA0012300145"
        )

        val first = writer.toByteArray(lines)
        val second = writer.toByteArray(lines)

        assertArrayEquals(first, second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun writeLines_rejectsEmbeddedNewline() {
        val output = ByteArrayOutputStream()
        writer.writeLines(
            output = output,
            lines = listOf("HFPLTPILOTINCHARGE:DOE\nJOHN")
        )
    }

    @Test
    fun writerRoundTrip_formatterToWriterToParser_preservesIasTas() {
        val lines = listOf(
            formatter.formatH(
                IgcRecordFormatter.HeaderRecord(
                    source = 'F',
                    code = "DTE",
                    longName = "DATE",
                    value = "090326,01"
                )
            ),
            formatter.formatI(IgcRecordFormatter.IAS_TAS_EXTENSIONS),
            formatter.formatB(
                record = IgcRecordFormatter.BRecord(
                    timeUtc = LocalTime.of(12, 0, 0),
                    latitudeDegrees = 37.75205,
                    longitudeDegrees = -122.50205,
                    fixValidity = IgcRecordFormatter.FixValidity.A,
                    pressureAltitudeMeters = 1234,
                    gnssAltitudeMeters = 1456,
                    extensionValues = mapOf("IAS" to 123, "TAS" to 145)
                ),
                definitions = IgcRecordFormatter.IAS_TAS_EXTENSIONS
            )
        )

        val bytes = writer.toByteArray(lines)
        val parser = IgcParser(FakeClock(wallMs = 0L))
        val log = parser.parse(ByteArrayInputStream(bytes))

        assertEquals(1, log.points.size)
        val point = log.points.first()
        assertEquals(123.0, point.indicatedAirspeedKmh ?: Double.NaN, 0.001)
        assertEquals(145.0, point.trueAirspeedKmh ?: Double.NaN, 0.001)
    }

    @Test
    fun writerRoundTrip_preservesCoordinatesAltitudesAndTimeWithinTolerance() {
        val lines = listOf(
            formatter.formatH(
                IgcRecordFormatter.HeaderRecord(
                    source = 'F',
                    code = "DTE",
                    longName = "DATE",
                    value = "090326,01"
                )
            ),
            formatter.formatI(IgcRecordFormatter.IAS_TAS_EXTENSIONS),
            formatter.formatB(
                record = IgcRecordFormatter.BRecord(
                    timeUtc = LocalTime.of(12, 0, 0),
                    latitudeDegrees = 37.75205,
                    longitudeDegrees = -122.50205,
                    fixValidity = IgcRecordFormatter.FixValidity.A,
                    pressureAltitudeMeters = 1234,
                    gnssAltitudeMeters = 1456,
                    extensionValues = mapOf("IAS" to 123, "TAS" to 145)
                ),
                definitions = IgcRecordFormatter.IAS_TAS_EXTENSIONS
            ),
            formatter.formatB(
                record = IgcRecordFormatter.BRecord(
                    timeUtc = LocalTime.of(12, 0, 5),
                    latitudeDegrees = 37.75275,
                    longitudeDegrees = -122.50125,
                    fixValidity = IgcRecordFormatter.FixValidity.A,
                    pressureAltitudeMeters = 1240,
                    gnssAltitudeMeters = 1462,
                    extensionValues = mapOf("IAS" to 124, "TAS" to 146)
                ),
                definitions = IgcRecordFormatter.IAS_TAS_EXTENSIONS
            )
        )

        val bytes = writer.toByteArray(lines)
        val parser = IgcParser(FakeClock(wallMs = 0L))
        val log = parser.parse(ByteArrayInputStream(bytes))

        assertEquals(2, log.points.size)
        val first = log.points[0]
        val second = log.points[1]
        assertEquals(5_000L, second.timestampMillis - first.timestampMillis)
        assertEquals(37.75205, first.latitude, 0.0002)
        assertEquals(-122.50205, first.longitude, 0.0002)
        assertEquals(1456.0, first.gpsAltitude, 0.001)
        assertEquals(1234.0, first.pressureAltitude ?: Double.NaN, 0.001)
        assertEquals(37.75275, second.latitude, 0.0002)
        assertEquals(-122.50125, second.longitude, 0.0002)
        assertEquals(1462.0, second.gpsAltitude, 0.001)
        assertEquals(1240.0, second.pressureAltitude ?: Double.NaN, 0.001)
    }

    @Test
    fun writeLines_largeFileStress_hasNoBareLineEndings_andParses() {
        val headers = listOf(
            "HFDTEDATE:090326,01"
        )
        val bLine = "B1200003746494N12225164WA0012300145"
        val lines = buildList {
            addAll(headers)
            repeat(10_000) { add(bLine) }
        }

        val bytes = writer.toByteArray(lines)
        assertTrue("Expected trailing CRLF", endsWithCrlf(bytes))
        assertFalse("Expected no bare LF", hasBareLf(bytes))
        assertFalse("Expected no bare CR", hasBareCr(bytes))
        assertEquals(10_001, countCrlf(bytes))

        val parser = IgcParser(FakeClock(wallMs = 0L))
        val log = parser.parse(ByteArrayInputStream(bytes))
        assertEquals(10_000, log.points.size)
    }

    private fun countCrlf(bytes: ByteArray): Int {
        var count = 0
        var i = 0
        while (i < bytes.size - 1) {
            if (bytes[i] == '\r'.code.toByte() && bytes[i + 1] == '\n'.code.toByte()) {
                count += 1
                i += 2
            } else {
                i += 1
            }
        }
        return count
    }

    private fun hasBareLf(bytes: ByteArray): Boolean {
        for (i in bytes.indices) {
            if (bytes[i] == '\n'.code.toByte()) {
                val previousIsCr = i > 0 && bytes[i - 1] == '\r'.code.toByte()
                if (!previousIsCr) return true
            }
        }
        return false
    }

    private fun hasBareCr(bytes: ByteArray): Boolean {
        for (i in bytes.indices) {
            if (bytes[i] == '\r'.code.toByte()) {
                val nextIsLf = i < bytes.lastIndex && bytes[i + 1] == '\n'.code.toByte()
                if (!nextIsLf) return true
            }
        }
        return false
    }

    private fun endsWithCrlf(bytes: ByteArray): Boolean {
        if (bytes.size < 2) return false
        return bytes[bytes.lastIndex - 1] == '\r'.code.toByte() &&
            bytes[bytes.lastIndex] == '\n'.code.toByte()
    }
}
