package com.example.xcpro.forecast

const val FORECAST_OPACITY_MIN = 0.0f
const val FORECAST_OPACITY_MAX = 1.0f
const val FORECAST_OPACITY_DEFAULT = 0.65f
const val FORECAST_AUTO_TIME_DEFAULT = true

fun clampForecastOpacity(opacity: Float): Float =
    opacity.coerceIn(FORECAST_OPACITY_MIN, FORECAST_OPACITY_MAX)
