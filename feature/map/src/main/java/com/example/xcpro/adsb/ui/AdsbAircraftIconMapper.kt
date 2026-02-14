package com.example.xcpro.adsb.ui

import com.example.xcpro.adsb.AdsbTrafficUiModel
import java.util.Locale

/**
 * Deterministic mapping from OpenSky category to ADS-B icon class.
 * OpenSky categories are defined in the REST API docs (`extended=1`, index 17).
 */
fun iconForCategory(category: Int?): AdsbAircraftIcon = when (category) {
    2, 3 -> AdsbAircraftIcon.PlaneLight
    4 -> AdsbAircraftIcon.PlaneMedium
    5, 7 -> AdsbAircraftIcon.PlaneLarge
    6 -> AdsbAircraftIcon.PlaneHeavy
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
    metadataIcaoAircraftType: String?,
    icao24Raw: String? = null
): AdsbAircraftIcon {
    if (category == 6) {
        return AdsbAircraftIcon.PlaneHeavy
    }
    val fromMetadata = iconFromIcaoMetadata(
        typecode = metadataTypecode,
        icaoAircraftType = metadataIcaoAircraftType
    )
    if (fromMetadata == AdsbAircraftIcon.PlaneHeavy) {
        return AdsbAircraftIcon.PlaneHeavy
    }
    if (normalizeIcao24(icao24Raw) in LARGE_ICON_ICAO24_OVERRIDES) {
        return AdsbAircraftIcon.PlaneLargeIcaoOverride
    }
    return fromMetadata ?: iconForCategory(category)
}

fun AdsbTrafficUiModel.aircraftIcon(): AdsbAircraftIcon = iconForAircraft(
    category = category,
    metadataTypecode = metadataTypecode,
    metadataIcaoAircraftType = metadataIcaoAircraftType,
    icao24Raw = id.raw
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
    val fromTypecode = typecode
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { it.isNotBlank() }
        ?.let(::iconFromTypecode)
    val fromIcaoClass = iconFromIcaoAircraftType(icaoAircraftType)

    if (fromTypecode != null) {
        if (fromTypecode == AdsbAircraftIcon.PlaneLight && fromIcaoClass == AdsbAircraftIcon.PlaneTwinProp) {
            return fromIcaoClass
        }
        return fromTypecode
    }

    return fromIcaoClass
}

private fun iconFromIcaoAircraftType(icaoAircraftType: String?): AdsbAircraftIcon? {
    val normalizedClass = icaoAircraftType
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { ICAO_AIRCRAFT_TYPE_REGEX.matches(it) }
    if (normalizedClass != null) {
        if (normalizedClass.startsWith("H")) {
            return AdsbAircraftIcon.Helicopter
        }
        val engineCount = normalizedClass[1].digitToIntOrNull() ?: 1
        return when (normalizedClass[2]) {
            'J' -> when {
                engineCount >= 4 -> AdsbAircraftIcon.PlaneHeavy
                engineCount == 2 -> AdsbAircraftIcon.PlaneTwinJet
                engineCount == 3 -> AdsbAircraftIcon.PlaneLarge
                else -> AdsbAircraftIcon.PlaneLight
            }

            'P', 'T' -> when {
                engineCount == 2 -> AdsbAircraftIcon.PlaneTwinProp
                engineCount >= 3 -> AdsbAircraftIcon.PlaneLarge
                else -> AdsbAircraftIcon.PlaneLight
            }

            else -> null
        }
    }

    return null
}

private fun iconFromTypecode(normalizedTypecode: String): AdsbAircraftIcon? {
    if (GLIDER_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) }) {
        return AdsbAircraftIcon.Glider
    }
    if (HELICOPTER_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) }) {
        return AdsbAircraftIcon.Helicopter
    }
    if (FIXED_WING_TYPECODE_REGEX.matches(normalizedTypecode) && normalizedTypecode.any { it.isDigit() }) {
        return when {
            FOUR_ENGINE_JET_TYPECODE_EXACT.contains(normalizedTypecode) ->
                AdsbAircraftIcon.PlaneHeavy

            FOUR_ENGINE_JET_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) } ->
                AdsbAircraftIcon.PlaneHeavy

            TWIN_JET_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) } ->
                AdsbAircraftIcon.PlaneTwinJet

            LARGE_FIXED_WING_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) } ->
                AdsbAircraftIcon.PlaneLarge

            TWIN_PROP_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) } ->
                AdsbAircraftIcon.PlaneTwinProp

            MEDIUM_FIXED_WING_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) } ->
                AdsbAircraftIcon.PlaneMedium

            else -> AdsbAircraftIcon.PlaneLight
        }
    }
    return null
}

private fun normalizeIcao24(raw: String?): String? {
    return raw
        ?.trim()
        ?.lowercase(Locale.US)
        ?.takeIf { ICAO24_REGEX.matches(it) }
}

private val ICAO24_REGEX = Regex("[0-9a-f]{6}")
private val ICAO_AIRCRAFT_TYPE_REGEX = Regex("[A-Z][0-9][A-Z]")
private val FIXED_WING_TYPECODE_REGEX = Regex("[A-Z][A-Z0-9]{2,3}")
private val LARGE_ICON_ICAO24_OVERRIDES = setOf(
    "7c7c77",
    "7c6c90"
)

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

private val FOUR_ENGINE_JET_TYPECODE_PREFIXES = listOf(
    "A38",
    "A34",
    "B74"
)

private val FOUR_ENGINE_JET_TYPECODE_EXACT = setOf(
    "C17"
)

private val TWIN_JET_TYPECODE_PREFIXES = listOf(
    "A2",
    "A3",
    "B73",
    "B75",
    "B76",
    "B77",
    "B78",
    "B79",
    "E17",
    "E18",
    "E19",
    "CRJ",
    "F70",
    "F100"
)

private val LARGE_FIXED_WING_TYPECODE_PREFIXES = listOf(
    "A39",
    "MD11",
    "DC10",
    "L101"
)

private val TWIN_PROP_TYPECODE_PREFIXES = listOf(
    "AT7",
    "AT8",
    "DH8"
)

private val MEDIUM_FIXED_WING_TYPECODE_PREFIXES = listOf(
    "AT7",
    "AT8",
    "DH8"
)
