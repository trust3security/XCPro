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
    Hangglider(
        resId = R.drawable.ic_ogn_hangglider,
        styleImageId = "ogn_icon_hangglider"
    )
}

/**
 * OGN DDB aircraft type codes follow the FLARM/OGN enum:
 * 2 = tow/tug aircraft,
 * 6 = hang glider, 7 = paraglider.
 */
fun iconForOgnAircraftTypeCode(aircraftTypeCode: Int?): OgnAircraftIcon = when (aircraftTypeCode) {
    OGN_AIRCRAFT_TYPE_TUGPLANE -> OgnAircraftIcon.Tugplane
    OGN_AIRCRAFT_TYPE_HANG_GLIDER -> OgnAircraftIcon.Hangglider
    OGN_AIRCRAFT_TYPE_PARAGLIDER -> OgnAircraftIcon.Hangglider
    else -> OgnAircraftIcon.Glider
}

private const val OGN_AIRCRAFT_TYPE_TUGPLANE = 2
private const val OGN_AIRCRAFT_TYPE_HANG_GLIDER = 6
private const val OGN_AIRCRAFT_TYPE_PARAGLIDER = 7
