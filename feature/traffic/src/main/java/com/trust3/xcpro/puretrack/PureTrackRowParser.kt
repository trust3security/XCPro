package com.trust3.xcpro.puretrack

class PureTrackRowParser {

    fun parse(row: String): PureTrackParsedRow {
        if (row.isBlank()) {
            return dropped(PureTrackRowDropReason.EMPTY_ROW)
        }

        val fields = LinkedHashMap<Char, String>()
        val rawKeys = LinkedHashSet<Char>()
        var duplicateCount = 0

        row.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { token ->
                val key = token.first()
                val value = token.drop(1).trim()
                if (fields.containsKey(key)) duplicateCount += 1
                fields[key] = value
                rawKeys += key
            }

        val timestamp = fields['T']?.toLongOrNull()
            ?: return dropped(
                reason = if (fields.containsKey('T')) {
                    PureTrackRowDropReason.INVALID_TIMESTAMP
                } else {
                    PureTrackRowDropReason.MISSING_REQUIRED_FIELD
                },
                duplicateTokenCount = duplicateCount,
                rawFieldKeys = rawKeys
            )
        val latitude = fields['L']?.toDoubleOrNull()
        val longitude = fields['G']?.toDoubleOrNull()
        if (latitude == null || longitude == null) {
            return dropped(
                reason = PureTrackRowDropReason.MISSING_REQUIRED_FIELD,
                duplicateTokenCount = duplicateCount,
                rawFieldKeys = rawKeys
            )
        }
        if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0
        ) {
            return dropped(
                reason = PureTrackRowDropReason.INVALID_COORDINATE,
                duplicateTokenCount = duplicateCount,
                rawFieldKeys = rawKeys
            )
        }
        val key = fields['K']?.trim()
            ?: return dropped(
                reason = PureTrackRowDropReason.MISSING_REQUIRED_FIELD,
                duplicateTokenCount = duplicateCount,
                rawFieldKeys = rawKeys
            )
        if (key.isBlank()) {
            return dropped(
                reason = PureTrackRowDropReason.BLANK_KEY,
                duplicateTokenCount = duplicateCount,
                rawFieldKeys = rawKeys
            )
        }

        return PureTrackParsedRow(
            target = PureTrackTarget(
                key = key,
                sourceTimestampEpochSec = timestamp,
                latitude = latitude,
                longitude = longitude,
                altitudeMetersGps = fields['A'].parseDoubleOrNull(),
                altitudeMetersStandard = fields['t'].parseDoubleOrNull(),
                pressure = fields['P'].parseDoubleOrNull(),
                courseDegrees = fields['C'].parseDoubleOrNull()?.takeIf { it in 0.0..360.0 },
                speedMetersPerSecond = fields['S'].parseDoubleOrNull(),
                calculatedSpeedMetersPerSecond = fields['s'].parseDoubleOrNull(),
                verticalSpeedMetersPerSecond = fields['V'].parseDoubleOrNull(),
                objectTypeId = fields['O'].parseIntOrNull(),
                sourceTypeId = fields['U'].parseIntOrNull(),
                trackerUid = fields['D'].takeIfNotBlank(),
                targetId = fields['J'].takeIfNotBlank(),
                label = fields['B'].takeIfNotBlank(),
                name = fields['N'].takeIfNotBlank(),
                registration = fields['E'].takeIfNotBlank(),
                model = fields['M'].takeIfNotBlank(),
                callsign = fields['m'].takeIfNotBlank(),
                horizontalAccuracyMeters = fields['h'].parseDoubleOrNull(),
                verticalAccuracyMeters = fields['z'].parseDoubleOrNull(),
                onGround = fields['8'].toBooleanFlagOrNull(),
                rawFieldKeys = rawKeys
            ),
            dropReason = null,
            duplicateTokenCount = duplicateCount,
            rawFieldKeys = rawKeys
        )
    }

    private fun dropped(
        reason: PureTrackRowDropReason,
        duplicateTokenCount: Int = 0,
        rawFieldKeys: Set<Char> = emptySet()
    ): PureTrackParsedRow = PureTrackParsedRow(
        target = null,
        dropReason = reason,
        duplicateTokenCount = duplicateTokenCount,
        rawFieldKeys = rawFieldKeys
    )

    private fun String?.parseDoubleOrNull(): Double? = this?.toDoubleOrNull()
        ?.takeIf { it.isFinite() }

    private fun String?.parseIntOrNull(): Int? = this?.toIntOrNull()

    private fun String?.takeIfNotBlank(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private fun String?.toBooleanFlagOrNull(): Boolean? = when (this?.trim()?.lowercase()) {
        "1", "true" -> true
        "0", "false" -> false
        else -> null
    }
}
