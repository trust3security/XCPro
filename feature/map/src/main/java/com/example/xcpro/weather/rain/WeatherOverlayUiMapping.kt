package com.example.xcpro.weather.rain

enum class WeatherMapConfidenceLevel {
    LIVE,
    STALE,
    ERROR
}

data class WeatherMapConfidenceState(
    val visible: Boolean = false,
    val level: WeatherMapConfidenceLevel = WeatherMapConfidenceLevel.ERROR,
    val label: String = ""
)

fun weatherRadarStatusLabel(
    status: WeatherRadarStatusCode
): String =
    when (status) {
        WeatherRadarStatusCode.NO_METADATA -> "No metadata"
        WeatherRadarStatusCode.NO_FRAMES -> "No frames"
        WeatherRadarStatusCode.RATE_LIMIT -> "Rate limited"
        WeatherRadarStatusCode.NETWORK_ERROR -> "Network error"
        WeatherRadarStatusCode.PARSE_ERROR -> "Parse error"
        WeatherRadarStatusCode.OK -> "OK"
    }

fun resolveWeatherMapConfidenceState(
    runtimeState: WeatherOverlayRuntimeState
): WeatherMapConfidenceState {
    if (!runtimeState.enabled) return WeatherMapConfidenceState(visible = false)

    val selectedFrame = runtimeState.selectedFrame
    if (selectedFrame == null) {
        return WeatherMapConfidenceState(
            visible = true,
            level = WeatherMapConfidenceLevel.ERROR,
            label = "Rain Error"
        )
    }

    return when {
        runtimeState.metadataStatus in HARD_ERROR_STATUSES -> {
            WeatherMapConfidenceState(
                visible = true,
                level = WeatherMapConfidenceLevel.ERROR,
                label = "Rain Error"
            )
        }

        runtimeState.metadataStale -> {
            WeatherMapConfidenceState(
                visible = true,
                level = WeatherMapConfidenceLevel.STALE,
                label = "Rain Stale"
            )
        }

        else -> {
            WeatherMapConfidenceState(
                visible = true,
                level = WeatherMapConfidenceLevel.LIVE,
                label = "Rain Live"
            )
        }
    }
}

private val HARD_ERROR_STATUSES: Set<WeatherRadarStatusCode> = setOf(
    WeatherRadarStatusCode.NO_METADATA,
    WeatherRadarStatusCode.NO_FRAMES,
    WeatherRadarStatusCode.PARSE_ERROR
)
