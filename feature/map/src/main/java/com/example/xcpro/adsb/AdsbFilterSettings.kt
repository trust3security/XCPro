package com.example.xcpro.adsb

import com.example.xcpro.common.units.UnitsConverter

const val ADSB_MAX_DISTANCE_MIN_KM = 1
const val ADSB_MAX_DISTANCE_DEFAULT_KM = 10
const val ADSB_MAX_DISTANCE_MAX_KM = 50

const val ADSB_VERTICAL_FILTER_MIN_METERS = 0.0
const val ADSB_VERTICAL_FILTER_MAX_METERS = 3_048.0 // 10,000 ft

const val ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS = 3_000.0 * UnitsConverter.METERS_PER_FOOT
const val ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS = 2_000.0 * UnitsConverter.METERS_PER_FOOT

fun clampAdsbMaxDistanceKm(value: Int): Int =
    value.coerceIn(ADSB_MAX_DISTANCE_MIN_KM, ADSB_MAX_DISTANCE_MAX_KM)

fun clampAdsbVerticalFilterMeters(value: Double): Double {
    if (!value.isFinite()) return ADSB_VERTICAL_FILTER_MIN_METERS
    return value.coerceIn(ADSB_VERTICAL_FILTER_MIN_METERS, ADSB_VERTICAL_FILTER_MAX_METERS)
}
