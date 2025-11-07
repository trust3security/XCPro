package com.example.xcpro.xcprov1.bluetooth

import kotlin.math.abs
import kotlin.math.floor

/**
 * Lightweight NMEA parser capable of extracting the essential fields from
 * Garmin GLO 2 $GPGGA and $GPRMC sentences.
 */
class GarminNmeaParser {

    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null
    private var lastAltitude: Double? = null
    private var lastSpeedMps: Double? = null
    private var lastTrackDeg: Double? = null
    private var lastHdop: Double? = null
    private var lastVdop: Double? = null
    private var lastSatellites: Int? = null
    private var lastFixQuality: Int? = null
    private var lastTimestampMillis: Long = 0

    /**
     * Consume a raw NMEA line. Returns a [GloGpsFix] whenever enough information
     * has been accumulated to form a consistent fix.
     */
    fun consume(line: String, timestampMillis: Long = System.currentTimeMillis()): GloGpsFix? {
        if (!line.startsWith("\$")) return null
        val withoutChecksum = line.substringBefore('*')
        val parts = withoutChecksum.split(',')
        when {
            parts.isEmpty() -> return null
            parts[0].endsWith("GGA") -> handleGga(parts)
            parts[0].endsWith("RMC") -> handleRmc(parts)
            parts[0].endsWith("GSA") -> handleGsa(parts)
        }

        if (lastLatitude != null && lastLongitude != null) {
            val fix = GloGpsFix(
                latitude = lastLatitude!!,
                longitude = lastLongitude!!,
                altitudeMeters = lastAltitude,
                groundSpeedMps = lastSpeedMps,
                trackDegrees = lastTrackDeg,
                timestampMillis = timestampMillis,
                hdop = lastHdop,
                vdop = lastVdop,
                satellites = lastSatellites,
                fixQuality = lastFixQuality
            )
            lastTimestampMillis = timestampMillis
            return fix
        }
        return null
    }

    /**
     * Returns the age of the last fix in milliseconds.
     */
    fun ageMillis(now: Long = System.currentTimeMillis()): Long {
        if (lastTimestampMillis == 0L) return Long.MAX_VALUE
        return now - lastTimestampMillis
    }

    private fun handleGga(parts: List<String>) {
        if (parts.size < 10) return
        val lat = parseLat(parts[2], parts.getOrNull(3))
        val lon = parseLon(parts[4], parts.getOrNull(5))
        val quality = parts.getOrNull(6)?.toIntOrNull()
        val satellites = parts.getOrNull(7)?.toIntOrNull()
        val hdop = parts.getOrNull(8)?.toDoubleOrNull()
        val altitude = parts.getOrNull(9)?.toDoubleOrNull()

        if (lat != null && lon != null) {
            lastLatitude = lat
            lastLongitude = lon
        }
        lastFixQuality = quality
        lastSatellites = satellites
        lastHdop = hdop
        if (altitude != null) lastAltitude = altitude
    }

    private fun handleRmc(parts: List<String>) {
        if (parts.size < 9) return
        val lat = parseLat(parts[3], parts.getOrNull(4))
        val lon = parseLon(parts[5], parts.getOrNull(6))
        val speedKnots = parts.getOrNull(7)?.toDoubleOrNull()
        val track = parts.getOrNull(8)?.toDoubleOrNull()

        if (lat != null && lon != null) {
            lastLatitude = lat
            lastLongitude = lon
        }
        if (speedKnots != null) {
            lastSpeedMps = speedKnots * 0.514444
        }
        if (track != null) {
            lastTrackDeg = track
        }
    }

    private fun handleGsa(parts: List<String>) {
        if (parts.size < 17) return
        val pdop = parts.getOrNull(15)?.toDoubleOrNull()
        val hdop = parts.getOrNull(16)?.toDoubleOrNull()
        val vdop = parts.getOrNull(17)?.toDoubleOrNull()
        if (hdop != null) lastHdop = hdop
        if (vdop != null) lastVdop = vdop
        if (pdop != null && lastHdop == null) lastHdop = pdop
    }

    private fun parseLat(value: String?, hemisphere: String?): Double? {
        if (value.isNullOrBlank() || hemisphere.isNullOrBlank()) return null
        return parseCoordinate(value, hemisphere == "S")
    }

    private fun parseLon(value: String?, hemisphere: String?): Double? {
        if (value.isNullOrBlank() || hemisphere.isNullOrBlank()) return null
        return parseCoordinate(value, hemisphere == "W")
    }

    private fun parseCoordinate(raw: String, isNegative: Boolean): Double? {
        if (raw.length < 3) return null
        val dot = raw.indexOf('.')
        if (dot < 0) return null
        val degLength = if (raw.length - dot > 4) dot - 2 else dot - 1
        val degrees = raw.substring(0, degLength).toIntOrNull() ?: return null
        val minutes = raw.substring(degLength).toDoubleOrNull() ?: return null
        val decDegrees = degrees + (minutes / 60.0)
        return if (isNegative) -abs(decDegrees) else decDegrees
    }
}
