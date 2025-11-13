package com.example.xcpro.weather.wind.data

import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindVector

data class WindState(
    val vector: WindVector? = null,
    val source: WindSource = WindSource.NONE,
    val quality: Int = 0,
    val headwind: Double = 0.0,
    val crosswind: Double = 0.0,
    val lastUpdatedMillis: Long = 0L
) {
    val isAvailable: Boolean = vector != null && quality > 0
}
