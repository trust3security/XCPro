package com.example.xcpro.ogn

import androidx.annotation.DrawableRes
import com.example.xcpro.map.R

/**
 * UI-level OGN icon model used by MapLibre style image registration.
 */
enum class OgnAircraftIcon(
    @DrawableRes val resId: Int,
    val styleImageId: String
) {
    Glider(
        resId = R.drawable.ic_adsb_glider,
        styleImageId = "ogn_icon_glider"
    ),
    Tugplane(
        resId = R.drawable.ic_ogn_tug,
        styleImageId = "ogn_icon_tug"
    ),
    Helicopter(
        resId = R.drawable.ic_adsb_helicopter,
        styleImageId = "ogn_icon_helicopter"
    ),
    Paraglider(
        resId = R.drawable.ic_ogn_hangglider,
        styleImageId = "ogn_icon_paraglider"
    ),
    Hangglider(
        resId = R.drawable.ic_ogn_hangglider,
        styleImageId = "ogn_icon_hangglider"
    ),
    Balloon(
        resId = R.drawable.ic_adsb_balloon,
        styleImageId = "ogn_icon_balloon"
    ),
    Uav(
        resId = R.drawable.ic_adsb_drone,
        styleImageId = "ogn_icon_uav"
    ),
    Unknown(
        resId = R.drawable.ic_ogn_ufo,
        styleImageId = "ogn_icon_unknown"
    )
}

/**
 * OGN DDB aircraft type codes follow the FLARM/OGN enum (typical):
 * 1 = glider (sailplane),
 * 2 = tow/tug aircraft,
 * 3 = helicopter,
 * 4 = paraglider,
 * 5 = hang glider,
 * 6 = balloon,
 * 7 = UAV,
 * 8 = static object.
 * Unknown or unsupported codes map to Unknown (UFO).
 */
fun iconForOgnAircraftTypeCode(aircraftTypeCode: Int?): OgnAircraftIcon = when (aircraftTypeCode) {
    OGN_AIRCRAFT_TYPE_GLIDER -> OgnAircraftIcon.Glider
    OGN_AIRCRAFT_TYPE_TUGPLANE -> OgnAircraftIcon.Tugplane
    OGN_AIRCRAFT_TYPE_HELICOPTER -> OgnAircraftIcon.Helicopter
    OGN_AIRCRAFT_TYPE_PARAGLIDER -> OgnAircraftIcon.Paraglider
    OGN_AIRCRAFT_TYPE_HANG_GLIDER -> OgnAircraftIcon.Hangglider
    OGN_AIRCRAFT_TYPE_BALLOON -> OgnAircraftIcon.Balloon
    OGN_AIRCRAFT_TYPE_UAV -> OgnAircraftIcon.Uav
    OGN_AIRCRAFT_TYPE_STATIC_OBJECT -> OgnAircraftIcon.Unknown
    else -> OgnAircraftIcon.Unknown
}

private const val OGN_AIRCRAFT_TYPE_GLIDER = 1
private const val OGN_AIRCRAFT_TYPE_TUGPLANE = 2
private const val OGN_AIRCRAFT_TYPE_HELICOPTER = 3
private const val OGN_AIRCRAFT_TYPE_PARAGLIDER = 4
private const val OGN_AIRCRAFT_TYPE_HANG_GLIDER = 5
private const val OGN_AIRCRAFT_TYPE_BALLOON = 6
private const val OGN_AIRCRAFT_TYPE_UAV = 7
private const val OGN_AIRCRAFT_TYPE_STATIC_OBJECT = 8
