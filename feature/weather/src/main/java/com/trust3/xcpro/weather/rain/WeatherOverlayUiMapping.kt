package com.trust3.xcpro.weather.rain

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
