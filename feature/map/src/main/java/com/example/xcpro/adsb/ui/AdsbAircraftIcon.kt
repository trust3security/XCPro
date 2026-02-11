package com.example.xcpro.adsb.ui

import androidx.annotation.DrawableRes
import com.example.xcpro.map.R

/**
 * UI-level ADS-B icon model used by MapLibre style image registration and feature mapping.
 */
enum class AdsbAircraftIcon(
    @DrawableRes val resId: Int,
    val styleImageId: String
) {
    PlaneLight(
        resId = R.drawable.ic_adsb_plane_light,
        styleImageId = "adsb_icon_plane_light"
    ),
    PlaneLarge(
        resId = R.drawable.ic_adsb_plane_large,
        styleImageId = "adsb_icon_plane_large"
    ),
    Helicopter(
        resId = R.drawable.ic_adsb_helicopter,
        styleImageId = "adsb_icon_helicopter"
    ),
    Glider(
        resId = R.drawable.ic_adsb_glider,
        styleImageId = "adsb_icon_glider"
    ),
    Balloon(
        resId = R.drawable.ic_adsb_balloon,
        styleImageId = "adsb_icon_balloon"
    ),
    Parachutist(
        resId = R.drawable.ic_adsb_parachutist_symbol,
        styleImageId = "adsb_icon_parachutist"
    ),
    Hangglider(
        resId = R.drawable.ic_adsb_hangglider_symbol,
        styleImageId = "adsb_icon_hangglider"
    ),
    Drone(
        resId = R.drawable.ic_adsb_drone,
        styleImageId = "adsb_icon_drone"
    ),
    Unknown(
        resId = R.drawable.ic_adsb_unknown,
        styleImageId = "adsb_icon_unknown"
    )
}

fun AdsbAircraftIcon.emergencyStyleImageId(): String = "${styleImageId}_emergency"

fun AdsbAircraftIcon.displayLabel(): String = when (this) {
    AdsbAircraftIcon.PlaneLight -> "Light aircraft"
    AdsbAircraftIcon.PlaneLarge -> "Large aircraft"
    AdsbAircraftIcon.Helicopter -> "Helicopter"
    AdsbAircraftIcon.Glider -> "Glider"
    AdsbAircraftIcon.Balloon -> "Balloon"
    AdsbAircraftIcon.Parachutist -> "Parachutist"
    AdsbAircraftIcon.Hangglider -> "Hangglider"
    AdsbAircraftIcon.Drone -> "Drone"
    AdsbAircraftIcon.Unknown -> "Unknown"
}
