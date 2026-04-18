package com.trust3.xcpro.adsb.ui

import androidx.annotation.DrawableRes
import com.trust3.xcpro.traffic.R

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
    PlaneMedium(
        resId = R.drawable.ic_adsb_plane_medium,
        styleImageId = "adsb_icon_plane_medium"
    ),
    PlaneLarge(
        resId = R.drawable.ic_adsb_plane_large,
        styleImageId = "adsb_icon_plane_large"
    ),
    PlaneHeavy(
        resId = R.drawable.ic_adsb_plane_heavy,
        styleImageId = "adsb_icon_plane_heavy"
    ),
    PlaneTwinJet(
        resId = R.drawable.ic_adsb_jet_twin,
        styleImageId = "adsb_icon_jet_twin"
    ),
    PlaneTwinProp(
        resId = R.drawable.ic_adsb_twinprop,
        styleImageId = "adsb_icon_twinprop"
    ),
    PlaneLargeIcaoOverride(
        resId = R.drawable.ic_adsb_plane_large,
        styleImageId = "adsb_icon_plane_large_icao_override"
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
        // Keep unknown semantics while using a neutral fixed-wing visual fallback.
        resId = R.drawable.ic_adsb_plane_medium,
        styleImageId = "adsb_icon_unknown"
    )
}

fun AdsbAircraftIcon.emergencyStyleImageId(): String = "${styleImageId}_emergency"

fun AdsbAircraftIcon.displayLabel(): String = when (this) {
    AdsbAircraftIcon.PlaneLight -> "Light aircraft"
    AdsbAircraftIcon.PlaneMedium -> "Medium aircraft"
    AdsbAircraftIcon.PlaneLarge -> "Large aircraft"
    AdsbAircraftIcon.PlaneHeavy -> "Heavy aircraft"
    AdsbAircraftIcon.PlaneTwinJet -> "Twin-jet aircraft"
    AdsbAircraftIcon.PlaneTwinProp -> "Twin-prop aircraft"
    AdsbAircraftIcon.PlaneLargeIcaoOverride -> "Large aircraft"
    AdsbAircraftIcon.Helicopter -> "Helicopter"
    AdsbAircraftIcon.Glider -> "Glider"
    AdsbAircraftIcon.Balloon -> "Balloon"
    AdsbAircraftIcon.Parachutist -> "Parachutist"
    AdsbAircraftIcon.Hangglider -> "Hangglider"
    AdsbAircraftIcon.Drone -> "Drone"
    AdsbAircraftIcon.Unknown -> "Unknown"
}
