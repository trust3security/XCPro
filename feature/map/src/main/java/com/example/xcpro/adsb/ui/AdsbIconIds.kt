package com.example.xcpro.adsb.ui

object AdsbIconIds {
    const val SMALL_SINGLE_ENGINE = "adsb_icon_small_single_engine"
    const val SMALL_JET = "adsb_icon_small_jet"
    const val LARGE_JET = "adsb_icon_large_jet"
    const val HELICOPTER = "adsb_icon_helicopter"
    const val GLIDER = "adsb_icon_glider"
    const val UNKNOWN = "adsb_icon_unknown"
}

fun AdsbAircraftKind.toAdsbIconId(): String = when (this) {
    AdsbAircraftKind.SmallSingleEngine -> AdsbIconIds.SMALL_SINGLE_ENGINE
    AdsbAircraftKind.SmallJet -> AdsbIconIds.SMALL_JET
    AdsbAircraftKind.LargeJet -> AdsbIconIds.LARGE_JET
    AdsbAircraftKind.Helicopter -> AdsbIconIds.HELICOPTER
    AdsbAircraftKind.Glider -> AdsbIconIds.GLIDER
    AdsbAircraftKind.Unknown -> AdsbIconIds.UNKNOWN
}

