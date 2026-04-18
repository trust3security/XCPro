package com.trust3.xcpro.adsb

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser

object OpenSkyStateVectorMapper {
    private const val IDX_ICAO24 = 0
    private const val IDX_CALLSIGN = 1
    private const val IDX_TIME_POSITION = 3
    private const val IDX_LAST_CONTACT = 4
    private const val IDX_LONGITUDE = 5
    private const val IDX_LATITUDE = 6
    private const val IDX_BARO_ALTITUDE = 7
    private const val IDX_VELOCITY = 9
    private const val IDX_TRUE_TRACK = 10
    private const val IDX_VERTICAL_RATE = 11
    private const val IDX_GEO_ALTITUDE = 13
    private const val IDX_POSITION_SOURCE = 16
    private const val IDX_CATEGORY = 17

    fun parseResponse(json: String): OpenSkyResponse {
        val root = JsonParser.parseString(json).asJsonObject
        val timeSec = root.get("time").asLongOrNull()
        val statesRaw = root.get("states")
        val statesArray = if (statesRaw != null && statesRaw.isJsonArray) {
            statesRaw.asJsonArray
        } else {
            JsonArray()
        }
        val states = buildList {
            for (entry in statesArray) {
                val row = entry.takeIf { it.isJsonArray }?.asJsonArray ?: continue
                val mapped = mapRow(row) ?: continue
                add(mapped)
            }
        }
        return OpenSkyResponse(timeSec = timeSec, states = states)
    }

    internal fun mapRow(row: JsonArray): OpenSkyStateVector? {
        val icao24 = row.stringAt(IDX_ICAO24)?.trim()?.lowercase() ?: return null
        if (!icao24.matches(Regex("[0-9a-f]{6}"))) return null

        return OpenSkyStateVector(
            icao24 = icao24,
            callsign = row.stringAt(IDX_CALLSIGN)?.trim()?.takeIf { it.isNotBlank() },
            timePositionSec = row.longAt(IDX_TIME_POSITION),
            lastContactSec = row.longAt(IDX_LAST_CONTACT),
            longitude = row.doubleAt(IDX_LONGITUDE),
            latitude = row.doubleAt(IDX_LATITUDE),
            baroAltitudeM = row.doubleAt(IDX_BARO_ALTITUDE),
            velocityMps = row.doubleAt(IDX_VELOCITY),
            trueTrackDeg = row.doubleAt(IDX_TRUE_TRACK),
            verticalRateMps = row.doubleAt(IDX_VERTICAL_RATE),
            geoAltitudeM = row.doubleAt(IDX_GEO_ALTITUDE),
            positionSource = row.intAt(IDX_POSITION_SOURCE),
            category = row.intAt(IDX_CATEGORY)
        )
    }

    private fun JsonArray.stringAt(index: Int): String? {
        if (index < 0 || index >= size()) return null
        return get(index).asStringOrNull()
    }

    private fun JsonArray.longAt(index: Int): Long? {
        if (index < 0 || index >= size()) return null
        return get(index).asLongOrNull()
    }

    private fun JsonArray.intAt(index: Int): Int? {
        if (index < 0 || index >= size()) return null
        return get(index).asIntOrNull()
    }

    private fun JsonArray.doubleAt(index: Int): Double? {
        if (index < 0 || index >= size()) return null
        return get(index).asDoubleOrNull()
    }

    private fun JsonElement.asStringOrNull(): String? {
        if (isJsonNull || !isJsonPrimitive) return null
        return runCatching { asString }.getOrNull()
    }

    private fun JsonElement.asLongOrNull(): Long? {
        if (isJsonNull || !isJsonPrimitive) return null
        return runCatching { asLong }.getOrNull()
    }

    private fun JsonElement.asIntOrNull(): Int? {
        if (isJsonNull || !isJsonPrimitive) return null
        return runCatching { asInt }.getOrNull()
    }

    private fun JsonElement.asDoubleOrNull(): Double? {
        if (isJsonNull || !isJsonPrimitive) return null
        return runCatching { asDouble }.getOrNull()
    }
}

