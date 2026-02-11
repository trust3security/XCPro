package com.example.xcpro.adsb.metadata.data

import javax.inject.Inject
import javax.inject.Singleton

data class ParsedMetadataRecord(
    val icao24: String,
    val registration: String?,
    val typecode: String?,
    val model: String?,
    val manufacturerName: String?,
    val owner: String?,
    val operator: String?,
    val operatorCallsign: String?,
    val icaoAircraftType: String?,
    val qualityScore: Int,
    val sourceRowOrder: Long
)

data class CsvHeaderMapping(
    val icao24Index: Int,
    val registrationIndex: Int?,
    val typecodeIndex: Int?,
    val modelIndex: Int?,
    val manufacturerNameIndex: Int?,
    val ownerIndex: Int?,
    val operatorIndex: Int?,
    val operatorCallsignIndex: Int?,
    val icaoAircraftTypeIndex: Int?
)

@Singleton
class AircraftMetadataCsvParser @Inject constructor() {

    fun parseHeader(rawLine: String): CsvHeaderMapping {
        val headerCells = parseCsvLine(rawLine)
        if (headerCells.isEmpty()) {
            throw IllegalArgumentException("CSV header is empty")
        }
        val normalized = headerCells.mapIndexed { index, value ->
            val sanitized = value
                .removePrefix("\uFEFF")
                .trim()
            sanitizeHeaderKey(sanitized) to index
        }.toMap()

        val icao24Index = firstIndexForAliases(normalized, ICAO24_ALIASES)
            ?: throw IllegalArgumentException("CSV header missing ICAO24 column")

        return CsvHeaderMapping(
            icao24Index = icao24Index,
            registrationIndex = firstIndexForAliases(normalized, REGISTRATION_ALIASES),
            typecodeIndex = firstIndexForAliases(normalized, TYPECODE_ALIASES),
            modelIndex = firstIndexForAliases(normalized, MODEL_ALIASES),
            manufacturerNameIndex = firstIndexForAliases(normalized, MANUFACTURER_ALIASES),
            ownerIndex = firstIndexForAliases(normalized, OWNER_ALIASES),
            operatorIndex = firstIndexForAliases(normalized, OPERATOR_ALIASES),
            operatorCallsignIndex = firstIndexForAliases(normalized, OPERATOR_CALLSIGN_ALIASES),
            icaoAircraftTypeIndex = firstIndexForAliases(normalized, ICAO_AIRCRAFT_TYPE_ALIASES)
        )
    }

    fun parseRecord(
        rawLine: String,
        mapping: CsvHeaderMapping,
        sourceRowOrder: Long
    ): ParsedMetadataRecord? {
        val cells = parseCsvLine(rawLine)
        if (cells.isEmpty()) {
            return null
        }

        val icao24 = normalizeIcao24(valueAt(cells, mapping.icao24Index)) ?: return null
        val registration = normalizeText(valueAt(cells, mapping.registrationIndex))
        val typecode = normalizeText(valueAt(cells, mapping.typecodeIndex))
        val model = normalizeText(valueAt(cells, mapping.modelIndex))
        val manufacturerName = normalizeText(valueAt(cells, mapping.manufacturerNameIndex))
        val owner = normalizeText(valueAt(cells, mapping.ownerIndex))
        val operator = normalizeText(valueAt(cells, mapping.operatorIndex))
        val operatorCallsign = normalizeText(valueAt(cells, mapping.operatorCallsignIndex))
        val icaoAircraftType = normalizeText(valueAt(cells, mapping.icaoAircraftTypeIndex))
        val qualityScore = qualityScore(registration, typecode, model)

        return ParsedMetadataRecord(
            icao24 = icao24,
            registration = registration,
            typecode = typecode,
            model = model,
            manufacturerName = manufacturerName,
            owner = owner,
            operator = operator,
            operatorCallsign = operatorCallsign,
            icaoAircraftType = icaoAircraftType,
            qualityScore = qualityScore,
            sourceRowOrder = sourceRowOrder
        )
    }

    internal fun parseCsvLine(line: String): List<String> {
        if (line.isEmpty()) {
            return emptyList()
        }
        val result = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val c = line[index]
            when {
                c == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }

                c == '"' -> {
                    inQuotes = !inQuotes
                }

                c == ',' && !inQuotes -> {
                    result += current.toString()
                    current.setLength(0)
                }

                else -> {
                    current.append(c)
                }
            }
            index += 1
        }
        result += current.toString()
        return result
    }

    private fun valueAt(cells: List<String>, index: Int?): String? {
        if (index == null || index < 0 || index >= cells.size) {
            return null
        }
        return cells[index]
    }

    private fun normalizeIcao24(raw: String?): String? {
        return raw
            ?.trim()
            ?.lowercase()
            ?.takeIf { ICAO24_REGEX.matches(it) }
    }

    private fun normalizeText(raw: String?): String? {
        return raw?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun qualityScore(
        registration: String?,
        typecode: String?,
        model: String?
    ): Int {
        var score = 0
        if (!registration.isNullOrBlank()) score += 1
        if (!typecode.isNullOrBlank()) score += 1
        if (!model.isNullOrBlank()) score += 1
        return score
    }

    private fun sanitizeHeaderKey(raw: String): String {
        return raw
            .trim()
            .lowercase()
            .replace("[^a-z0-9]".toRegex(), "")
    }

    private fun firstIndexForAliases(
        indexedHeaders: Map<String, Int>,
        aliases: Set<String>
    ): Int? {
        aliases.forEach { alias ->
            indexedHeaders[alias]?.let { return it }
        }
        return null
    }

    private companion object {
        val ICAO24_REGEX = Regex("[0-9a-f]{6}")

        val ICAO24_ALIASES = setOf("icao24", "icao")
        val REGISTRATION_ALIASES = setOf("registration", "tailnumber", "tail")
        val TYPECODE_ALIASES = setOf("typecode", "type")
        val MODEL_ALIASES = setOf("model", "aircraftmodel")
        val MANUFACTURER_ALIASES = setOf("manufacturername", "manufacturer")
        val OWNER_ALIASES = setOf("owner")
        val OPERATOR_ALIASES = setOf("operator")
        val OPERATOR_CALLSIGN_ALIASES = setOf("operatorcallsign")
        val ICAO_AIRCRAFT_TYPE_ALIASES = setOf("icaoaircrafttype")
    }
}
