package com.example.xcpro.adsb.domain

import java.util.Locale

/**
 * Domain-level aircraft family classification for ADS-B targets.
 * This logic is intentionally UI-agnostic so icon resource mapping can remain a thin UI adapter.
 */
enum class AdsbAircraftClass {
    PlaneLight,
    PlaneMedium,
    PlaneLarge,
    PlaneHeavy,
    PlaneTwinJet,
    PlaneTwinProp,
    PlaneLargeIcaoOverride,
    Helicopter,
    Glider,
    Balloon,
    Parachutist,
    Hangglider,
    Drone,
    Unknown
}

fun classForCategory(category: Int?): AdsbAircraftClass = when (category) {
    2, 3 -> AdsbAircraftClass.PlaneLight
    4, 5 -> AdsbAircraftClass.PlaneLarge
    6 -> AdsbAircraftClass.PlaneHeavy
    7 -> AdsbAircraftClass.PlaneTwinJet
    8 -> AdsbAircraftClass.Helicopter
    9 -> AdsbAircraftClass.Glider
    10 -> AdsbAircraftClass.Balloon
    11 -> AdsbAircraftClass.Parachutist
    12 -> AdsbAircraftClass.Hangglider
    14 -> AdsbAircraftClass.Drone
    else -> AdsbAircraftClass.Unknown
}

fun classForAircraft(
    category: Int?,
    metadataTypecode: String?,
    metadataIcaoAircraftType: String?,
    icao24Raw: String? = null
): AdsbAircraftClass {
    authoritativeCategoryClass(category)?.let { return it }

    val fromMetadata = classFromIcaoMetadata(
        typecode = metadataTypecode,
        icaoAircraftType = metadataIcaoAircraftType
    )
    if (fromMetadata == AdsbAircraftClass.PlaneHeavy) {
        return AdsbAircraftClass.PlaneHeavy
    }
    if (normalizeIcao24(icao24Raw) in LARGE_ICON_ICAO24_OVERRIDES) {
        return AdsbAircraftClass.PlaneLargeIcaoOverride
    }
    return fromMetadata ?: aircraftFallbackClassForCategory(category)
}

private fun authoritativeCategoryClass(category: Int?): AdsbAircraftClass? = when (category) {
    6, 8, 9, 10, 11, 12, 14 -> classForCategory(category)
    else -> null
}

private fun aircraftFallbackClassForCategory(category: Int?): AdsbAircraftClass = when (category) {
    // Keep the pure emitter-category table unchanged, but avoid a too-specific
    // light-aircraft silhouette when only coarse fixed-wing category data exists.
    2, 3 -> AdsbAircraftClass.PlaneMedium
    4, 5 -> AdsbAircraftClass.PlaneLarge
    6 -> AdsbAircraftClass.PlaneHeavy
    7 -> AdsbAircraftClass.PlaneTwinJet
    else -> classForCategory(category)
}

private fun classFromIcaoMetadata(
    typecode: String?,
    icaoAircraftType: String?
): AdsbAircraftClass? {
    val fromTypecode = typecode
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { it.isNotBlank() }
        ?.let(::classFromTypecode)
    val fromIcaoClass = classFromIcaoAircraftType(icaoAircraftType)

    if (fromIcaoClass == AdsbAircraftClass.Helicopter) {
        return AdsbAircraftClass.Helicopter
    }
    if (fromTypecode == null) {
        return fromIcaoClass
    }
    if (fromIcaoClass != null && fromTypecode.strength == MappingStrength.WeakFallback) {
        return fromIcaoClass
    }
    if (
        fromTypecode.aircraftClass == AdsbAircraftClass.PlaneLight &&
        fromIcaoClass == AdsbAircraftClass.PlaneTwinProp
    ) {
        return fromIcaoClass
    }
    return fromTypecode.aircraftClass
}

