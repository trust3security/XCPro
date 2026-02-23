package com.example.xcpro.ogn

import javax.inject.Inject
import java.util.Locale
import kotlin.math.abs

/**
 * Parses OGN/APRS TNC2 lines into typed traffic targets.
 * Unknown comment tokens are preserved as raw comment text.
 */
class OgnAprsLineParser @Inject constructor() {

    companion object {
        private const val FEET_TO_METERS = 0.3048
        private const val KNOTS_TO_MPS = 0.514444
        private const val FPM_TO_MPS = 0.00508
        private const val OGN_DEVICE_ID_HEX_LENGTH = 6
        private const val OGN_TYPED_ID_HEX_LENGTH = 8
        private const val OGN_TYPE_MASK = 0x3C
        private const val OGN_TYPE_SHIFT = 2
        private const val OGN_MALFORMED_ID_HEX_LENGTH = 7
    }

    private val positionRegex =
        Regex("""(\d{2})(\d{2}\.\d{2})([NS])([/\\])(\d{3})(\d{2}\.\d{2})([EW])(.)""")
    private val courseSpeedRegex = Regex("""\b(\d{1,3})/(\d{1,3})\b""")
    private val altitudeRegex = Regex("""/A=(\d{1,6})\b""")
    private val verticalSpeedRegex = Regex("""([+-]?\d+(?:\.\d+)?)fpm\b""", RegexOption.IGNORE_CASE)
    private val signalRegex = Regex("""(\d+(?:\.\d+)?)dB\b""", RegexOption.IGNORE_CASE)
    private val idRegex = Regex("""\bid((?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8}))\b""")
    private val malformedIdRegex = Regex("""\bid([0-9A-Fa-f]{7})\b""")
    private val callsignDeviceIdRegex = Regex("""^[A-Z]{3}[0-9A-F]{6}$""")

    fun parseTraffic(line: String, receivedAtMillis: Long): OgnTrafficTarget? {
        if (line.isBlank() || line.startsWith("#")) return null

        val payloadSplit = line.split(":", limit = 2)
        if (payloadSplit.size != 2) return null

        val header = parseHeader(payloadSplit[0]) ?: return null
        if (header.destination.equals("OGNSDR", ignoreCase = true)) return null

        val info = payloadSplit[1]
        val payloadType = info.firstOrNull() ?: return null
        if (payloadType !in setOf('!', '=', '/', '@')) return null

        val positionMatch = positionRegex.find(info) ?: return null
        val latitude = parseLatitude(
            degreesText = positionMatch.groupValues[1],
            minutesText = positionMatch.groupValues[2],
            hemisphere = positionMatch.groupValues[3].single()
        ) ?: return null
        val longitude = parseLongitude(
            degreesText = positionMatch.groupValues[5],
            minutesText = positionMatch.groupValues[6],
            hemisphere = positionMatch.groupValues[7].single()
        ) ?: return null
        if (!isValidCoordinate(latitude, longitude)) return null

        val comment = info.substring(positionMatch.range.last + 1).trim().ifEmpty { null }
        val courseSpeedMatch = courseSpeedRegex.find(info)
        val trackDegrees = courseSpeedMatch
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { course ->
                when {
                    course == 0 -> null
                    course in 1..360 -> course.toDouble()
                    else -> null
                }
            }
        val groundSpeedMps = courseSpeedMatch
            ?.groupValues
            ?.getOrNull(2)
            ?.toDoubleOrNull()
            ?.times(KNOTS_TO_MPS)

        val altitudeMeters = altitudeRegex
            .find(info)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.times(FEET_TO_METERS)

        val verticalSpeedMps = verticalSpeedRegex
            .find(info)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.times(FPM_TO_MPS)

        val signalDb = signalRegex
            .find(info)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()

        if (malformedIdRegex.containsMatchIn(info)) return null

        val parsedIdToken = parseOgnIdToken(info)
        val sourceAddressTypeFallback = ognAddressTypeFromCallsignPrefix(header.source)
        val addressType = when {
            parsedIdToken?.hasExplicitTypeByte == true -> parsedIdToken.addressType
            else -> sourceAddressTypeFallback
        }
        val deviceIdFromToken = parsedIdToken?.deviceIdHex
        val aprsAircraftTypeCode = parsedIdToken?.aircraftTypeCode
        val deviceIdHex = deviceIdFromToken
            ?: extractDeviceIdFromCallsign(header.source)
        val stableId = deviceIdHex ?: header.source.uppercase()
        val displayLabel = deviceIdHex ?: header.source

        return OgnTrafficTarget(
            id = stableId,
            callsign = header.source,
            destination = header.destination,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            trackDegrees = trackDegrees,
            groundSpeedMps = groundSpeedMps,
            verticalSpeedMps = verticalSpeedMps,
            deviceIdHex = deviceIdHex,
            signalDb = signalDb,
            displayLabel = displayLabel,
            identity = aprsAircraftTypeCode?.let { aircraftTypeCode ->
                OgnTrafficIdentity(
                    registration = null,
                    competitionNumber = null,
                    aircraftModel = null,
                    tracked = null,
                    identified = null,
                    aircraftTypeCode = aircraftTypeCode
                )
            },
            rawComment = comment,
            rawLine = line,
            timestampMillis = receivedAtMillis,
            lastSeenMillis = receivedAtMillis,
            addressType = addressType,
            addressHex = deviceIdHex
        )
    }

