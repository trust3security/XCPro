package com.trust3.xcpro.variometer.bluetooth.lxnav

import com.trust3.xcpro.bluetooth.NmeaLine
import kotlin.math.pow
import kotlin.math.roundToInt

class LxSentenceParser {

    fun parse(line: NmeaLine): LxParseOutcome {
        val text = line.text
        if (!text.startsWith("$")) {
            return LxParseOutcome.Rejected(
                reason = LxRejectedReason.MISSING_PREFIX,
                sentenceId = LxSentenceId.UNKNOWN,
                receivedMonoMs = line.receivedMonoMs
            )
        }

        val payload = extractPayload(text)
        val sentenceIdToken = extractSentenceIdToken(payload)
        val sentenceId = classifySentenceId(sentenceIdToken)
        val checksumStatus = evaluateChecksum(text)

        when (checksumStatus) {
            ChecksumStatus.INVALID -> {
                return LxParseOutcome.Rejected(
                    reason = LxRejectedReason.INVALID_CHECKSUM,
                    sentenceId = sentenceId,
                    receivedMonoMs = line.receivedMonoMs,
                    checksumStatus = checksumStatus
                )
            }

            ChecksumStatus.MALFORMED -> {
                return LxParseOutcome.Rejected(
                    reason = LxRejectedReason.MALFORMED_CHECKSUM,
                    sentenceId = sentenceId,
                    receivedMonoMs = line.receivedMonoMs,
                    checksumStatus = checksumStatus
                )
            }

            ChecksumStatus.MISSING,
            ChecksumStatus.VALID -> Unit
        }

        val fields = extractFields(payload)
        return when (sentenceId) {
            LxSentenceId.LXWP0 -> parseLxWp0(fields, checksumStatus, line.receivedMonoMs)
            LxSentenceId.LXWP1 -> parseLxWp1(fields, checksumStatus, line.receivedMonoMs)
            LxSentenceId.LXWP2 -> parseLxWp2(fields, checksumStatus, line.receivedMonoMs)
            LxSentenceId.LXWP3 -> parseLxWp3(fields, checksumStatus, line.receivedMonoMs)
            LxSentenceId.PLXVF -> parsePlxVf(fields, checksumStatus, line.receivedMonoMs)
            LxSentenceId.PLXVS -> parsePlxVs(fields, checksumStatus, line.receivedMonoMs)

            LxSentenceId.UNKNOWN -> LxParseOutcome.UnknownSentence(
                rawSentenceId = sentenceIdToken,
                receivedMonoMs = line.receivedMonoMs,
                checksumStatus = checksumStatus
            )
        }
    }

    private fun parseLxWp0(
        fields: List<String>,
        checksumStatus: ChecksumStatus,
        receivedMonoMs: Long
    ): LxParseOutcome {
        val airspeed = parseOptionalDouble(fields.getOrNull(1))
        val pressureAltitude = parseOptionalDouble(fields.getOrNull(2))
        val totalEnergyVario = parseOptionalDouble(fields.getOrNull(3))

        if (
            airspeed is NumericField.Malformed ||
            pressureAltitude is NumericField.Malformed ||
            totalEnergyVario is NumericField.Malformed
        ) {
            return LxParseOutcome.Rejected(
                reason = LxRejectedReason.MALFORMED_FIELDS,
                sentenceId = LxSentenceId.LXWP0,
                receivedMonoMs = receivedMonoMs,
                checksumStatus = checksumStatus
            )
        }

        return LxParseOutcome.Accepted(
            LxWp0Sentence(
                airspeedKph = (airspeed as? NumericField.Value)?.value,
                pressureAltitudeM = (pressureAltitude as? NumericField.Value)?.value,
                totalEnergyVarioMps = (totalEnergyVario as? NumericField.Value)?.value,
                checksumStatus = checksumStatus,
                receivedMonoMs = receivedMonoMs
            )
        )
    }

    private fun parseLxWp1(
        fields: List<String>,
        checksumStatus: ChecksumStatus,
        receivedMonoMs: Long
    ): LxParseOutcome {
        return LxParseOutcome.Accepted(
            LxWp1Sentence(
                deviceInfo = LxDeviceInfo(
                    product = fields.getOrNull(0)?.nullIfEmpty(),
                    serial = fields.getOrNull(1)?.nullIfEmpty(),
                    softwareVersion = fields.getOrNull(2)?.nullIfEmpty(),
                    hardwareVersion = fields.getOrNull(3)?.nullIfEmpty()
                ),
                checksumStatus = checksumStatus,
                receivedMonoMs = receivedMonoMs
            )
        )
    }

