package com.example.xcpro.adsb.ui

import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.domain.AdsbAircraftClass
import com.example.xcpro.adsb.domain.classForAircraft
import com.example.xcpro.adsb.domain.classForCategory

/**
 * Deterministic mapping from OpenSky category to ADS-B icon class.
 * OpenSky categories are defined in the REST API docs (`extended=1`, index 17).
 */
fun iconForCategory(category: Int?): AdsbAircraftIcon = classForCategory(category).toUiIcon()

fun iconForAircraft(
    category: Int?,
    metadataTypecode: String?,
    metadataIcaoAircraftType: String?,
    icao24Raw: String? = null
): AdsbAircraftIcon = classForAircraft(
    category = category,
    metadataTypecode = metadataTypecode,
    metadataIcaoAircraftType = metadataIcaoAircraftType,
    icao24Raw = icao24Raw
).toUiIcon()

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

private fun AdsbAircraftClass.toUiIcon(): AdsbAircraftIcon = when (this) {
    AdsbAircraftClass.PlaneLight -> AdsbAircraftIcon.PlaneLight
    AdsbAircraftClass.PlaneMedium -> AdsbAircraftIcon.PlaneMedium
    AdsbAircraftClass.PlaneLarge -> AdsbAircraftIcon.PlaneLarge
    AdsbAircraftClass.PlaneHeavy -> AdsbAircraftIcon.PlaneHeavy
    AdsbAircraftClass.PlaneTwinJet -> AdsbAircraftIcon.PlaneTwinJet
    AdsbAircraftClass.PlaneTwinProp -> AdsbAircraftIcon.PlaneTwinProp
    AdsbAircraftClass.PlaneLargeIcaoOverride -> AdsbAircraftIcon.PlaneLargeIcaoOverride
    AdsbAircraftClass.Helicopter -> AdsbAircraftIcon.Helicopter
    AdsbAircraftClass.Glider -> AdsbAircraftIcon.Glider
    AdsbAircraftClass.Balloon -> AdsbAircraftIcon.Balloon
    AdsbAircraftClass.Parachutist -> AdsbAircraftIcon.Parachutist
    AdsbAircraftClass.Hangglider -> AdsbAircraftIcon.Hangglider
    AdsbAircraftClass.Drone -> AdsbAircraftIcon.Drone
    AdsbAircraftClass.Unknown -> AdsbAircraftIcon.Unknown
}
