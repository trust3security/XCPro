package com.example.xcpro.sensors

import android.location.Location
import com.example.dfcards.dfcards.calculations.SimpleAglCalculator
import com.example.xcpro.glider.StillAirSinkProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min

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
    private val sinkProvider: StillAirSinkProvider
) {
    data class NettoComputation(val value: Double, val valid: Boolean)

    companion object {
        private const val MAX_LOCATION_HISTORY = 20
        private const val LD_CALCULATION_INTERVAL = 5000L     // ms
        private const val SPEED_HOLD_MS = 10_000L
        private const val DEFAULT_FALLBACK_SPEED_MS = 27.78 // 100 km/h
        private const val MIN_MOVING_SPEED_MS = 0.5         // ~1 kt; below this we treat as stationary
        private const val NETTO_VALID_WARMUP_MS = 20_000L
    }

    private val thermalTracker = ThermalTracker()
    val thermalAverageTotal: Float
        get() = thermalTracker.thermalAverageTotal
    val thermalAverageCurrent: Float
        get() = thermalTracker.thermalAverageCurrent
    val thermalGainCurrent: Double
        get() = thermalTracker.thermalGainCurrent
    val thermalGainValid: Boolean
        get() = thermalTracker.thermalGainValid
    val currentThermalLiftRate: Double
        get() = thermalTracker.currentThermalLiftRate
    val currentThermalValid: Boolean
        get() = thermalTracker.currentThermalValid

    // L/D tracking state
    private var lastLDCalculationTime = 0L
    private var lastLDAltitude = 0.0
    private var currentLD = 0f

    // AGL state
    var currentAGL: Double = 0.0
        private set

    // Last known speeds for dropout resilience
    private var lastValidTAS: Double? = null
    private var lastValidGnd: Double? = null
    private var lastSpeedTimestamp: Long = 0L
    private var nettoEligibleStartMs: Long = -1L

    /**
     * Update AGL (Above Ground Level) using SRTM terrain database
     *
     * Uses barometric altitude (stable) instead of GPS altitude (jumpy)
     * Cache lookups are instant (<1ms), only first API call takes time
     *
     *  NON-BLOCKING: Launches async fetch, doesn't freeze GPS loop
     */
    fun updateAGL(baroAltitude: Double, gps: GPSData, speed: Double) {
        //  FIXED: Launch async coroutine instead of runBlocking
        // GPS loop continues immediately, AGL updates when fetch completes
        scope.launch {
            val newAGL = aglCalculator.calculateAgl(
                altitude = baroAltitude,  // Use baro for stability
                lat = gps.position.latitude,
                lon = gps.position.longitude,
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
    fun recordLocationSample(gps: GPSData, timestampMillis: Long) {
        val location = Location("").apply {
            latitude = gps.position.latitude
            longitude = gps.position.longitude
        }
        addLocationToHistory(location, timestampMillis, gps.speed.value.toFloat(), gps.bearing.toFloat())
    }

    fun updateThermalState(
        timestampMillis: Long,
        teAltitudeMeters: Double,
        verticalSpeedMs: Double,
        isCircling: Boolean,
        isTurning: Boolean
    ) {
        thermalTracker.update(timestampMillis, teAltitudeMeters, verticalSpeedMs, isCircling, isTurning)
    }

    /**
     * Calculate L/D ratio (glide ratio)
     * Ported from FlightDataManager.kt lines 460-494
     */
    fun calculateCurrentLD(gps: GPSData, currentAltitude: Double, timestampMillis: Long): Float {
        val currentTime = timestampMillis

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
                latitude = gps.position.latitude
                longitude = gps.position.longitude
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
     * Formula: TE = Raw Vario + (Kinetic Energy) / t
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
        val g = 9.81  // m/s (gravity)

        // Calculate change in kinetic energy per unit mass
        // KE = 0.5 * m * v
        // (KE/m) = 0.5 * (v_current - v_previous)
        // Height equivalent: h = (KE/m) / g = (v_current - v_previous) / (2g)
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
    fun calculateNetto(
        currentVerticalSpeed: Double,
        indicatedAirspeed: Double?,
        fallbackGroundSpeed: Double,
        timestampMillis: Long
    ): NettoComputation {
        val now = timestampMillis
        val iasCandidate = indicatedAirspeed?.takeIf { it.isFinite() && it > MIN_MOVING_SPEED_MS }
        if (iasCandidate != null) {
            lastValidTAS = iasCandidate
            lastSpeedTimestamp = now
        }

        val gndCandidate = fallbackGroundSpeed.takeIf { it.isFinite() && it > MIN_MOVING_SPEED_MS }
        if (gndCandidate != null) {
            lastValidGnd = gndCandidate
            lastSpeedTimestamp = now
        }

        val recentTas = lastValidTAS?.takeIf { now - lastSpeedTimestamp <= SPEED_HOLD_MS && it > MIN_MOVING_SPEED_MS }
        val recentGnd = lastValidGnd?.takeIf { now - lastSpeedTimestamp <= SPEED_HOLD_MS && it > MIN_MOVING_SPEED_MS }
        val hasRecentMotion = iasCandidate != null || gndCandidate != null || recentTas != null || recentGnd != null

        val speed = iasCandidate
            ?: recentTas
            ?: gndCandidate
            ?: recentGnd
            ?: if (hasRecentMotion) DEFAULT_FALLBACK_SPEED_MS else null

        if (speed == null) {
            // No evidence of movement; don't invent sink from polar. Publish brutto and flag invalid.
            nettoEligibleStartMs = -1L
            return NettoComputation(currentVerticalSpeed, false)
        }

        val sinkRate = sinkProvider.sinkAtSpeed(speed)
            ?: run {
                nettoEligibleStartMs = -1L
                return NettoComputation(currentVerticalSpeed, false)
            }

        if (nettoEligibleStartMs < 0L || now < nettoEligibleStartMs) {
            nettoEligibleStartMs = now
        }
        val warmedUp = now - nettoEligibleStartMs >= NETTO_VALID_WARMUP_MS

        val nettoValue = currentVerticalSpeed + sinkRate
        return NettoComputation(nettoValue, warmedUp)
    }

    /**
     * Add location to history for wind and L/D calculations
     */
    private fun addLocationToHistory(location: Location, timestampMillis: Long, groundSpeed: Float, track: Float) {
        locationHistory.add(LocationWithTime(location, timestampMillis, groundSpeed, track))

        while (locationHistory.size > MAX_LOCATION_HISTORY) {
            locationHistory.removeAt(0)
        }
    }

    /**
     * Calculate wind confidence
     */
    internal fun resetThermalTracking() {
        thermalTracker.reset()
    }

    internal fun resetAll() {
        resetThermalTracking()
        locationHistory.clear()
        lastLDCalculationTime = 0L
        lastLDAltitude = 0.0
        currentLD = 0f
        currentAGL = 0.0
        lastValidTAS = null
        lastValidGnd = null
        lastSpeedTimestamp = 0L
        nettoEligibleStartMs = -1L
    }
}
