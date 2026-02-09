package com.example.xcpro.ogn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OgnAprsLineParserTest {

    private val parser = OgnAprsLineParser()

    @Test
    fun parseTraffic_parsesAircraftBeacon() {
        val line =
            "FLRDDDEAD>APRS,qAS,EDER:/114500h5029.86N/00956.98E'342/049/A=005524 id0ADDDEAD -454fpm -1.1rot 8.8dB 0e +51.2kHz gps4x5"

        val parsed = parser.parseTraffic(line, receivedAtMillis = 1_700_000_000_000)

        assertNotNull(parsed)
        parsed!!
        assertEquals("FLRDDDEAD", parsed.callsign)
        assertEquals("APRS", parsed.destination)
        assertEquals(50.497666, parsed.latitude, 1e-5)
        assertEquals(9.949666, parsed.longitude, 1e-5)
        assertEquals(1683.7152, parsed.altitudeMeters!!, 1e-3)
        assertEquals(342.0, parsed.trackDegrees!!, 1e-6)
        assertEquals(25.207756, parsed.groundSpeedMps!!, 1e-6)
        assertEquals(-2.30632, parsed.verticalSpeedMps!!, 1e-5)
        assertEquals("DDDEAD", parsed.deviceIdHex)
        assertEquals(8.8, parsed.signalDb!!, 1e-6)
    }

    @Test
    fun parseTraffic_ignoresReceiverToCall() {
        val line =
            "LILH>OGNSDR,TCPIP*,qAC,GLIDERN2:/132201h4457.61NI00900.58E&/A=000423"

        val parsed = parser.parseTraffic(line, receivedAtMillis = 1_700_000_000_000)

        assertNull(parsed)
    }

    @Test
    fun parseTraffic_ignoresStatusMessage() {
        val line =
            "LKHE>OGNSDR,qAS,glidern5:>164507h CpuLoad=0.10 GPS: -0.4 / 6 satellites"

        val parsed = parser.parseTraffic(line, receivedAtMillis = 1_700_000_000_000)

        assertNull(parsed)
    }

    @Test
    fun parseTraffic_parsesBackslashSymbolTable() {
        val line =
            "ICA4CA6A4>OGADSB,qAS,SpainAVX:/091637h3724.87N\\00559.81W^085/165/A=001275 id254CA6A4 -832fpm 0rot fnA3:RYR5VV regEI-DYO modelB738"

        val parsed = parser.parseTraffic(line, receivedAtMillis = 1_700_000_000_000)

        assertNotNull(parsed)
        parsed!!
        assertEquals("ICA4CA6A4", parsed.callsign)
        assertEquals("OGADSB", parsed.destination)
        assertEquals(37.4145, parsed.latitude, 1e-4)
        assertEquals(-5.996833, parsed.longitude, 1e-4)
        assertEquals(84.88326, parsed.groundSpeedMps!!, 1e-3)
        assertEquals(-4.22656, parsed.verticalSpeedMps!!, 1e-5)
        assertEquals("4CA6A4", parsed.deviceIdHex)
    }

    @Test
    fun parseTraffic_extractsDeviceIdFromCallsignWhenIdTokenMissing() {
        val line = "ICA484D20>APRS,TCPIP*,qAC,GLIDERN1:!4903.50N/07201.75W^110/064/A=001000"

        val parsed = parser.parseTraffic(line, receivedAtMillis = 1_700_000_000_000)

        assertNotNull(parsed)
        assertEquals("484D20", parsed?.deviceIdHex)
    }
}
