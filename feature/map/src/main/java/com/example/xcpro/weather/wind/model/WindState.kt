package com.example.xcpro.weather.wind.model

data class WindState(
    val vector: WindVector? = null,
    val source: WindSource = WindSource.NONE,
    val quality: Int = 0,
    val headwind: Double = 0.0,
    val crosswind: Double = 0.0,
    val lastUpdatedMillis: Long = 0L,
    val stale: Boolean = false
) {
    val isAvailable: Boolean = vector != null && quality > 0 && !stale
}
