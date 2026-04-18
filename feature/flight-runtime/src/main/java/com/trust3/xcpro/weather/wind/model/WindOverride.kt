package com.trust3.xcpro.weather.wind.model

data class WindOverride(
    val vector: WindVector,
    val timestampMillis: Long,
    val source: WindSource,
    val quality: Int = DEFAULT_OVERRIDE_QUALITY
) {
    companion object {
        const val DEFAULT_OVERRIDE_QUALITY = 6
    }
}
