package com.trust3.xcpro.variometer.bluetooth.lxnav

import com.trust3.xcpro.bluetooth.NmeaLine

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
            LxSentenceId.PLXVF -> parsePlxVf(fields, checksumStatus, line.receivedMonoMs)
            LxSentenceId.LXWP2,
            LxSentenceId.LXWP3,
            LxSentenceId.PLXVS -> LxParseOutcome.KnownUnsupported(
                sentenceId = sentenceId,
                receivedMonoMs = line.receivedMonoMs,
                checksumStatus = checksumStatus
            )

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

    private fun String.nullIfEmpty(): String? = if (isEmpty()) null else this

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

    companion object {
        private const val CHECKSUM_SEPARATOR: Char = '*'
        private const val FIELD_SEPARATOR: Char = ','
        private const val CHECKSUM_HEX_LENGTH: Int = 2
    }
}

