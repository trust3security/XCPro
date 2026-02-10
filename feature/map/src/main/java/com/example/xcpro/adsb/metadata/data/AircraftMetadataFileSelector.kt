package com.example.xcpro.adsb.metadata.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AircraftMetadataFileSelector @Inject constructor() {

    fun selectLatestCompleteKey(keys: List<String>): String? {
        var best: Pair<Int, Int>? = null
        var bestKey: String? = null
        keys.forEach { key ->
            val match = COMPLETE_FILE_REGEX.matchEntire(key) ?: return@forEach
            val year = match.groupValues[1].toIntOrNull() ?: return@forEach
            val month = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (month !in 1..12) {
                return@forEach
            }
            val candidate = year to month
            val shouldReplace = best == null ||
                candidate.first > best!!.first ||
                (candidate.first == best!!.first && candidate.second > best!!.second)
            if (shouldReplace) {
                best = candidate
                bestKey = key
            }
        }
        return bestKey
    }

    private companion object {
        val COMPLETE_FILE_REGEX =
            Regex("^metadata/aircraft-database-complete-(\\d{4})-(\\d{2})\\.csv$")
    }
}
