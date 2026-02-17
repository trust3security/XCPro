package com.example.xcpro.forecast

import kotlin.math.abs

const val FORECAST_OPACITY_MIN = 0.0f
const val FORECAST_OPACITY_MAX = 1.0f
const val FORECAST_OPACITY_DEFAULT = 0.65f
const val FORECAST_AUTO_TIME_DEFAULT = true
const val FORECAST_WIND_OVERLAY_SCALE_MIN = 0.6f
const val FORECAST_WIND_OVERLAY_SCALE_MAX = 2.0f
const val FORECAST_WIND_OVERLAY_SCALE_DEFAULT = 1.0f
const val FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT = 0
const val FORECAST_FOLLOW_TIME_OFFSET_MINUTES_MIN = -60
const val FORECAST_FOLLOW_TIME_OFFSET_MINUTES_MAX = 60
const val FORECAST_FOLLOW_TIME_OFFSET_STEP_MINUTES = 30
val FORECAST_FOLLOW_TIME_OFFSET_OPTIONS_MINUTES: List<Int> = listOf(-60, -30, 0, 30, 60)
val FORECAST_WIND_DISPLAY_MODE_DEFAULT: ForecastWindDisplayMode = ForecastWindDisplayMode.ARROW

enum class ForecastWindDisplayMode(
    val storageValue: String,
    val label: String
) {
    ARROW(storageValue = "ARROW", label = "Arrow"),
    BARB(storageValue = "BARB", label = "Barb");

    companion object {
        fun fromStorageValue(rawValue: String?): ForecastWindDisplayMode {
            val normalized = rawValue?.trim().orEmpty()
            return entries.firstOrNull { mode ->
                mode.storageValue.equals(normalized, ignoreCase = true)
            } ?: FORECAST_WIND_DISPLAY_MODE_DEFAULT
        }
    }
}

fun clampForecastOpacity(opacity: Float): Float =
    opacity.coerceIn(FORECAST_OPACITY_MIN, FORECAST_OPACITY_MAX)

fun clampForecastWindOverlayScale(scale: Float): Float =
    scale.coerceIn(FORECAST_WIND_OVERLAY_SCALE_MIN, FORECAST_WIND_OVERLAY_SCALE_MAX)

fun normalizeForecastFollowTimeOffsetMinutes(offsetMinutes: Int): Int =
    FORECAST_FOLLOW_TIME_OFFSET_OPTIONS_MINUTES
        .minByOrNull { option -> abs(option - offsetMinutes) }
        ?: FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT
