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
    }

    private val currentThermalInfo = ThermalClimbInfo()
    private val lastThermalInfo = ThermalClimbInfo()
    private var lastThermalLiftRate = 0.0
    private var lastThermalGain = 0.0
    private var lastThermalTimestamp = 0L
    private var totalCirclingSeconds = 0.0
    private var totalHeightGain = 0.0
    var thermalAverageTotal: Float = 0f
        private set
    var thermalAverageCurrent: Float = 0f
        private set
    var thermalGainCurrent: Double = 0.0
        private set
    var thermalGainValid: Boolean = false
        private set
    val currentThermalLiftRate: Double
        get() = when {
            currentThermalInfo.isDefined() -> currentThermalInfo.liftRate
            lastThermalInfo.isDefined() -> lastThermalInfo.liftRate
            else -> Double.NaN
        }
    val currentThermalValid: Boolean
        get() = currentThermalInfo.isDefined() || lastThermalInfo.isDefined()

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
        addLocationToHistory(location, gps.speed.value.toFloat(), gps.bearing.toFloat())
    }

    fun updateThermalState(
        timestampMillis: Long,
        teAltitudeMeters: Double,
        verticalSpeedMs: Double,
        isCircling: Boolean
    ) {
        if (!teAltitudeMeters.isFinite()) {
            // leave last thermal intact; before first thermal it'll remain undefined
            return
        }

        if (timestampMillis < lastThermalTimestamp) {
            resetThermalTracking()
        }

        val deltaSeconds = if (lastThermalTimestamp == 0L || timestampMillis <= lastThermalTimestamp) {
            0.0
        } else {
            (timestampMillis - lastThermalTimestamp) / 1000.0
        }
        lastThermalTimestamp = timestampMillis

        if (isCircling) {
            if (!currentThermalInfo.isDefined()) {
                currentThermalInfo.startTime = timestampMillis
                currentThermalInfo.startTeAltitude = teAltitudeMeters
            }
            currentThermalInfo.endTime = timestampMillis
            currentThermalInfo.endTeAltitude = teAltitudeMeters
            currentThermalInfo.gain =
                currentThermalInfo.endTeAltitude - currentThermalInfo.startTeAltitude
            val durationSeconds = currentThermalInfo.durationSeconds
            val liftRate = when {
                durationSeconds > 0.5 -> currentThermalInfo.gain / durationSeconds
                verticalSpeedMs.isFinite() -> verticalSpeedMs
                else -> 0.0
            }
            currentThermalInfo.liftRate = liftRate
            thermalGainCurrent = currentThermalInfo.gain
            thermalAverageCurrent = liftRate.toFloat()
            lastThermalLiftRate = liftRate
            lastThermalGain = currentThermalInfo.gain
            thermalGainValid = true

        } else {
            if (currentThermalInfo.isDefined()) {
                totalCirclingSeconds += currentThermalInfo.durationSeconds
                totalHeightGain += currentThermalInfo.gain
                lastThermalLiftRate = currentThermalInfo.liftRate
                lastThermalGain = currentThermalInfo.gain
                lastThermalInfo.copyFrom(currentThermalInfo)
            }
            // Keep showing last thermal stats like XCSoar (current_thermal falls back to last_thermal)
            currentThermalInfo.copyFrom(lastThermalInfo)
            thermalGainCurrent = lastThermalGain
            thermalGainValid = lastThermalInfo.isDefined()
            thermalAverageCurrent = lastThermalLiftRate.toFloat()
        }

        val cumulativeSeconds = totalCirclingSeconds +
            if (currentThermalInfo.isDefined()) currentThermalInfo.durationSeconds else 0.0
        val cumulativeGain = totalHeightGain +
            if (currentThermalInfo.isDefined()) currentThermalInfo.gain else 0.0

        thermalAverageTotal = if (cumulativeSeconds > 0.5) {
            (cumulativeGain / cumulativeSeconds).toFloat()
        } else {
            0f
        }
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
    fun calculateNetto(
        currentVerticalSpeed: Double,
        trueAirspeed: Double?,
        fallbackGroundSpeed: Double
    ): NettoComputation {
        val now = System.currentTimeMillis()
        val tasCandidate = trueAirspeed?.takeIf { it.isFinite() && it > MIN_MOVING_SPEED_MS }
        if (tasCandidate != null) {
            lastValidTAS = tasCandidate
            lastSpeedTimestamp = now
        }

        val gndCandidate = fallbackGroundSpeed.takeIf { it.isFinite() && it > MIN_MOVING_SPEED_MS }
        if (gndCandidate != null) {
            lastValidGnd = gndCandidate
            lastSpeedTimestamp = now
        }

        val recentTas = lastValidTAS?.takeIf { now - lastSpeedTimestamp <= SPEED_HOLD_MS && it > MIN_MOVING_SPEED_MS }
        val recentGnd = lastValidGnd?.takeIf { now - lastSpeedTimestamp <= SPEED_HOLD_MS && it > MIN_MOVING_SPEED_MS }
        val hasRecentMotion = tasCandidate != null || gndCandidate != null || recentTas != null || recentGnd != null

        val speed = tasCandidate
            ?: recentTas
            ?: gndCandidate
            ?: recentGnd
            ?: if (hasRecentMotion) DEFAULT_FALLBACK_SPEED_MS else null

        if (speed == null) {
            // No evidence of movement; don't invent sink from polar. Publish brutto and flag invalid.
            return NettoComputation(currentVerticalSpeed, false)
        }

        val sinkRate = sinkProvider.sinkAtSpeed(speed)
            ?: return NettoComputation(currentVerticalSpeed, false)

        val nettoValue = currentVerticalSpeed + sinkRate
        return NettoComputation(nettoValue, true)
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
    internal fun resetThermalTracking() {
        currentThermalInfo.clear()
        lastThermalInfo.clear()
        lastThermalLiftRate = 0.0
        lastThermalGain = 0.0
        lastThermalTimestamp = 0L
        totalCirclingSeconds = 0.0
        totalHeightGain = 0.0
        thermalAverageTotal = 0f
        thermalAverageCurrent = 0f
        thermalGainCurrent = 0.0
        thermalGainValid = false
    }
}
