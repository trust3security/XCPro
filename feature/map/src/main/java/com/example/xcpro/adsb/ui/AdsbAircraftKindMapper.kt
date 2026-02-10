package com.example.xcpro.adsb.ui

import com.example.xcpro.adsb.AdsbTrafficUiModel

private const val SMALL_JET_SPEED_THRESHOLD_MPS = 120.0

/**
 * UI-only mapping from ADS-B emitter metadata to visual aircraft kind.
 */
fun classifyAdsbAircraftKind(category: Int?, speedMps: Double?): AdsbAircraftKind = when {
    category == null -> {
        when {
            speedMps != null && speedMps >= SMALL_JET_SPEED_THRESHOLD_MPS -> AdsbAircraftKind.SmallJet
            speedMps != null && speedMps > 0.0 -> AdsbAircraftKind.SmallSingleEngine
            else -> AdsbAircraftKind.Unknown
        }
    }
    category == 8 -> AdsbAircraftKind.Helicopter
    category == 9 -> AdsbAircraftKind.Glider
    category == 4 || category == 5 || category == 6 -> AdsbAircraftKind.LargeJet
    category == 7 -> AdsbAircraftKind.SmallJet
    category == 3 -> {
        if (speedMps != null && speedMps >= SMALL_JET_SPEED_THRESHOLD_MPS) {
            AdsbAircraftKind.SmallJet
        } else {
            AdsbAircraftKind.SmallSingleEngine
        }
    }
    category == 2 -> AdsbAircraftKind.SmallSingleEngine
    else -> AdsbAircraftKind.Unknown
}

fun AdsbTrafficUiModel.aircraftKind(): AdsbAircraftKind = classifyAdsbAircraftKind(
    category = category,
    speedMps = speedMps
)
