package com.example.xcpro.adsb.ui

/**
 * UI-only ADS-B aircraft visual categories derived from provider metadata.
 */
enum class AdsbAircraftKind {
    SmallSingleEngine,
    SmallJet,
    LargeJet,
    Helicopter,
    Glider,
    Unknown
}

fun AdsbAircraftKind.displayLabel(): String = when (this) {
    AdsbAircraftKind.SmallSingleEngine -> "Small single engine"
    AdsbAircraftKind.SmallJet -> "Small jet"
    AdsbAircraftKind.LargeJet -> "Large jet"
    AdsbAircraftKind.Helicopter -> "Helicopter"
    AdsbAircraftKind.Glider -> "Glider"
    AdsbAircraftKind.Unknown -> "Unknown"
}

