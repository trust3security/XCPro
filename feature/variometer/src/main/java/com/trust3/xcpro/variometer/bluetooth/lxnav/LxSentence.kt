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

data class PlxVfSentence(
    val provisionalVarioMps: Double?,
    val indicatedAirspeedKph: Double?,
    val pressureAltitudeM: Double?,
    override val checksumStatus: ChecksumStatus,
    override val receivedMonoMs: Long
) : ParsedLxSentence {
    override val sentenceId: LxSentenceId = LxSentenceId.PLXVF
}

data class LxWp2Sentence(
    val macCreadyMps: Double?,
    val ballastOverloadFactor: Double?,
    val bugsPercent: Int?,
    val polarA: Double?,
    val polarB: Double?,
    val polarC: Double?,
    val audioVolume: Int?,
    override val checksumStatus: ChecksumStatus,
    override val receivedMonoMs: Long
) : ParsedLxSentence {
    override val sentenceId: LxSentenceId = LxSentenceId.LXWP2
}

data class LxWp3Sentence(
    val altitudeOffsetFeet: Double?,
    val qnhHpa: Double?,
    val scMode: Double?,
    val varioFilter: Double?,
    val teFilter: Double?,
    val teLevel: Double?,
    val varioAverage: Double?,
    val varioRange: Double?,
    val scTab: Double?,
    val scLow: Double?,
    val scSpeed: Double?,
    val smartDiff: Double?,
    val gliderName: String?,
    val timeOffsetMinutes: Int?,
    override val checksumStatus: ChecksumStatus,
    override val receivedMonoMs: Long
) : ParsedLxSentence {
    override val sentenceId: LxSentenceId = LxSentenceId.LXWP3
}

data class PlxVsSentence(
    val outsideAirTemperatureC: Double?,
    val mode: Int?,
    val voltageV: Double?,
    override val checksumStatus: ChecksumStatus,
    override val receivedMonoMs: Long
) : ParsedLxSentence {
    override val sentenceId: LxSentenceId = LxSentenceId.PLXVS
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
