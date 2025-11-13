package com.example.xcpro.sensors

import android.location.Location
import com.example.dfcards.dfcards.calculations.SimpleAglCalculator
import com.example.xcpro.glider.StillAirSinkProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.*

/**
 * Flight Calculation Helpers
 *
 * Extracted from FlightDataCalculator.kt to maintain 500-line file limit.
 *
 * Contains calculation functions for:
 * - Wind speed and direction (GPS track history analysis)
 * - Thermal average (vertical speed history analysis)
 * - L/D ratio (glide ratio calculation)
 * - Total Energy (TE) compensation (removes stick thermals)
 * - Netto variometer (compensated for sink rate)
 * - AGL (Above Ground Level) calculation
 *
 * All functions are internal to the sensors package.
 */
internal class FlightCalculationHelpers(
    private val scope: CoroutineScope,
    private val aglCalculator: SimpleAglCalculator,
    private val locationHistory: MutableList<LocationWithTime>,
    private val verticalSpeedHistory: MutableList<VerticalSpeedPoint>,
    private val sinkProvider: StillAirSinkProvider
) {

    companion object {
        // History sizes
        private const val MAX_LOCATION_HISTORY = 20
        private const val MAX_VSPEED_HISTORY = 10

        // Thermal detection
        private const val THERMAL_DETECTION_THRESHOLD = 0.5f  // m/s
        private const val THERMAL_MIN_DURATION = 15L          // seconds

        // L/D calculation
        private const val LD_CALCULATION_INTERVAL = 5000L     // ms

        // Netto calculation
        private const val MIN_AIRSPEED_FOR_NETTO = 15.0       // m/s (54 km/h)
    }

    // Thermal tracking state
    private var thermalStartTime: Long = 0L
    private var thermalStartAltitude: Double = 0.0
    private var thermalClimbRates = mutableListOf<Float>()

    // L/D tracking state
    private var lastLDCalculationTime = 0L
    private var lastLDAltitude = 0.0
    private var currentLD = 0f

    // AGL state
    var currentAGL: Double = 0.0
        private set

    /**
     * Update AGL (Above Ground Level) using SRTM terrain database
     *
     * Uses barometric altitude (stable) instead of GPS altitude (jumpy)
     * Cache lookups are instant (<1ms), only first API call takes time
     *
     * ✅ NON-BLOCKING: Launches async fetch, doesn't freeze GPS loop
     */
    fun updateAGL(baroAltitude: Double, gps: GPSData, speed: Double) {
        // ✅ FIXED: Launch async coroutine instead of runBlocking
        // GPS loop continues immediately, AGL updates when fetch completes
        scope.launch {
            val newAGL = aglCalculator.calculateAgl(
                altitude = baroAltitude,  // Use baro for stability
                lat = gps.latLng.latitude,
                lon = gps.latLng.longitude,
                speed = speed
            )

            // Update cached value (or keep existing if fetch failed)
            if (newAGL != null) {
                currentAGL = newAGL
            }
            // If fetch failed (null), keep previous AGL value
        }
    }

    /**
     * Calculate wind speed and direction from GPS track history
     * Ported from FlightDataManager.kt lines 375-415
     */
    fun recordLocationSample(gps: GPSData) {
        val location = Location("").apply {
            latitude = gps.latLng.latitude
            longitude = gps.latLng.longitude
        }
        addLocationToHistory(location, gps.speed.toFloat(), gps.bearing.toFloat())
    }

    /**
     * Calculate thermal average from vertical speed history
     * Ported from FlightDataManager.kt lines 417-458
     */
    fun calculateThermalAverage(currentVerticalSpeed: Float, currentAltitude: Double): Float {
        val currentTime = System.currentTimeMillis()

        verticalSpeedHistory.add(VerticalSpeedPoint(currentVerticalSpeed, currentTime, currentAltitude))

        while (verticalSpeedHistory.size > MAX_VSPEED_HISTORY) {
            verticalSpeedHistory.removeAt(0)
        }

        val isCurrentlyClimbing = currentVerticalSpeed > THERMAL_DETECTION_THRESHOLD

        if (isCurrentlyClimbing && thermalStartTime == 0L) {
            thermalStartTime = currentTime
            thermalStartAltitude = currentAltitude
            thermalClimbRates.clear()
        } else if (!isCurrentlyClimbing && thermalStartTime > 0L) {
            val thermalDuration = (currentTime - thermalStartTime) / 1000L
            if (thermalDuration < THERMAL_MIN_DURATION) {
                resetThermalTracking()
                return 0f
            } else {
                resetThermalTracking()
            }
        }

        if (thermalStartTime > 0L && isCurrentlyClimbing) {
            thermalClimbRates.add(currentVerticalSpeed)

            val cutoffTime = currentTime - 30000L
            thermalClimbRates.removeAll {
                verticalSpeedHistory.find { vsp -> vsp.verticalSpeed == it }?.timestamp ?: 0L < cutoffTime
            }

            return if (thermalClimbRates.isNotEmpty()) {
                thermalClimbRates.average().toFloat()
            } else {
                currentVerticalSpeed
            }
        }

        return 0f
    }

    /**
     * Calculate L/D ratio (glide ratio)
     * Ported from FlightDataManager.kt lines 460-494
     */
    fun calculateCurrentLD(gps: GPSData, currentAltitude: Double): Float {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastLDCalculationTime < LD_CALCULATION_INTERVAL) {
            return currentLD
        }

        if (lastLDAltitude == 0.0) {
            lastLDAltitude = currentAltitude
            lastLDCalculationTime = currentTime
            return 0f
        }

        val distanceTraveled = if (locationHistory.size >= 2) {
            val oldLocation = locationHistory[locationHistory.size - min(locationHistory.size, 5)]
            val currentLocation = Location("").apply {
                latitude = gps.latLng.latitude
                longitude = gps.latLng.longitude
            }
            currentLocation.distanceTo(oldLocation.location)
        } else {
            0f
        }

        val altitudeLost = (lastLDAltitude - currentAltitude).toFloat()

        if (altitudeLost > 0.5f && distanceTraveled > 10f) {
            currentLD = distanceTraveled / altitudeLost
            lastLDAltitude = currentAltitude
            lastLDCalculationTime = currentTime
            return currentLD.coerceIn(5f, 100f)
        }

        return currentLD
    }

    /**
     * Calculate Total Energy (TE) compensated vertical speed
     *
     * CRITICAL: Removes "stick thermals" (false lift from pilot maneuvers)
     *
     * When pilot pulls back:
     * - Glider slows (loses kinetic energy)
     * - Glider climbs (gains potential energy)
     * - Raw vario shows FALSE LIFT
     * - TE vario shows ZERO (correct - no air mass movement)
     *
     * Formula: TE = Raw Vario + Δ(Kinetic Energy) / Δt
     *
     * @param rawVario Raw vertical speed from Kalman filter (m/s)
     * @param currentSpeed Current GPS ground speed (m/s)
     * @param previousSpeed Previous GPS ground speed (m/s)
     * @param deltaTime Time between measurements (s)
     * @return TE-compensated vertical speed (m/s)
     */
    fun calculateTotalEnergy(
        rawVario: Double,
        currentSpeed: Double,
        previousSpeed: Double,
        deltaTime: Double
    ): Double {
        // Prevent division by zero or invalid delta time
        if (deltaTime <= 0.001) {
            return rawVario
        }

        // Constants
        val g = 9.81  // m/s² (gravity)

        // Calculate change in kinetic energy per unit mass
        // KE = 0.5 * m * v²
        // Δ(KE/m) = 0.5 * (v_current² - v_previous²)
        // Height equivalent: Δh = Δ(KE/m) / g = (v_current² - v_previous²) / (2g)
        val deltaKineticEnergyHeight = (currentSpeed * currentSpeed - previousSpeed * previousSpeed) / (2.0 * g)

        // Rate of kinetic energy change (m/s)
        val kineticEnergyRate = deltaKineticEnergyHeight / deltaTime

        // Total Energy vertical speed = barometric V/S + kinetic energy change rate
        // If pilot pulls back: rawVario increases, kineticEnergyRate decreases (losing speed)
        // Net effect: TE stays constant (no false lift)
        val teVerticalSpeed = rawVario + kineticEnergyRate

        return teVerticalSpeed
    }

    /**
     * Calculate netto variometer (compensated for sink rate)
     * Ported from FlightDataManager.kt lines 496-503
     */
    fun calculateNetto(currentVerticalSpeed: Float, currentGroundSpeed: Float): Float {
        if (currentGroundSpeed < MIN_AIRSPEED_FOR_NETTO) {
            return 0f
        }

        val sinkFromPolar = sinkProvider.sinkAtSpeed(currentGroundSpeed.toDouble())
        val estimatedSinkRate = if (sinkFromPolar != null) {
            sinkFromPolar.toFloat()
        } else {
            calculateLegacySinkRate(currentGroundSpeed).also {
                // AI-NOTE: fallback when no polar/config is available.
            }
        }
        return currentVerticalSpeed + estimatedSinkRate
    }

    /**
     * Add location to history for wind and L/D calculations
     */
    private fun addLocationToHistory(location: Location, groundSpeed: Float, track: Float) {
        locationHistory.add(LocationWithTime(location, System.currentTimeMillis(), groundSpeed, track))

        while (locationHistory.size > MAX_LOCATION_HISTORY) {
            locationHistory.removeAt(0)
        }
    }

    /**
     * Calculate wind confidence
     */
    /**
     * Reset thermal tracking
     */
    private fun resetThermalTracking() {
        thermalStartTime = 0L
        thermalStartAltitude = 0.0
        thermalClimbRates.clear()
    }

    /**
     * Calculate sink rate based on ground speed
     * Ported from FlightDataManager.kt lines 526-535
     */
    private fun calculateLegacySinkRate(groundSpeed: Float): Float {
        val speedKmh = groundSpeed * 3.6f

        return when {
            speedKmh < 60f -> 0.8f
            speedKmh < 90f -> 0.6f
            speedKmh < 120f -> 0.9f
            else -> 1.5f
        }
    }
}
