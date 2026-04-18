package com.trust3.xcpro.igc.domain

import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pure domain formatter for deterministic IGC record text generation.
 *
 * All returned lines are record-only text (no CRLF) except [formatLinesCrlf].
 */
class IgcRecordFormatter {

    data class HeaderRecord(
        val source: Char,
        val code: String,
        val longName: String?,
        val value: String
    )

    data class ExtensionDefinition(
        val start: Int,
        val end: Int,
        val code: String
    ) {
        val width: Int get() = end - start + 1

        init {
            require(start in 0..99) { "start must be within 00..99: $start" }
            require(end in 0..99) { "end must be within 00..99: $end" }
            require(end >= start) { "end must be >= start: $start..$end" }
            require(code.length == 3) { "code must be exactly 3 chars: $code" }
            require(code.all { it.isLetterOrDigit() }) { "code must be alphanumeric: $code" }
        }
    }

    enum class FixValidity {
        A,
        V
    }

    data class BRecord(
        val timeUtc: LocalTime,
        val latitudeDegrees: Double,
        val longitudeDegrees: Double,
        val fixValidity: FixValidity,
        val pressureAltitudeMeters: Int?,
        val gnssAltitudeMeters: Int?,
        val extensionValues: Map<String, Int> = emptyMap()
    )

    data class EventRecord(
        val timeUtc: LocalTime,
        val code: String,
        val payload: String? = null
    )

    fun formatA(
        manufacturerId: String,
        serialId: String,
        extraText: String? = null
    ): String {
        val manufacturer = sanitizeToken(manufacturerId).uppercase()
        val serial = sanitizeToken(serialId).uppercase()
        require(manufacturer.length == 3) { "manufacturerId must be 3 chars: $manufacturerId" }
        require(serial.length >= 3) { "serialId must be at least 3 chars: $serialId" }

        val suffix = extraText?.trim().orEmpty()
        return if (suffix.isEmpty()) {
            "A$manufacturer$serial"
        } else {
            "A$manufacturer$serial-${sanitizeText(suffix)}"
        }
    }

    fun formatH(record: HeaderRecord): String {
        val source = record.source.uppercaseChar()
        require(source == 'F' || source == 'O') { "H source must be F or O: ${record.source}" }

        val code = sanitizeToken(record.code).uppercase()
        require(code.length == 3) { "H code must be 3 chars: ${record.code}" }

        val longName = sanitizeToken(record.longName.orEmpty()).uppercase()
        val value = sanitizeText(record.value)
        require(value.isNotEmpty()) { "H value must not be empty" }

        return "H$source$code$longName:$value"
    }

    fun formatI(definitions: List<ExtensionDefinition>): String {
        require(definitions.size <= 99) { "I extension count must be <= 99" }
        if (definitions.isEmpty()) return "I00"

        val sorted = definitions.sortedBy { it.start }
        validateNoOverlaps(sorted)

        return buildString {
            append("I")
            append(sorted.size.toString().padStart(2, '0'))
            sorted.forEach { definition ->
                append(definition.start.toString().padStart(2, '0'))
                append(definition.end.toString().padStart(2, '0'))
                append(definition.code.uppercase())
            }
        }
    }

    fun formatB(
        record: BRecord,
        definitions: List<ExtensionDefinition> = emptyList()
    ): String {
        val time = formatTime(record.timeUtc)
        val latitude = decimalDegreesToIgcLatitude(record.latitudeDegrees)
        val longitude = decimalDegreesToIgcLongitude(record.longitudeDegrees)
        val pressure = formatAltitudeMeters5(record.pressureAltitudeMeters)
        val gnss = formatAltitudeMeters5(record.gnssAltitudeMeters)

        val base = "B$time$latitude$longitude${record.fixValidity.name}$pressure$gnss"
        require(base.length == BASE_B_RECORD_LENGTH) {
            "B base record must be $BASE_B_RECORD_LENGTH chars, got ${base.length}"
        }
        if (definitions.isEmpty()) return base

        val sorted = definitions.sortedBy { it.start }
        validateNoOverlaps(sorted)
        validateContiguousBExtensions(sorted)

        val extensionPart = buildString {
            sorted.forEach { definition ->
                val rawValue = record.extensionValues[definition.code.uppercase()] ?: 0
                append(formatUnsignedValue(rawValue, definition.width))
            }
        }
        return base + extensionPart
    }

    fun formatE(record: EventRecord): String {
        val code = sanitizeToken(record.code).uppercase()
        require(code.length == 3) { "E code must be 3 chars: ${record.code}" }
        val payload = record.payload?.let { sanitizeText(it) }.orEmpty()
        return "E${formatTime(record.timeUtc)}$code$payload"
    }

    fun formatL(
        source: String,
        text: String
    ): String {
        val sourceCode = sanitizeToken(source).uppercase()
        require(sourceCode.length == 3) { "L source must be 3 chars: $source" }
        val payload = sanitizeText(text)
        require(payload.isNotEmpty()) { "L text must not be empty" }
        return "L$sourceCode$payload"
    }

