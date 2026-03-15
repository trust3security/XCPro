package com.example.xcpro.glider

data class SpeedBoundsMs(
    val minMs: Double,
    val maxMs: Double
) {
    fun clamp(value: Double): Double = value.coerceIn(minMs, maxMs)
}