    private fun parseLxWp2(
        fields: List<String>,
        checksumStatus: ChecksumStatus,
        receivedMonoMs: Long
    ): LxParseOutcome {
        val macCready = parseOptionalDouble(fields.getOrNull(0))
        val ballastOverloadFactor = parseOptionalDouble(fields.getOrNull(1))
        val rawBugs = parseOptionalDouble(fields.getOrNull(2))
        val polarA = parseOptionalDouble(fields.getOrNull(3))
        val polarB = parseOptionalDouble(fields.getOrNull(4))
        val polarC = parseOptionalDouble(fields.getOrNull(5))
        val audioVolume = parseOptionalInt(fields.getOrNull(6))

        if (
            macCready is NumericField.Malformed ||
            ballastOverloadFactor is NumericField.Malformed ||
            rawBugs is NumericField.Malformed ||
            polarA is NumericField.Malformed ||
            polarB is NumericField.Malformed ||
            polarC is NumericField.Malformed ||
            audioVolume is IntField.Malformed
        ) {
            return malformedFieldsOutcome(LxSentenceId.LXWP2, receivedMonoMs, checksumStatus)
        }

        return LxParseOutcome.Accepted(
            LxWp2Sentence(
                macCreadyMps = (macCready as? NumericField.Value)?.value,
                ballastOverloadFactor = (ballastOverloadFactor as? NumericField.Value)?.value,
                bugsPercent = (rawBugs as? NumericField.Value)?.value?.toLxBugsPercent(),
                polarA = (polarA as? NumericField.Value)?.value,
                polarB = (polarB as? NumericField.Value)?.value,
                polarC = (polarC as? NumericField.Value)?.value,
                audioVolume = (audioVolume as? IntField.Value)?.value,
                checksumStatus = checksumStatus,
                receivedMonoMs = receivedMonoMs
            )
        )
    }

    private fun parseLxWp3(
        fields: List<String>,
        checksumStatus: ChecksumStatus,
        receivedMonoMs: Long
    ): LxParseOutcome {
        val altitudeOffsetFeet = parseOptionalDouble(fields.getOrNull(0))
        val scMode = parseOptionalDouble(fields.getOrNull(1))
        val varioFilter = parseOptionalDouble(fields.getOrNull(2))
        val teFilter = parseOptionalDouble(fields.getOrNull(3))
        val teLevel = parseOptionalDouble(fields.getOrNull(4))
        val varioAverage = parseOptionalDouble(fields.getOrNull(5))
        val varioRange = parseOptionalDouble(fields.getOrNull(6))
        val scTab = parseOptionalDouble(fields.getOrNull(7))
        val scLow = parseOptionalDouble(fields.getOrNull(8))
        val scSpeed = parseOptionalDouble(fields.getOrNull(9))
        val smartDiff = parseOptionalDouble(fields.getOrNull(10))
        val timeOffset = parseOptionalInt(fields.getOrNull(12))

        if (
            altitudeOffsetFeet is NumericField.Malformed ||
            scMode is NumericField.Malformed ||
            varioFilter is NumericField.Malformed ||
            teFilter is NumericField.Malformed ||
            teLevel is NumericField.Malformed ||
            varioAverage is NumericField.Malformed ||
            varioRange is NumericField.Malformed ||
            scTab is NumericField.Malformed ||
            scLow is NumericField.Malformed ||
            scSpeed is NumericField.Malformed ||
            smartDiff is NumericField.Malformed ||
            timeOffset is IntField.Malformed
        ) {
            return malformedFieldsOutcome(LxSentenceId.LXWP3, receivedMonoMs, checksumStatus)
        }

        val altitudeOffsetFeetValue = (altitudeOffsetFeet as? NumericField.Value)?.value

        return LxParseOutcome.Accepted(
            LxWp3Sentence(
                altitudeOffsetFeet = altitudeOffsetFeetValue,
                qnhHpa = altitudeOffsetFeetValue?.toQnhHpaFromAltitudeOffsetFeet(),
                scMode = (scMode as? NumericField.Value)?.value,
                varioFilter = (varioFilter as? NumericField.Value)?.value,
                teFilter = (teFilter as? NumericField.Value)?.value,
                teLevel = (teLevel as? NumericField.Value)?.value,
                varioAverage = (varioAverage as? NumericField.Value)?.value,
                varioRange = (varioRange as? NumericField.Value)?.value,
                scTab = (scTab as? NumericField.Value)?.value,
                scLow = (scLow as? NumericField.Value)?.value,
                scSpeed = (scSpeed as? NumericField.Value)?.value,
                smartDiff = (smartDiff as? NumericField.Value)?.value,
                gliderName = fields.getOrNull(11)?.nullIfEmpty(),
                timeOffsetMinutes = (timeOffset as? IntField.Value)?.value,
                checksumStatus = checksumStatus,
                receivedMonoMs = receivedMonoMs
            )
        )
    }

