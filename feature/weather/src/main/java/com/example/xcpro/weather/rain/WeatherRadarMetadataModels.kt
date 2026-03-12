package com.example.xcpro.weather.rain

data class WeatherRadarFrame(
    val timeEpochSec: Long,
    val path: String
)

data class WeatherRadarMetadata(
    val hostUrl: String,
    val generatedEpochSec: Long,
    val pastFrames: List<WeatherRadarFrame>
)

data class WeatherRadarMetadataState(
    val status: WeatherRadarStatusCode = WeatherRadarStatusCode.NO_METADATA,
    val metadata: WeatherRadarMetadata? = null,
    val lastSuccessfulFetchWallMs: Long? = null,
    val lastContentChangeWallMs: Long? = null,
    val detail: String? = null
)
