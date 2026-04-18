package com.trust3.xcpro.common.units

/**
 * Shared conversion helpers between SI and user-facing aviation units.
 */
object UnitsConverter {

    // Distance / altitude
    const val METERS_PER_FOOT = 0.3048
    const val METERS_PER_NAUTICAL_MILE = 1852.0
    const val METERS_PER_STATUTE_MILE = 1609.344

    // Speed
    const val KMH_PER_MS = 3.6
    const val KNOTS_PER_MS = 1.943844
    const val MPH_PER_MS = 2.2369362921
    const val FPM_PER_MS = 196.8503937

    // Pressure
    const val INHG_PER_HPA = 0.0295299830714

    // Temperature
    private const val FAHRENHEIT_OFFSET = 32.0
    private const val FAHRENHEIT_SCALE = 9.0 / 5.0

    fun metersToFeet(value: Double): Double = value / METERS_PER_FOOT
    fun feetToMeters(value: Double): Double = value * METERS_PER_FOOT

    fun metersToKilometers(value: Double): Double = value / 1000.0
    fun kilometersToMeters(value: Double): Double = value * 1000.0

    fun metersToNauticalMiles(value: Double): Double = value / METERS_PER_NAUTICAL_MILE
    fun nauticalMilesToMeters(value: Double): Double = value * METERS_PER_NAUTICAL_MILE

    fun metersToStatuteMiles(value: Double): Double = value / METERS_PER_STATUTE_MILE
    fun statuteMilesToMeters(value: Double): Double = value * METERS_PER_STATUTE_MILE

    fun msToKmh(value: Double): Double = value * KMH_PER_MS
    fun kmhToMs(value: Double): Double = value / KMH_PER_MS

    fun msToKnots(value: Double): Double = value * KNOTS_PER_MS
    fun knotsToMs(value: Double): Double = value / KNOTS_PER_MS

    fun msToMph(value: Double): Double = value * MPH_PER_MS
    fun mphToMs(value: Double): Double = value / MPH_PER_MS

    fun verticalMsToFpm(value: Double): Double = value * FPM_PER_MS
    fun fpmToVerticalMs(value: Double): Double = value / FPM_PER_MS

    fun hpaToInhg(value: Double): Double = value * INHG_PER_HPA
    fun inhgToHpa(value: Double): Double = value / INHG_PER_HPA

    fun celsiusToFahrenheit(value: Double): Double = value * FAHRENHEIT_SCALE + FAHRENHEIT_OFFSET
    fun fahrenheitToCelsius(value: Double): Double = (value - FAHRENHEIT_OFFSET) / FAHRENHEIT_SCALE
}