    private fun parsePlxVf(
        fields: List<String>,
        checksumStatus: ChecksumStatus,
        receivedMonoMs: Long
    ): LxParseOutcome {
        val provisionalVario = parseOptionalDouble(fields.getOrNull(4))
        val indicatedAirspeed = parseOptionalDouble(fields.getOrNull(5))
        val pressureAltitude = parseOptionalDouble(fields.getOrNull(6))

        if (
            provisionalVario is NumericField.Malformed ||
            indicatedAirspeed is NumericField.Malformed ||
            pressureAltitude is NumericField.Malformed
        ) {
            return LxParseOutcome.Rejected(
                reason = LxRejectedReason.MALFORMED_FIELDS,
                sentenceId = LxSentenceId.PLXVF,
                receivedMonoMs = receivedMonoMs,
                checksumStatus = checksumStatus
            )
        }

        return LxParseOutcome.Accepted(
            PlxVfSentence(
                provisionalVarioMps = (provisionalVario as? NumericField.Value)?.value,
                indicatedAirspeedKph = (indicatedAirspeed as? NumericField.Value)?.value,
                pressureAltitudeM = (pressureAltitude as? NumericField.Value)?.value,
                checksumStatus = checksumStatus,
                receivedMonoMs = receivedMonoMs
            )
        )
    }

    private fun parsePlxVs(
        fields: List<String>,
        checksumStatus: ChecksumStatus,
        receivedMonoMs: Long
    ): LxParseOutcome {
        val outsideAirTemperature = parseOptionalDouble(fields.getOrNull(0))
        val mode = parseOptionalInt(fields.getOrNull(1))
        val voltage = parseOptionalDouble(fields.getOrNull(2))

        if (
            outsideAirTemperature is NumericField.Malformed ||
            mode is IntField.Malformed ||
            voltage is NumericField.Malformed
        ) {
            return malformedFieldsOutcome(LxSentenceId.PLXVS, receivedMonoMs, checksumStatus)
        }

        return LxParseOutcome.Accepted(
            PlxVsSentence(
                outsideAirTemperatureC = (outsideAirTemperature as? NumericField.Value)?.value,
                mode = (mode as? IntField.Value)?.value,
                voltageV = (voltage as? NumericField.Value)?.value,
                checksumStatus = checksumStatus,
                receivedMonoMs = receivedMonoMs
            )
        )
    }

    private fun extractPayload(text: String): String {
        val checksumIndex = text.indexOf(CHECKSUM_SEPARATOR)
        return if (checksumIndex >= 0) {
            text.substring(1, checksumIndex)
        } else {
            text.substring(1)
        }
    }

    private fun extractSentenceIdToken(payload: String): String {
        val delimiterIndex = payload.indexOf(FIELD_SEPARATOR)
        return if (delimiterIndex >= 0) {
            payload.substring(0, delimiterIndex)
        } else {
            payload
        }
    }

    private fun extractFields(payload: String): List<String> {
        val delimiterIndex = payload.indexOf(FIELD_SEPARATOR)
        if (delimiterIndex < 0) return emptyList()
        return payload.substring(delimiterIndex + 1).split(
            FIELD_SEPARATOR,
            ignoreCase = false,
            limit = Int.MAX_VALUE
        )
    }