    fun formatLinesCrlf(lines: List<String>): String {
        if (lines.isEmpty()) return ""
        val sanitized = lines.map(::sanitizeLine)
        return sanitized.joinToString(separator = CRLF, postfix = CRLF)
    }

    fun decimalDegreesToIgcLatitude(latitudeDegrees: Double): String {
        require(latitudeDegrees in -90.0..90.0) { "Latitude out of range: $latitudeDegrees" }
        val (degrees, minutes, thousandths) = decimalDegreesToDegreesMinutes(latitudeDegrees)
        val hemisphere = if (latitudeDegrees >= 0.0) "N" else "S"
        return "${degrees.toString().padStart(2, '0')}${minutes.toString().padStart(2, '0')}${thousandths.toString().padStart(3, '0')}$hemisphere"
    }

    fun decimalDegreesToIgcLongitude(longitudeDegrees: Double): String {
        require(longitudeDegrees in -180.0..180.0) { "Longitude out of range: $longitudeDegrees" }
        val (degrees, minutes, thousandths) = decimalDegreesToDegreesMinutes(longitudeDegrees)
        val hemisphere = if (longitudeDegrees >= 0.0) "E" else "W"
        return "${degrees.toString().padStart(3, '0')}${minutes.toString().padStart(2, '0')}${thousandths.toString().padStart(3, '0')}$hemisphere"
    }

    fun formatAltitudeMeters5(altitudeMeters: Int?): String {
        val altitude = altitudeMeters ?: return "00000"
        require(altitude in MIN_SIGNED_ALTITUDE..MAX_UNSIGNED_ALTITUDE) {
            "Altitude out of 5-char range: $altitude"
        }
        return if (altitude >= 0) {
            altitude.toString().padStart(5, '0')
        } else {
            "-" + abs(altitude).toString().padStart(4, '0')
        }
    }

    private fun formatTime(timeUtc: LocalTime): String {
        return buildString(6) {
            append(timeUtc.hour.toString().padStart(2, '0'))
            append(timeUtc.minute.toString().padStart(2, '0'))
            append(timeUtc.second.toString().padStart(2, '0'))
        }
    }

    private fun decimalDegreesToDegreesMinutes(value: Double): Triple<Int, Int, Int> {
        var degrees = floor(abs(value)).toInt()
        val minutesTotal = (abs(value) - degrees) * 60.0
        var minutes = floor(minutesTotal).toInt()
        var thousandths = ((minutesTotal - minutes) * 1000.0).roundToInt()

        if (thousandths == 1000) {
            thousandths = 0
            minutes += 1
        }
        if (minutes == 60) {
            minutes = 0
            degrees += 1
        }
        return Triple(degrees, minutes, thousandths)
    }

    private fun validateNoOverlaps(definitions: List<ExtensionDefinition>) {
        var previousEnd = -1
        definitions.forEach { definition ->
            require(definition.start > previousEnd) {
                "I extension ranges overlap: previous end=$previousEnd next=${definition.start}"
            }
            previousEnd = definition.end
        }
    }

    private fun validateContiguousBExtensions(definitions: List<ExtensionDefinition>) {
        var expectedStart = FIRST_B_EXTENSION_START
        definitions.forEach { definition ->
            require(definition.start == expectedStart) {
                "B extensions must be contiguous from byte $FIRST_B_EXTENSION_START; got ${definition.start}"
            }
            expectedStart = definition.end + 1
        }
    }

    private fun formatUnsignedValue(value: Int, width: Int): String {
        val maxValue = ("9".repeat(width)).toInt()
        val normalized = value.coerceIn(0, maxValue)
        return normalized.toString().padStart(width, '0')
    }

    private fun sanitizeToken(value: String): String {
        return value
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .trim()
    }

    private fun sanitizeText(value: String): String {
        return value
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
    }

    private fun sanitizeLine(value: String): String {
        require(value.none { it == '\r' || it == '\n' }) { "Line must not contain newline characters" }
        return value
    }

    companion object {
        const val BASE_B_RECORD_LENGTH: Int = 35

        const val CRLF: String = "\r\n"

        val IAS_EXTENSION: ExtensionDefinition = ExtensionDefinition(
            start = 36,
            end = 38,
            code = "IAS"
        )
        val TAS_EXTENSION: ExtensionDefinition = ExtensionDefinition(
            start = 39,
            end = 41,
            code = "TAS"
        )
        val IAS_TAS_EXTENSIONS: List<ExtensionDefinition> = listOf(IAS_EXTENSION, TAS_EXTENSION)

        private const val FIRST_B_EXTENSION_START: Int = 36
        private const val MIN_SIGNED_ALTITUDE: Int = -9_999
        private const val MAX_UNSIGNED_ALTITUDE: Int = 99_999
    }
}