private fun classFromIcaoAircraftType(icaoAircraftType: String?): AdsbAircraftClass? {
    val normalizedClass = icaoAircraftType
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { ICAO_AIRCRAFT_TYPE_REGEX.matches(it) }
    if (normalizedClass != null) {
        if (normalizedClass.startsWith("H")) {
            return AdsbAircraftClass.Helicopter
        }
        val engineCount = normalizedClass[1].digitToIntOrNull() ?: 1
        return when (normalizedClass[2]) {
            'J' -> when {
                engineCount >= 4 -> AdsbAircraftClass.PlaneHeavy
                engineCount == 2 -> AdsbAircraftClass.PlaneTwinJet
                engineCount == 3 -> AdsbAircraftClass.PlaneLarge
                else -> AdsbAircraftClass.PlaneLight
            }

            'P', 'T' -> when {
                engineCount == 2 -> AdsbAircraftClass.PlaneTwinProp
                engineCount >= 3 -> AdsbAircraftClass.PlaneLarge
                else -> AdsbAircraftClass.PlaneLight
            }

            else -> null
        }
    }

    return null
}

private fun classFromTypecode(normalizedTypecode: String): TypecodeClassification? {
    if (GLIDER_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) }) {
        return TypecodeClassification(AdsbAircraftClass.Glider, MappingStrength.Strong)
    }
    if (HELICOPTER_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) }) {
        return TypecodeClassification(AdsbAircraftClass.Helicopter, MappingStrength.Strong)
    }
    if (FIXED_WING_TYPECODE_REGEX.matches(normalizedTypecode) && normalizedTypecode.any { it.isDigit() }) {
        return when {
            FOUR_ENGINE_JET_TYPECODE_EXACT.contains(normalizedTypecode) ->
                TypecodeClassification(AdsbAircraftClass.PlaneHeavy, MappingStrength.Strong)

            FOUR_ENGINE_JET_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) } ->
                TypecodeClassification(AdsbAircraftClass.PlaneHeavy, MappingStrength.Strong)

            TWIN_JET_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) } ->
                TypecodeClassification(AdsbAircraftClass.PlaneTwinJet, MappingStrength.Strong)

            LARGE_FIXED_WING_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) } ->
                TypecodeClassification(AdsbAircraftClass.PlaneLarge, MappingStrength.Strong)

            TWIN_PROP_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) } ->
                TypecodeClassification(AdsbAircraftClass.PlaneTwinProp, MappingStrength.Strong)

            MEDIUM_FIXED_WING_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) } ->
                TypecodeClassification(AdsbAircraftClass.PlaneMedium, MappingStrength.Strong)

            LIGHT_FIXED_WING_TYPECODE_PREFIXES.any { normalizedTypecode.startsWith(it) } ||
                LIGHT_FIXED_WING_TYPECODE_EXACT.contains(normalizedTypecode) ->
                TypecodeClassification(AdsbAircraftClass.PlaneLight, MappingStrength.Strong)

            else -> TypecodeClassification(AdsbAircraftClass.PlaneMedium, MappingStrength.WeakFallback)
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
    "UH",
    "B06",
    "B47",
    "B407",
    "B429",
    "A109",
    "A119",
    "A139",
    "AS50",
    "H269",
    "H500",
    "MI8",
    "MI17",
    "S76",
    "S92"
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
    "B37",
    "B38",
    "B39",
    "B3X",
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
    "BE20",
    "BE30",
    "BE5",
    "BE6",
    "BE9",
    "B19",
    "B20",
    "B30",
    "B35",
    "B360",
    "C90",
    "DA42",
    "DH8",
    "E90",
    "F90",
    "P68",
    "PA34",
    "PA44"
)

private val MEDIUM_FIXED_WING_TYPECODE_PREFIXES = listOf(
    "C27",
    "C29",
    "C295",
    "L410",
    "SF34"
)

private val LIGHT_FIXED_WING_TYPECODE_EXACT = setOf(
    "BE35",
    "BE36",
    "C152",
    "C172",
    "C182",
    "C208",
    "C210",
    "SR20",
    "SR22"
)

private val LIGHT_FIXED_WING_TYPECODE_PREFIXES = listOf(
    "C15",
    "C17",
    "C18",
    "C20",
    "C21",
    "M20",
    "P28",
    "PA18",
    "PA24",
    "PA28",
    "PA32",
    "SR2"
)

private data class TypecodeClassification(
    val aircraftClass: AdsbAircraftClass,
    val strength: MappingStrength
)

private enum class MappingStrength {
    Strong,
    WeakFallback
}
