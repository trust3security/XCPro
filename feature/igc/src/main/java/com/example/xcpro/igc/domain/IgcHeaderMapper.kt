package com.example.xcpro.igc.domain

import java.time.format.DateTimeFormatter

/**
 * Deterministic mapper for required IGC H-record coverage.
 */
class IgcHeaderMapper {

    fun map(context: IgcHeaderContext): List<IgcRecordFormatter.HeaderRecord> {
        val profile = context.profileMetadata
        val recorder = context.recorderMetadata
        val date = DATE_FORMAT.format(context.utcDate)
        val flightNumber = context.flightNumberOfDay.coerceIn(1, 99).toString().padStart(2, '0')

        return listOf(
            header("DTE", "DATE", "$date,$flightNumber"),
            header("PLT", "PILOTINCHARGE", valueOrNkn(profile?.pilotName)),
            header("CM2", "CREW2", valueOrNil(profile?.crew2)),
            header("GTY", "GLIDERTYPE", valueOrNkn(profile?.gliderType)),
            header("GID", "GLIDERID", valueOrNkn(profile?.gliderId)),
            header("DTM", "GPSDATUM", "WGS84"),
            header("RFW", "FIRMWAREVERSION", valueOrNkn(recorder.firmwareVersion)),
            header("RHW", "HARDWAREVERSION", valueOrNkn(recorder.hardwareVersion)),
            header("FTY", "FRTYPE", valueOrNkn(recorder.recorderType)),
            header("GPS", "RECEIVER", valueOrNkn(recorder.gpsReceiver)),
            header("PRS", "PRESSALTSENSOR", valueOrNkn(recorder.pressureSensor)),
            header("FRS", "SECURITY", valueOrNkn(recorder.securityStatus)),
            header("ALG", "ALTGPS", recorder.gpsAltitudeDatum.value),
            header("ALP", "ALTPRESSURE", recorder.pressureAltitudeDatum.value)
        )
    }

    private fun header(
        code: String,
        longName: String,
        value: String
    ): IgcRecordFormatter.HeaderRecord {
        return IgcRecordFormatter.HeaderRecord(
            source = 'F',
            code = code,
            longName = longName,
            value = sanitize(value)
        )
    }

    private fun valueOrNkn(value: String?): String {
        val normalized = sanitize(value.orEmpty())
        return if (normalized.isBlank()) "NKN" else normalized
    }

    private fun valueOrNil(value: String?): String {
        val normalized = sanitize(value.orEmpty())
        return if (normalized.isBlank()) "NIL" else normalized
    }

    private fun sanitize(value: String): String {
        return value
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .let { if (it.length <= MAX_VALUE_LENGTH) it else it.take(MAX_VALUE_LENGTH) }
    }

    companion object {
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMyy")
        private const val MAX_VALUE_LENGTH = 64
    }
}