    private fun parseHeader(headerText: String): ParsedHeader? {
        val gtIndex = headerText.indexOf('>')
        if (gtIndex <= 0 || gtIndex >= headerText.length - 1) return null

        val source = headerText.substring(0, gtIndex).trim()
        if (source.isEmpty()) return null

        val afterDestination = headerText.substring(gtIndex + 1)
        val destination = afterDestination.substringBefore(",").trim()
        if (destination.isEmpty()) return null

        return ParsedHeader(source = source, destination = destination)
    }

    private fun parseLatitude(degreesText: String, minutesText: String, hemisphere: Char): Double? {
        val degrees = degreesText.toDoubleOrNull() ?: return null
        val minutes = minutesText.toDoubleOrNull() ?: return null
        val value = degrees + minutes / 60.0
        val signed = if (hemisphere == 'S') -value else value
        return signed
    }

    private fun parseLongitude(degreesText: String, minutesText: String, hemisphere: Char): Double? {
        val degrees = degreesText.toDoubleOrNull() ?: return null
        val minutes = minutesText.toDoubleOrNull() ?: return null
        val value = degrees + minutes / 60.0
        val signed = if (hemisphere == 'W') -value else value
        return signed
    }

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (abs(latitude) > 90.0) return false
        if (abs(longitude) > 180.0) return false
        return true
    }

    private fun extractDeviceIdFromCallsign(sourceCallsign: String): String? {
        val normalized = sourceCallsign
            .trim()
            .uppercase(Locale.US)
            .substringBefore("-")
        if (!callsignDeviceIdRegex.matches(normalized)) return null
        return normalized.takeLast(6)
    }

    private fun parseOgnIdToken(info: String): ParsedOgnIdToken? {
        val token = idRegex
            .find(info)
            ?.groupValues
            ?.getOrNull(1)
            ?.uppercase(Locale.US)
            ?: return null

        return when (token.length) {
            OGN_TYPED_ID_HEX_LENGTH -> {
                val typeByteHex = token.take(2)
                ParsedOgnIdToken(
                    deviceIdHex = token.takeLast(OGN_DEVICE_ID_HEX_LENGTH),
                    addressType = ognAddressTypeFromTypeByteHex(typeByteHex),
                    hasExplicitTypeByte = true,
                    aircraftTypeCode = decodeAircraftTypeCodeFromTypeByte(typeByteHex)
                )
            }
            OGN_DEVICE_ID_HEX_LENGTH -> {
                ParsedOgnIdToken(
                    deviceIdHex = token,
                    addressType = OgnAddressType.UNKNOWN,
                    hasExplicitTypeByte = false,
                    aircraftTypeCode = null
                )
            }
            OGN_MALFORMED_ID_HEX_LENGTH -> null
            else -> null
        }
    }

    /**
     * OGN typed id prefix encodes STttttaa where tttt is aircraft type.
     */
    private fun decodeAircraftTypeCodeFromTypeByte(typeByteHex: String): Int? {
        val typeByte = typeByteHex.toIntOrNull(radix = 16) ?: return null
        val aircraftTypeCode = (typeByte and OGN_TYPE_MASK) shr OGN_TYPE_SHIFT
        return aircraftTypeCode.takeIf { it > 0 }
    }

    private data class ParsedHeader(
        val source: String,
        val destination: String
    )

    private data class ParsedOgnIdToken(
        val deviceIdHex: String,
        val addressType: OgnAddressType,
        val hasExplicitTypeByte: Boolean,
        val aircraftTypeCode: Int?
    )
}
