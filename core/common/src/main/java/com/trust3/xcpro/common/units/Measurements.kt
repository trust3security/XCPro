package com.trust3.xcpro.common.units

import kotlin.math.abs

@JvmInline
value class SpeedMs(val value: Double) {
    operator fun plus(other: SpeedMs): SpeedMs = SpeedMs(value + other.value)
    operator fun minus(other: SpeedMs): SpeedMs = SpeedMs(value - other.value)
    operator fun times(scalar: Double): SpeedMs = SpeedMs(value * scalar)
    operator fun div(scalar: Double): SpeedMs = SpeedMs(value / scalar)
    fun abs(): SpeedMs = SpeedMs(abs(value))
}

@JvmInline
value class VerticalSpeedMs(val value: Double) {
    operator fun plus(other: VerticalSpeedMs): VerticalSpeedMs = VerticalSpeedMs(value + other.value)
    operator fun minus(other: VerticalSpeedMs): VerticalSpeedMs = VerticalSpeedMs(value - other.value)
    operator fun times(scalar: Double): VerticalSpeedMs = VerticalSpeedMs(value * scalar)
    operator fun div(scalar: Double): VerticalSpeedMs = VerticalSpeedMs(value / scalar)
    fun abs(): VerticalSpeedMs = VerticalSpeedMs(abs(value))
}

@JvmInline
value class AccelerationMs2(val value: Double)

@JvmInline
value class AltitudeM(val value: Double) {
    operator fun plus(other: AltitudeM): AltitudeM = AltitudeM(value + other.value)
    operator fun minus(other: AltitudeM): AltitudeM = AltitudeM(value - other.value)
}

@JvmInline
value class DistanceM(val value: Double) {
    operator fun plus(other: DistanceM): DistanceM = DistanceM(value + other.value)
    operator fun minus(other: DistanceM): DistanceM = DistanceM(value - other.value)
}

@JvmInline
value class PressureHpa(val value: Double)

@JvmInline
value class TemperatureC(val value: Double)

fun SpeedMs.toDouble(): Double = value
fun VerticalSpeedMs.toDouble(): Double = value
fun AccelerationMs2.toDouble(): Double = value
fun AltitudeM.toDouble(): Double = value
fun DistanceM.toDouble(): Double = value
fun PressureHpa.toDouble(): Double = value
fun TemperatureC.toDouble(): Double = value