    private fun classifySentenceId(token: String): LxSentenceId =
        when (token) {
            "LXWP0" -> LxSentenceId.LXWP0
            "LXWP1" -> LxSentenceId.LXWP1
            "LXWP2" -> LxSentenceId.LXWP2
            "LXWP3" -> LxSentenceId.LXWP3
            "PLXVF" -> LxSentenceId.PLXVF
            "PLXVS" -> LxSentenceId.PLXVS
            else -> LxSentenceId.UNKNOWN
        }

    private fun evaluateChecksum(text: String): ChecksumStatus {
        val checksumIndex = text.indexOf(CHECKSUM_SEPARATOR)
        if (checksumIndex < 0) return ChecksumStatus.MISSING
        if (text.indexOf(CHECKSUM_SEPARATOR, checksumIndex + 1) >= 0) {
            return ChecksumStatus.MALFORMED
        }

        val trailer = text.substring(checksumIndex + 1)
        if (trailer.length != CHECKSUM_HEX_LENGTH) return ChecksumStatus.MALFORMED

        val expected = trailer.parseHexByte() ?: return ChecksumStatus.MALFORMED
        val computed = computeChecksum(text.substring(1, checksumIndex))
        return if (computed == expected) {
            ChecksumStatus.VALID
        } else {
            ChecksumStatus.INVALID
        }
    }

    private fun computeChecksum(payload: String): Int =
        payload.fold(0) { checksum, character -> checksum xor character.code }

    private fun parseOptionalDouble(field: String?): NumericField =
        when {
            field == null || field.isEmpty() -> NumericField.Blank
            else -> field.toDoubleOrNull()?.let(NumericField::Value) ?: NumericField.Malformed
        }

    private fun parseOptionalInt(field: String?): IntField =
        when {
            field == null || field.isEmpty() -> IntField.Blank
            else -> field.toIntOrNull()?.let(IntField::Value) ?: IntField.Malformed
        }

    private fun malformedFieldsOutcome(
        sentenceId: LxSentenceId,
        receivedMonoMs: Long,
        checksumStatus: ChecksumStatus
    ): LxParseOutcome =
        LxParseOutcome.Rejected(
            reason = LxRejectedReason.MALFORMED_FIELDS,
            sentenceId = sentenceId,
            receivedMonoMs = receivedMonoMs,
            checksumStatus = checksumStatus
        )

    private fun String.nullIfEmpty(): String? = if (isEmpty()) null else this

    private fun Double.toLxBugsPercent(): Int =
        when {
            this in 1.0..1.5 -> ((this - 1.0) * 100.0).roundToInt().coerceIn(0, 50)
            else -> roundToInt().coerceIn(0, 50)
        }

    private fun Double.toQnhHpaFromAltitudeOffsetFeet(): Double {
        val altitudeMeters = -this * FEET_TO_METERS
        val ratio = (1.0 - (altitudeMeters / PRESSURE_ALTITUDE_BASE_METERS))
            .takeIf { it.isFinite() && it > 0.0 }
            ?: return Double.NaN
        return SEA_LEVEL_PRESSURE_HPA * ratio.pow(PRESSURE_ALTITUDE_POWER)
    }

    private fun String.parseHexByte(): Int? {
        if (length != CHECKSUM_HEX_LENGTH) return null
        val high = this[0].digitToIntOrNull(16) ?: return null
        val low = this[1].digitToIntOrNull(16) ?: return null
        return (high shl 4) or low
    }

    private sealed interface NumericField {
        data class Value(val value: Double) : NumericField
        data object Blank : NumericField
        data object Malformed : NumericField
    }

    private sealed interface IntField {
        data class Value(val value: Int) : IntField
        data object Blank : IntField
        data object Malformed : IntField
    }

    companion object {
        private const val CHECKSUM_SEPARATOR: Char = '*'
        private const val FIELD_SEPARATOR: Char = ','
        private const val CHECKSUM_HEX_LENGTH: Int = 2
        private const val SEA_LEVEL_PRESSURE_HPA = 1013.25
        private const val PRESSURE_ALTITUDE_BASE_METERS = 44330.0
        private const val PRESSURE_ALTITUDE_POWER = 5.255
        private const val FEET_TO_METERS = 0.3048
    }
}

