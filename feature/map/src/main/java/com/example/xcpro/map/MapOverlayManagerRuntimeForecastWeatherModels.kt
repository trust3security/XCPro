package com.example.xcpro.map

import com.example.xcpro.weather.rain.WeatherRainFrameSelection

internal data class WeatherRainRuntimeConfig(
    val enabled: Boolean,
    val frameSelection: WeatherRainFrameSelection?,
    val opacity: Float,
    val transitionDurationMs: Long,
    val stale: Boolean
)

internal data class SkySightSatelliteRuntimeConfig(
    val enabled: Boolean,
    val showSatelliteImagery: Boolean,
    val showRadar: Boolean,
    val showLightning: Boolean,
    val animate: Boolean,
    val historyFrameCount: Int,
    val referenceTimeUtcMs: Long?
)

internal fun joinNonBlankRuntimeMessages(vararg messages: String?): String? {
    val joined = messages.asSequence()
        .filterNotNull()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" ")
    return joined.takeIf { it.isNotBlank() }
}
