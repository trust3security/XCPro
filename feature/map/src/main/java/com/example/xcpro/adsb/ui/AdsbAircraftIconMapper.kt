package com.example.xcpro.adsb.ui

import com.example.xcpro.adsb.AdsbTrafficUiModel
import java.util.Locale

/**
 * Deterministic mapping from OpenSky category to ADS-B icon class.
 * OpenSky categories are defined in the REST API docs (`extended=1`, index 17).
 */
fun iconForCategory(category: Int?): AdsbAircraftIcon = when (category) {
    2, 3 -> AdsbAircraftIcon.PlaneLight
    4, 5, 6, 7 -> AdsbAircraftIcon.PlaneLarge
    8 -> AdsbAircraftIcon.Helicopter
    9 -> AdsbAircraftIcon.Glider
    10 -> AdsbAircraftIcon.Balloon
    11 -> AdsbAircraftIcon.Parachutist
    12 -> AdsbAircraftIcon.Hangglider
    14 -> AdsbAircraftIcon.Drone
    else -> AdsbAircraftIcon.Unknown
}

fun iconForAircraft(
    category: Int?,
    metadataTypecode: String?,
    metadataIcaoAircraftType: String?
): AdsbAircraftIcon {
    val fromMetadata = iconFromIcaoMetadata(
        typecode = metadataTypecode,
        icaoAircraftType = metadataIcaoAircraftType
    )
    return fromMetadata ?: iconForCategory(category)
}

fun AdsbTrafficUiModel.aircraftIcon(): AdsbAircraftIcon = iconForAircraft(
    category = category,
    metadataTypecode = metadataTypecode,
    metadataIcaoAircraftType = metadataIcaoAircraftType
)

fun openSkyCategoryLabel(category: Int?): String = when (category) {
    0 -> "No category information"
    1 -> "No ADS-B category information"
    2 -> "Light"
    3 -> "Small"
    4 -> "Large"
    5 -> "High vortex large"
    6 -> "Heavy"
    7 -> "High performance"
    8 -> "Rotorcraft"
    9 -> "Glider"
    10 -> "Balloon"
    11 -> "Parachutist"
    12 -> "Ultralight / hang-glider"
    14 -> "UAV"
    null -> "Unknown"
    else -> "Unknown"
}

private fun iconFromIcaoMetadata(
    typecode: String?,
    icaoAircraftType: String?
): AdsbAircraftIcon? {
    val normalizedClass = icaoAircraftType
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { ICAO_AIRCRAFT_TYPE_REGEX.matches(it) }
    if (normalizedClass != null) {
        if (normalizedClass.startsWith("H")) {
            return AdsbAircraftIcon.Helicopter
        }
        val sizeClass = normalizedClass[1].digitToIntOrNull()
        return when (normalizedClass[2]) {
            'J' -> if ((sizeClass ?: 1) >= 2) {
                AdsbAircraftIcon.PlaneLarge
            } else {
                AdsbAircraftIcon.PlaneLight
            }

            'P', 'T' -> if ((sizeClass ?: 1) >= 3) {
                AdsbAircraftIcon.PlaneLarge
            } else {
                AdsbAircraftIcon.PlaneLight
            }

            else -> null
        }
    }

    val normalizedTypecode = typecode
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { it.isNotBlank() }
        ?: return null

    if (GLIDER_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) }) {
        return AdsbAircraftIcon.Glider
    }
    if (HELICOPTER_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) }) {
        return AdsbAircraftIcon.Helicopter
    }
    return null
}

private val ICAO_AIRCRAFT_TYPE_REGEX = Regex("[A-Z][0-9][A-Z]")

private val GLIDER_TYPECODE_PREFIXES = listOf(
    "GLID",
    "GLDR",
    "ASW",
    "ASK",
    "DG",
    "LS"
)

private val HELICOPTER_TYPECODE_PREFIXES = listOf(
    "R22",
    "R44",
    "R66",
    "EC",
    "AW",
    "H60",
    "UH"
)
