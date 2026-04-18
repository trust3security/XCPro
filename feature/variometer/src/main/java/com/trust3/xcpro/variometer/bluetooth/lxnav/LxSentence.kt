package com.trust3.xcpro.variometer.bluetooth.lxnav

enum class LxSentenceId {
    LXWP0,
    LXWP1,
    LXWP2,
    LXWP3,
    PLXVF,
    PLXVS,
    UNKNOWN
}

enum class ChecksumStatus {
    VALID,
    MISSING,
    INVALID,
    MALFORMED
}

enum class LxRejectedReason {
    MISSING_PREFIX,
    INVALID_CHECKSUM,
    MALFORMED_CHECKSUM,
    MALFORMED_FIELDS
}

data class LxDeviceInfo(
    val product: String? = null,
    val serial: String? = null,
    val softwareVersion: String? = null,
    val hardwareVersion: String? = null
) {
    fun isEmpty(): Boolean =
        product == null &&
            serial == null &&
            softwareVersion == null &&
            hardwareVersion == null
}

sealed interface ParsedLxSentence {
    val sentenceId: LxSentenceId
    val checksumStatus: ChecksumStatus
    val receivedMonoMs: Long
}

data class LxWp0Sentence(
    val airspeedKph: Double?,
    val pressureAltitudeM: Double?,
    val totalEnergyVarioMps: Double?,
    override val checksumStatus: ChecksumStatus,
    override val receivedMonoMs: Long
) : ParsedLxSentence {
    override val sentenceId: LxSentenceId = LxSentenceId.LXWP0
}

data class LxWp1Sentence(
    val deviceInfo: LxDeviceInfo,
    override val checksumStatus: ChecksumStatus,
    override val receivedMonoMs: Long
) : ParsedLxSentence {
    override val sentenceId: LxSentenceId = LxSentenceId.LXWP1
}

sealed interface LxParseOutcome {
    val sentenceId: LxSentenceId
    val receivedMonoMs: Long
    val checksumStatus: ChecksumStatus?

    data class Accepted(
        val sentence: ParsedLxSentence
    ) : LxParseOutcome {
        override val sentenceId: LxSentenceId = sentence.sentenceId
        override val receivedMonoMs: Long = sentence.receivedMonoMs
        override val checksumStatus: ChecksumStatus = sentence.checksumStatus
    }

    data class KnownUnsupported(
        override val sentenceId: LxSentenceId,
        override val receivedMonoMs: Long,
        override val checksumStatus: ChecksumStatus
    ) : LxParseOutcome

    data class UnknownSentence(
        val rawSentenceId: String,
        override val receivedMonoMs: Long,
        override val checksumStatus: ChecksumStatus
    ) : LxParseOutcome {
        override val sentenceId: LxSentenceId = LxSentenceId.UNKNOWN
    }

    data class Rejected(
        val reason: LxRejectedReason,
        override val sentenceId: LxSentenceId,
        override val receivedMonoMs: Long,
        override val checksumStatus: ChecksumStatus? = null
    ) : LxParseOutcome
}
