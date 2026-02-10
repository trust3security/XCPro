package com.example.xcpro.adsb.ui

import com.example.xcpro.adsb.AdsbTrafficUiModel

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

fun AdsbTrafficUiModel.aircraftIcon(): AdsbAircraftIcon = iconForCategory(category)

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
