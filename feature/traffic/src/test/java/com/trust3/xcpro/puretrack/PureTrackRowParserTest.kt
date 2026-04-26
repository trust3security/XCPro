package com.trust3.xcpro.puretrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PureTrackRowParserTest {
    private val parser = PureTrackRowParser()

    @Test
    fun parse_acceptsRequiredOnlyRow() {
        val parsed = parser.parse("T1710000000,L-33.8001,G151.2002,Kpt-1")

        assertTrue(parsed.parsed)
        val target = parsed.target
        assertNotNull(target)
        target!!
        assertEquals("pt-1", target.key)
        assertEquals(1_710_000_000L, target.sourceTimestampEpochSec)
        assertEquals(-33.8001, target.latitude, 1e-6)
        assertEquals(151.2002, target.longitude, 1e-6)
        assertEquals(setOf('T', 'L', 'G', 'K'), target.rawFieldKeys)
    }

    @Test
    fun parse_acceptsOptionalFields() {
        val parsed = parser.parse(
            "T1710000000,L-33.8,G151.2,Kpt-1,A900,t870,P1012.5,C180," +
                "S30.5,s31.2,V-1.1,O3,U16,Dtracker,Jtarget,Blabel,Nname," +
                "Ereg,Mmodel,mcallsign,h12.3,z45.6,81"
        )

        val target = parsed.target
        assertNotNull(target)
        target!!
        assertEquals(900.0, target.altitudeMetersGps ?: Double.NaN, 1e-6)
        assertEquals(870.0, target.altitudeMetersStandard ?: Double.NaN, 1e-6)
        assertEquals(1012.5, target.pressure ?: Double.NaN, 1e-6)
        assertEquals(180.0, target.courseDegrees ?: Double.NaN, 1e-6)
        assertEquals(30.5, target.speedMetersPerSecond ?: Double.NaN, 1e-6)
        assertEquals(31.2, target.calculatedSpeedMetersPerSecond ?: Double.NaN, 1e-6)
        assertEquals(-1.1, target.verticalSpeedMetersPerSecond ?: Double.NaN, 1e-6)
        assertEquals(3, target.objectTypeId)
        assertEquals(16, target.sourceTypeId)
        assertEquals("tracker", target.trackerUid)
        assertEquals("target", target.targetId)
        assertEquals("label", target.label)
        assertEquals("name", target.name)
        assertEquals("reg", target.registration)
        assertEquals("model", target.model)
        assertEquals("callsign", target.callsign)
        assertEquals(12.3, target.horizontalAccuracyMeters ?: Double.NaN, 1e-6)
        assertEquals(45.6, target.verticalAccuracyMeters ?: Double.NaN, 1e-6)
        assertEquals(true, target.onGround)
    }

    @Test
    fun parse_rejectsMissingRequiredFields() {
        assertDrop("L-33.8,G151.2,Kpt-1", PureTrackRowDropReason.MISSING_REQUIRED_FIELD)
        assertDrop("T1710000000,G151.2,Kpt-1", PureTrackRowDropReason.MISSING_REQUIRED_FIELD)
        assertDrop("T1710000000,L-33.8,Kpt-1", PureTrackRowDropReason.MISSING_REQUIRED_FIELD)
        assertDrop("T1710000000,L-33.8,G151.2", PureTrackRowDropReason.MISSING_REQUIRED_FIELD)
    }

    @Test
    fun parse_rejectsInvalidTimestamp() {
        assertDrop("Tbad,L-33.8,G151.2,Kpt-1", PureTrackRowDropReason.INVALID_TIMESTAMP)
    }

    @Test
    fun parse_rejectsCoordinatesOutsideRange() {
        assertDrop("T1710000000,L91.0,G151.2,Kpt-1", PureTrackRowDropReason.INVALID_COORDINATE)
        assertDrop("T1710000000,L-33.8,G181.0,Kpt-1", PureTrackRowDropReason.INVALID_COORDINATE)
    }

    @Test
    fun parse_rejectsBlankKey() {
        assertDrop("T1710000000,L-33.8,G151.2,K   ", PureTrackRowDropReason.BLANK_KEY)
    }

    @Test
    fun parse_invalidOptionalNumericFieldsDoNotDropRow() {
        val parsed = parser.parse("T1710000000,L-33.8,G151.2,Kpt-1,Abad,C361,Otype")

        assertTrue(parsed.parsed)
        val target = parsed.target
        assertNotNull(target)
        target!!
        assertNull(target.altitudeMetersGps)
        assertNull(target.courseDegrees)
        assertNull(target.objectTypeId)
    }

    @Test
    fun parse_nonFiniteOptionalDoubleFieldsBecomeNull() {
        val parsed = parser.parse(
            "T1710000000,L-33.8,G151.2,Kpt-1,ANaN,SInfinity,V-Infinity,hNaN,zInfinity"
        )

        assertTrue(parsed.parsed)
        val target = parsed.target
        assertNotNull(target)
        target!!
        assertNull(target.altitudeMetersGps)
        assertNull(target.speedMetersPerSecond)
        assertNull(target.verticalSpeedMetersPerSecond)
        assertNull(target.horizontalAccuracyMeters)
        assertNull(target.verticalAccuracyMeters)
    }

    @Test
    fun parse_isCaseSensitiveForTimestampAndStandardAltitude() {
        val parsed = parser.parse("T1710000000,t900,L-33.8,G151.2,Kpt-1")

        assertTrue(parsed.parsed)
        assertEquals(1_710_000_000L, parsed.target?.sourceTimestampEpochSec)
        assertEquals(900.0, parsed.target?.altitudeMetersStandard ?: Double.NaN, 1e-6)

        val lowerOnly = parser.parse("t1710000000,L-33.8,G151.2,Kpt-1")
        assertFalse(lowerOnly.parsed)
        assertEquals(PureTrackRowDropReason.MISSING_REQUIRED_FIELD, lowerOnly.dropReason)
    }

    @Test
    fun parse_isCaseSensitiveForSpeedAndCalculatedSpeed() {
        val parsed = parser.parse("T1710000000,L-33.8,G151.2,Kpt-1,S30.5,s31.5")

        assertTrue(parsed.parsed)
        assertEquals(30.5, parsed.target?.speedMetersPerSecond ?: Double.NaN, 1e-6)
        assertEquals(31.5, parsed.target?.calculatedSpeedMetersPerSecond ?: Double.NaN, 1e-6)
    }

    @Test
    fun parse_readsOnGroundKey8() {
        val onGround = parser.parse("T1710000000,L-33.8,G151.2,Kpt-1,81")
        val airborne = parser.parse("T1710000000,L-33.8,G151.2,Kpt-1,80")

        assertEquals(true, onGround.target?.onGround)
        assertEquals(false, airborne.target?.onGround)
    }

    @Test
    fun parse_ignoresUnknownTokenKeys() {
        val parsed = parser.parse("T1710000000,L-33.8,G151.2,Kpt-1,Xignored")

        assertTrue(parsed.parsed)
        assertEquals("pt-1", parsed.target?.key)
        assertTrue(parsed.rawFieldKeys.contains('X'))
    }

    @Test
    fun parse_duplicateKeysUseLastToken() {
        val parsed = parser.parse("T1,T2,L-34.0,L-33.8,G151.1,G151.2,Kold,Knew,S10,S12")

        assertTrue(parsed.parsed)
        assertEquals(5, parsed.duplicateTokenCount)
        assertEquals(2L, parsed.target?.sourceTimestampEpochSec)
        assertEquals(-33.8, parsed.target?.latitude ?: Double.NaN, 1e-6)
        assertEquals(151.2, parsed.target?.longitude ?: Double.NaN, 1e-6)
        assertEquals("new", parsed.target?.key)
        assertEquals(12.0, parsed.target?.speedMetersPerSecond ?: Double.NaN, 1e-6)
    }

    @Test
    fun parse_ignoresEmptyTokens() {
        val parsed = parser.parse("T1710000000,, L-33.8, ,G151.2,Kpt-1,")

        assertTrue(parsed.parsed)
        assertEquals("pt-1", parsed.target?.key)
        assertEquals(0, parsed.duplicateTokenCount)
    }

    private fun assertDrop(row: String, reason: PureTrackRowDropReason) {
        val parsed = parser.parse(row)

        assertFalse(parsed.parsed)
        assertEquals(reason, parsed.dropReason)
    }
}
