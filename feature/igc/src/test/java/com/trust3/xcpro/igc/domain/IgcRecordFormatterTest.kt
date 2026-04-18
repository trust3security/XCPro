package com.trust3.xcpro.igc.domain

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcRecordFormatterTest {

    private val formatter = IgcRecordFormatter()

    @Test
    fun formatA_buildsExpectedRecord() {
        val line = formatter.formatA(
            manufacturerId = "xcp",
            serialId = "00A1B2"
        )

        assertEquals("AXCP00A1B2", line)
    }

    @Test
    fun formatH_buildsExpectedHeaderLine() {
        val line = formatter.formatH(
            IgcRecordFormatter.HeaderRecord(
                source = 'F',
                code = "DTE",
                longName = "DATE",
                value = "080326,01"
            )
        )

        assertEquals("HFDTEDATE:080326,01", line)
    }

    @Test
    fun formatI_iasTasMapping_isDeterministic() {
        val line = formatter.formatI(IgcRecordFormatter.IAS_TAS_EXTENSIONS)

        assertEquals("I023638IAS3941TAS", line)
    }

    @Test
    fun formatB_withoutExtensions_is35Chars() {
        val line = formatter.formatB(
            IgcRecordFormatter.BRecord(
                timeUtc = LocalTime.of(12, 34, 56),
                latitudeDegrees = 37.7749,
                longitudeDegrees = -122.4194,
                fixValidity = IgcRecordFormatter.FixValidity.A,
                pressureAltitudeMeters = 123,
                gnssAltitudeMeters = 145
            )
        )

        assertEquals(IgcRecordFormatter.BASE_B_RECORD_LENGTH, line.length)
        assertTrue(line.startsWith("B123456"))
    }

    @Test
    fun formatB_withIasTasExtensions_appendsAtExpectedPositions() {
        val line = formatter.formatB(
            record = IgcRecordFormatter.BRecord(
                timeUtc = LocalTime.of(12, 0, 0),
                latitudeDegrees = 37.75205,
                longitudeDegrees = -122.50205,
                fixValidity = IgcRecordFormatter.FixValidity.A,
                pressureAltitudeMeters = 1234,
                gnssAltitudeMeters = 1456,
                extensionValues = mapOf(
                    "IAS" to 123,
                    "TAS" to 145
                )
            ),
            definitions = IgcRecordFormatter.IAS_TAS_EXTENSIONS
        )

        assertEquals("123", line.substring(35, 38))
        assertEquals("145", line.substring(38, 41))
        assertEquals(41, line.length)
    }

    @Test
    fun decimalDegreesToIgcLatitude_handlesRoundingCarry() {
        // 12 deg + 34.9995 min -> 12 deg 35.000 min after carry.
        val latitude = 12.583325
        val encoded = formatter.decimalDegreesToIgcLatitude(latitude)

        assertEquals("1235000N", encoded)
    }

    @Test
    fun decimalDegreesToIgcLongitude_handlesRoundingCarry() {
        // 123 deg + 59.9995 min -> 124 deg 00.000 min after carry.
        val longitude = 123.99999166666667
        val encoded = formatter.decimalDegreesToIgcLongitude(longitude)

        assertEquals("12400000E", encoded)
    }

    @Test
    fun formatAltitudeMeters5_formatsNegativeAltitude() {
        assertEquals("-0010", formatter.formatAltitudeMeters5(-10))
        assertEquals("-0123", formatter.formatAltitudeMeters5(-123))
    }

    @Test
    fun formatLinesCrlf_usesCanonicalLineEndings() {
        val content = formatter.formatLinesCrlf(
            listOf(
                "AXCP00A1B2",
                "HFDTEDATE:080326,01",
                "B1234563746494N12225164WA0012300145"
            )
        )

        assertTrue(content.contains(IgcRecordFormatter.CRLF))
        assertTrue(content.endsWith(IgcRecordFormatter.CRLF))
        assertEquals(3, content.split(IgcRecordFormatter.CRLF).filter { it.isNotEmpty() }.size)
    }
}
