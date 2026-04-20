package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.bluetooth.NmeaLine
import javax.inject.Inject
import kotlin.math.floor

sealed interface CondorSentence {
    val receivedMonoMs: Long

    data class Gga(
        val latitude: Double,
        val longitude: Double,
        val altitudeMeters: Double,
        override val receivedMonoMs: Long
    ) : CondorSentence

    data class Rmc(
        val speedKnots: Double,
        val bearingDeg: Double,
        override val receivedMonoMs: Long
    ) : CondorSentence

    data class LxWp0(
        val airspeedKph: Double,
        override val receivedMonoMs: Long
    ) : CondorSentence
}

class CondorSentenceParser @Inject constructor() {

    fun parse(line: NmeaLine): CondorSentence? {
        val text = line.text
        if (!text.startsWith("$")) return null
        if (!hasValidChecksum(text)) return null
        val payload = text.substring(1).substringBefore('*')
        val fields = payload.split(',', ignoreCase = false, limit = Int.MAX_VALUE)
        if (fields.isEmpty()) return null
        return when (fields[0]) {
            "GPGGA",
            "GNGGA" -> parseGga(fields, line.receivedMonoMs)
            "GPRMC",
            "GNRMC" -> parseRmc(fields, line.receivedMonoMs)
            "LXWP0" -> parseLxWp0(fields, line.receivedMonoMs)
            else -> null
        }
    }

    private fun parseGga(fields: List<String>, receivedMonoMs: Long): CondorSentence.Gga? {
        if ((fields.getOrNull(6)?.toIntOrNull() ?: 0) == 0) return null
        val latitude = parseCoordinate(
            value = fields.getOrNull(2),
            hemisphere = fields.getOrNull(3)
        ) ?: return null
        val longitude = parseCoordinate(
            value = fields.getOrNull(4),
            hemisphere = fields.getOrNull(5)
        ) ?: return null
        val altitudeMeters = fields.getOrNull(9)?.toDoubleOrNull() ?: return null
        return CondorSentence.Gga(
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            receivedMonoMs = receivedMonoMs
        )
    }

    private fun parseRmc(fields: List<String>, receivedMonoMs: Long): CondorSentence.Rmc? {
        if (fields.getOrNull(2) != "A") return null
        val speedKnots = fields.getOrNull(7)?.toDoubleOrNull() ?: 0.0
        val bearingDeg = fields.getOrNull(8)?.toDoubleOrNull() ?: 0.0
        return CondorSentence.Rmc(
            speedKnots = speedKnots,
            bearingDeg = bearingDeg,
            receivedMonoMs = receivedMonoMs
        )
    }

    private fun parseLxWp0(fields: List<String>, receivedMonoMs: Long): CondorSentence.LxWp0? {
        val airspeedKph = fields.getOrNull(2)?.toDoubleOrNull()
            ?: fields.getOrNull(1)?.toDoubleOrNull()
            ?: return null
        if (!airspeedKph.isFinite() || airspeedKph <= 0.0) return null
        return CondorSentence.LxWp0(
            airspeedKph = airspeedKph,
            receivedMonoMs = receivedMonoMs
        )
    }

    private fun hasValidChecksum(text: String): Boolean {
        val checksumIndex = text.indexOf('*')
        if (checksumIndex < 0) return true
        val trailer = text.substring(checksumIndex + 1)
        if (trailer.length != 2) return false
        val expected = trailer.toIntOrNull(16) ?: return false
        val computed = text.substring(1, checksumIndex)
            .fold(0) { checksum, character -> checksum xor character.code }
        return computed == expected
    }

    private fun parseCoordinate(
        value: String?,
        hemisphere: String?
    ): Double? {
        val raw = value?.toDoubleOrNull() ?: return null
        val degrees = floor(raw / 100.0)
        val minutes = raw - (degrees * 100.0)
        val decimalDegrees = degrees + (minutes / 60.0)
        return when (hemisphere) {
            "S",
            "W" -> -decimalDegrees
            "N",
            "E" -> decimalDegrees
            else -> null
        }
    }
}
