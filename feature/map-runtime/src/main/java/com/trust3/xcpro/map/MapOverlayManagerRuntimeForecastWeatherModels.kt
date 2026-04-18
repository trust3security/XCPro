package com.trust3.xcpro.map

import com.trust3.xcpro.weather.rain.WeatherRainFrameSelection
import com.trust3.xcpro.weather.rain.WeatherRadarStatusCode

internal data class WeatherRainRuntimeConfig(
    val enabled: Boolean,
    val frameSelection: WeatherRainFrameSelection?,
    val opacity: Float,
    val transitionDurationMs: Long,
    val stale: Boolean
)

internal data class WeatherRainRuntimeStatus(
    val enabled: Boolean,
    val statusCode: WeatherRadarStatusCode,
    val stale: Boolean,
    val frameSelected: Boolean,
    val transitionDurationMs: Long
)

internal data class SkySightSatelliteRuntimeStatus(
    val contrastIconsEnabled: Boolean,
    val enabled: Boolean,
    val showSatelliteImagery: Boolean,
    val showRadar: Boolean,
    val showLightning: Boolean,
    val animate: Boolean,
    val historyFrameCount: Int
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

internal data class ForecastRasterRuntimeStatus(
    val overlayEnabled: Boolean,
    val windOverlayEnabled: Boolean
)

internal fun joinNonBlankRuntimeMessages(vararg messages: String?): String? {
    val joined = messages.asSequence()
        .filterNotNull()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" | ")
    return joined.takeIf { it.isNotBlank() }
}
