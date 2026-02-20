package com.example.xcpro.ogn

import kotlin.math.abs

private const val KNOTS_TO_MPS = 0.514444
private const val MAX_ABS_VARIO_KTS = 12.0
private const val MAX_ABS_VARIO_MPS = MAX_ABS_VARIO_KTS * KNOTS_TO_MPS

const val OGN_THERMAL_SNAIL_COLOR_COUNT = 19

private val SNAIL_VARIO_HEX_COLORS = arrayOf(
    "#0B1026",
    "#12264A",
    "#153C66",
    "#1B5382",
    "#206A9E",
    "#2A81B9",
    "#3A96CB",
    "#56A9D6",
    "#7CC0E2",
    "#FFF4B0",
    "#FFE781",
    "#FFD24F",
    "#FFB532",
    "#FF9828",
    "#FF7A1E",
    "#F85B2B",
    "#E33B4E",
    "#B12C82",
    "#6D1A9C"
)

fun climbRateToSnailColorIndex(climbRateMps: Double): Int {
    if (!climbRateMps.isFinite()) return OGN_THERMAL_SNAIL_COLOR_COUNT / 2
    val clamped = climbRateMps.coerceIn(-MAX_ABS_VARIO_MPS, MAX_ABS_VARIO_MPS)
    val normalized = (clamped + MAX_ABS_VARIO_MPS) / (2.0 * MAX_ABS_VARIO_MPS)
    val raw = (normalized * OGN_THERMAL_SNAIL_COLOR_COUNT).toInt()
    return raw.coerceIn(0, OGN_THERMAL_SNAIL_COLOR_COUNT - 1)
}

fun snailColorHexForIndex(index: Int): String =
    SNAIL_VARIO_HEX_COLORS[index.coerceIn(0, SNAIL_VARIO_HEX_COLORS.lastIndex)]

fun snailColorHexStops(): Array<String> =
    SNAIL_VARIO_HEX_COLORS.copyOf()

fun isValidThermalCoordinate(latitude: Double, longitude: Double): Boolean {
    if (!latitude.isFinite() || !longitude.isFinite()) return false
    if (abs(latitude) > 90.0) return false
    if (abs(longitude) > 180.0) return false
    return true
}
