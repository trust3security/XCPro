package com.example.dfcards

import kotlin.math.*

/**
 * Calculates True Airspeed (TAS) and Indicated Airspeed (IAS) for aviation applications.
 *
 * TAS = Ground Speed Vector - Wind Vector
 * IAS = TAS corrected for air density at altitude
 */
object AirspeedCalculator {

    // Standard atmosphere constants
    private const val SEA_LEVEL_PRESSURE_HPA = 1013.25
    private const val SEA_LEVEL_TEMP_CELSIUS = 15.0
    private const val TEMP_LAPSE_RATE_C_PER_M = -0.0065  // Temperature decreases 6.5C per 1000m
    private const val GAS_CONSTANT = 287.05  // Specific gas constant for dry air (J/kgK)
    private const val GRAVITY = 9.80665  // Standard gravity (m/s)

    /**
     * Calculate True Airspeed (TAS) from ground speed and wind
     *
     * @param groundSpeedKt Ground speed in knots
     * @param trackDeg Track direction in degrees (0-360, where 0=North)
     * @param windSpeedKt Wind speed in knots
     * @param windFromDeg Wind direction in degrees (direction wind is FROM)
     * @return TAS in knots
     */
    fun calculateTAS(
        groundSpeedKt: Double,
        trackDeg: Double,
        windSpeedKt: Double,
        windFromDeg: Double
    ): Double {
        // Convert to radians
        val trackRad = Math.toRadians(trackDeg)
        val windFromRad = Math.toRadians(windFromDeg)

        // Ground speed vector components (North/East)
        val gsNorth = groundSpeedKt * cos(trackRad)
        val gsEast = groundSpeedKt * sin(trackRad)

        // Wind vector components (wind TO direction is opposite of FROM)
        val windToRad = windFromRad + PI
        val windNorth = windSpeedKt * cos(windToRad)
        val windEast = windSpeedKt * sin(windToRad)

        // TAS vector = Ground speed vector - Wind vector
        val tasNorth = gsNorth - windNorth
        val tasEast = gsEast - windEast

        // Calculate TAS magnitude
        val tas = sqrt(tasNorth * tasNorth + tasEast * tasEast)

        return tas
    }

    /**
     * Calculate Indicated Airspeed (IAS) from True Airspeed (TAS)
     * IAS = TAS * sqrt(/) where  is air density at altitude
     *
     * @param tasKt True airspeed in knots
     * @param altitudeFt Altitude in feet
     * @param qnhHPa QNH pressure setting in hectopascals (default 1013.25)
     * @param oatCelsius Outside air temperature in Celsius (optional, will use ISA if null)
     * @return IAS in knots
     */
    fun calculateIAS(
        tasKt: Double,
        altitudeFt: Double,
        qnhHPa: Double = SEA_LEVEL_PRESSURE_HPA,
        oatCelsius: Double? = null
    ): Double {
        // Convert altitude to meters
        val altitudeM = altitudeFt * 0.3048

        // Calculate ISA temperature at altitude
        val isaTemp = SEA_LEVEL_TEMP_CELSIUS + (TEMP_LAPSE_RATE_C_PER_M * altitudeM)

        // Use actual OAT if provided, otherwise use ISA
        val tempCelsius = oatCelsius ?: isaTemp
        val tempKelvin = tempCelsius + 273.15

        // Calculate pressure at altitude using barometric formula
        val pressureRatio = calculatePressureRatio(altitudeM, qnhHPa)
        val pressureAtAlt = qnhHPa * pressureRatio

        // Calculate air density at altitude ( = P / (R * T))
        val densityAtAlt = (pressureAtAlt * 100) / (GAS_CONSTANT * tempKelvin)  // Convert hPa to Pa

        // Calculate sea level air density (ISA conditions)
        val seaLevelTempK = SEA_LEVEL_TEMP_CELSIUS + 273.15
        val seaLevelDensity = (SEA_LEVEL_PRESSURE_HPA * 100) / (GAS_CONSTANT * seaLevelTempK)

        // IAS = TAS * sqrt(/)
        val densityRatio = densityAtAlt / seaLevelDensity
        val ias = tasKt * sqrt(densityRatio)

        return ias
    }

    /**
     * Calculate pressure ratio at altitude using the barometric formula
     */
    private fun calculatePressureRatio(altitudeM: Double, qnhHPa: Double): Double {
        // Simplified barometric formula for troposphere (valid up to 11km)
        val tempSeaLevelK = SEA_LEVEL_TEMP_CELSIUS + 273.15
        val exponent = (GRAVITY * 0.0289644) / (GAS_CONSTANT * abs(TEMP_LAPSE_RATE_C_PER_M))

        return (1 + (TEMP_LAPSE_RATE_C_PER_M * altitudeM) / tempSeaLevelK).pow(exponent)
    }

    /**
     * Convenience function to calculate IAS directly from flight data
     *
     * @param groundSpeedKt Ground speed in knots
     * @param trackDeg Track direction in degrees
     * @param windSpeedKt Wind speed in knots
     * @param windFromDeg Wind direction in degrees (FROM)
     * @param altitudeFt Altitude in feet
     * @param qnhHPa QNH pressure setting
     * @return IAS in knots
     */
    fun calculateIASFromGroundSpeed(
        groundSpeedKt: Double,
        trackDeg: Double,
        windSpeedKt: Double,
        windFromDeg: Double,
        altitudeFt: Double,
        qnhHPa: Double = SEA_LEVEL_PRESSURE_HPA
    ): Double {
        // First calculate TAS
        val tas = calculateTAS(groundSpeedKt, trackDeg, windSpeedKt, windFromDeg)

        // Then convert TAS to IAS
        return calculateIAS(tas, altitudeFt, qnhHPa)
    }

    /**
     * Fallback calculation when wind data is not available
     * This provides a rough approximation based on altitude only
     *
     * @param groundSpeedKt Ground speed in knots
     * @param altitudeFt Altitude in feet
     * @return Approximate IAS in knots (less accurate without wind data)
     */
    fun calculateApproximateIAS(
        groundSpeedKt: Double,
        altitudeFt: Double
    ): Double {
        // Without wind data, assume TAS  GS (only valid in calm conditions)
        // Then apply altitude correction

        // Rule of thumb: TAS increases ~2% per 1000ft
        // So IAS decreases by same amount
        val altitudeCorrection = 1.0 - (altitudeFt / 1000.0 * 0.02)
        val correctionClamped = altitudeCorrection.coerceIn(0.5, 1.0)  // Reasonable limits

        return groundSpeedKt * correctionClamped
    }
}