package com.example.xcpro.sensors

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.dfcards.calculations.BarometricAltitudeCalculator
import com.example.dfcards.calculations.ConfidenceLevel
import com.example.dfcards.filters.AdvancedBarometricFilter
import com.example.dfcards.dfcards.calculations.SimpleAglCalculator
import com.example.dfcards.filters.Modern3StateKalmanFilter
import com.example.xcpro.audio.VarioAudioEngine
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.vario.*  // NEW: Vario implementations for side-by-side testing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.*
import java.util.Locale

/**
 * Flight Data Calculator - Combines sensors and performs calculations
 *
 * RESPONSIBILITIES:
 * - Combine GPS + Barometer + Compass flows
 * - Calculate barometric altitude and QNH
 * - Calculate vertical speed (from barometric altitude changes)
 * - Calculate wind speed and direction
 * - Calculate thermal average
 * - Calculate L/D ratio
 * - Calculate netto variometer
 * - Maintain history for calculations
 * - NO sensor management (only calculations)
 *
 * SSOT PRINCIPLE:
 * - ONE StateFlow for calculated flight data
 * - ALL consumers read from this flow
 * - Reuses existing: BarometricAltitudeCalculator, AdvancedBarometricFilter
 * - Uses SimpleAglCalculator with SRTM terrain database (offline, global)
 *
 * SEPARATION OF CONCERNS:
 * - UnifiedSensorManager = raw sensors
 * - FlightDataCalculator = calculations
 * - FlightDataViewModel = UI state
 */
class FlightDataCalculator(
    private val context: Context,
    private val sensorManager: UnifiedSensorManager,
    private val scope: CoroutineScope,
    private val sinkProvider: StillAirSinkProvider
) {

    companion object {
        private const val TAG = "FlightDataCalculator"

        // History sizes
        private const val MAX_LOCATION_HISTORY = 20
        private const val MAX_VSPEED_HISTORY = 10

        // Wind calculation
        private const val WIND_CALCULATION_MIN_POINTS = 8

        // Thermal detection
        private const val THERMAL_DETECTION_THRESHOLD = 0.5f  // m/s
        private const val THERMAL_MIN_DURATION = 15L          // seconds

        // L/D calculation
        private const val LD_CALCULATION_INTERVAL = 5000L     // ms

        // Netto calculation
        private const val MIN_AIRSPEED_FOR_NETTO = 15.0       // m/s (54 km/h)

        // IMU fusion thresholds
        private const val MIN_SPEED_FOR_IMU_FUSION = 15.0      // m/s; below this we skip IMU fusion
        private const val MAX_ACCEL_FOR_FUSION = 12.0         // m/s^2 clamp to reject spikes

        // QNH jump suppression
        private const val QNH_JUMP_THRESHOLD_HPA = 0.8
        private const val QNH_ALTITUDE_JUMP_THRESHOLD_METERS = 5.0
        private const val QNH_CALIBRATION_ACCURACY_THRESHOLD = 8.0
        private const val SEA_LEVEL_TEMP_CELSIUS = 15.0
        private const val TEMP_LAPSE_RATE_C_PER_M = -0.0065
        private const val GAS_CONSTANT = 287.05
        private const val GRAVITY = 9.80665
        private const val MIN_SINK_FOR_IAS_MS = 0.15
        private const val IAS_SCAN_MIN_MS = 8.0
        private const val IAS_SCAN_MAX_MS = 80.0
        private const val IAS_SCAN_STEP_MS = 0.5
        private const val SEA_LEVEL_PRESSURE_HPA = 1013.25
    }

    // History for calculations (shared with helper) - must be initialized first
    private val locationHistory = mutableListOf<LocationWithTime>()
    private val verticalSpeedHistory = mutableListOf<VerticalSpeedPoint>()

    // Calculation modules (reuse existing code) - aglCalculator must be first!
    private val aglCalculator = SimpleAglCalculator(context)  // KISS: SRTM terrain database
    private val baroCalculator = BarometricAltitudeCalculator(aglCalculator)  // 🚀 SRTM-based QNH calibration
    private val baroFilter = AdvancedBarometricFilter()  // Old 2-state filter (fallback)
    private val modernVarioFilter = Modern3StateKalmanFilter()  // NEW: Modern 3-state filter

    // Flight calculation helpers (extracted to maintain 500-line limit)
    private val flightHelpers = FlightCalculationHelpers(
        scope = scope,
        aglCalculator = aglCalculator,
        locationHistory = locationHistory,
        verticalSpeedHistory = verticalSpeedHistory,
        sinkProvider = sinkProvider
    )

    // ✅ VARIO IMPLEMENTATIONS - Side-by-side testing (VARIO_IMPROVEMENTS.md)
    private val varioOptimized = OptimizedKalmanVario()     // Priority 1: R=0.5m
    private val varioLegacy = LegacyKalmanVario()           // Baseline: R=2.0m
    private val varioRaw = RawBaroVario()                   // No filtering
    private val varioGPS = GPSVario()                        // GPS-based
    private val varioComplementary = ComplementaryVario()    // Future (Priority 3)

    // ✅ PROFESSIONAL VARIO AUDIO ENGINE (zero-lag audio feedback)
    val audioEngine = VarioAudioEngine(context, scope)

    // StateFlow - Single Source of Truth for calculated flight data
    private val _flightDataFlow = MutableStateFlow<CompleteFlightData?>(null)
    val flightDataFlow: StateFlow<CompleteFlightData?> = _flightDataFlow.asStateFlow()

    // Tracking for delta time calculation
    private var lastUpdateTime = 0L
    private var lastVarioUpdateTime = 0L  // For high-speed vario loop

    // Tracking for Total Energy (TE) compensation
    private var previousGPSSpeed = 0.0  // m/s (for TE calculation)

    // ✅ PRIORITY 2: CACHED GPS DATA (for high-speed vario loop)
    // The vario loop runs at 50Hz but GPS only updates at 10Hz
    // Cache GPS data so vario can use "last known" values between GPS updates
    private var cachedGPSSpeed = 0.0
    // Use NaN as a sentinel until we have a real GPS altitude (prevents false calibration)
    private var cachedGPSAltitude = Double.NaN
    private var cachedGPSAccuracy = 15.0
    private var cachedIsGPSFixed = false
    private var cachedGPSLat = 0.0  // 🚀 For SRTM-based QNH calibration
    private var cachedGPSLon = 0.0  // 🚀 For SRTM-based QNH calibration
    private var cachedGPS: GPSData? = null  // Full GPS data for calculations
    private var imuFusionActive = false

    // Cached results from vario loop for GPS loop to use
    private var cachedVarioResult: com.example.dfcards.filters.ModernVarioResult? = null
    private var cachedBaroResult: com.example.dfcards.calculations.BarometricAltitudeData? = null
    private var cachedBaroData: BaroData? = null
    private var cachedCompassData: CompassData? = null
    @Volatile private var imuAssistEnabled: Boolean = true

    init {
        // ✅ PRIORITY 2: DECOUPLED SAMPLE RATES
        // High-speed vario loop (50Hz) + Slow GPS loop (10Hz)

        // HIGH-SPEED VARIO LOOP: Barometer + IMU (50Hz - unleashed!)
        scope.launch {
            combine(
                sensorManager.baroFlow,
                sensorManager.accelFlow
            ) { baro, accel ->
                Pair(baro, accel)
            }.collect { (baro, accel) ->
                updateVarioFilter(baro, accel)
            }
        }

        // SLOW GPS LOOP: GPS + Compass (10Hz - navigation data)
        scope.launch {
            combine(
                sensorManager.gpsFlow,
                sensorManager.compassFlow
            ) { gps, compass ->
                Pair(gps, compass)
            }.collect { (gps, compass) ->
                updateGPSData(gps, compass)
            }
        }

        // Initialize audio engine for professional vario audio
        scope.launch {
            val audioInitialized = audioEngine.initialize()
            if (audioInitialized) {
                audioEngine.start()
                Log.i(TAG, "Vario audio engine initialized and started (PRIORITY 2: High-speed mode)")
            } else {
                Log.w(TAG, "Failed to initialize vario audio engine")
            }
        }

        Log.d(TAG, "FlightDataCalculator initialized with PRIORITY 2: Decoupled sample rates (50Hz vario + 10Hz GPS)")
    }

    /**
     * ✅ PRIORITY 2: HIGH-SPEED VARIO LOOP (50Hz)
     *
     * Updates vario filter with barometer + IMU data at maximum speed
     * Uses cached GPS data (last known values) for motion detection
     * Immediately updates audio engine for zero-lag thermal detection
     */
    private fun updateVarioFilter(baro: BaroData?, accel: AccelData?) {
        if (baro == null) {
            Log.d(TAG, "No barometer data - skipping vario update")
            return
        }

        val currentTime = System.currentTimeMillis()

        val deltaTime = if (lastVarioUpdateTime > 0) {
            (currentTime - lastVarioUpdateTime) / 1000.0
        } else {
            0.02 // 50Hz = 20ms = 0.02s default
        }

        if (deltaTime < 0.01) {
            return
        }

        val previousBaroResult = cachedBaroResult
        val hasCalibrationFix = cachedIsGPSFixed &&
            !cachedGPSAltitude.isNaN() &&
            cachedGPSAccuracy <= QNH_CALIBRATION_ACCURACY_THRESHOLD

        val baroResult = baroCalculator.calculateBarometricAltitude(
            rawPressureHPa = baro.pressureHPa,
            gpsAltitudeMeters = if (hasCalibrationFix) cachedGPSAltitude else null,
            gpsAccuracy = if (hasCalibrationFix) cachedGPSAccuracy else null,
            isGPSFixed = hasCalibrationFix,
            gpsLat = cachedGPSLat.takeIf { hasCalibrationFix },
            gpsLon = cachedGPSLon.takeIf { hasCalibrationFix }
        )

        if (previousBaroResult != null) {
            val qnhDelta = abs(baroResult.qnh - previousBaroResult.qnh)
            val altitudeDelta = abs(baroResult.altitudeMeters - previousBaroResult.altitudeMeters)
            if (qnhDelta > QNH_JUMP_THRESHOLD_HPA || altitudeDelta > QNH_ALTITUDE_JUMP_THRESHOLD_METERS) {
                val qnhLabel = String.format(Locale.US, "%.2f", qnhDelta)
                val altitudeLabel = String.format(Locale.US, "%.1f", altitudeDelta)
                Log.w(
                    TAG,
                    "QNH jump detected Δ${qnhLabel} hPa / Δ${altitudeLabel} m – resetting vario filters"
                )
                modernVarioFilter.reset()
                varioOptimized.reset()
                varioLegacy.reset()
                varioRaw.reset()
                varioGPS.reset()
                varioComplementary.reset()
                baroFilter.reset()
                cachedVarioResult = null
            }
        }

        val rawAccel = accel?.verticalAcceleration ?: 0.0
        val accelReliable = accel?.isReliable == true
        val withinAccelRange = abs(rawAccel) <= MAX_ACCEL_FOR_FUSION
        val usingImu = imuAssistEnabled &&
            accelReliable &&
            withinAccelRange &&
            cachedGPSSpeed >= MIN_SPEED_FOR_IMU_FUSION

        if (usingImu != imuFusionActive) {
            val stateLabel = if (usingImu) "ENABLED" else "DISABLED"
            val speedLabel = String.format("%.1f", cachedGPSSpeed)
            val accelLabel = String.format("%.2f", rawAccel)
            Log.d(TAG, "IMU fusion  (speed= m/s, reliable=, accel= m/s^2)")
            modernVarioFilter.reset()
            varioOptimized.reset()
            varioLegacy.reset()
            varioComplementary.reset()
            imuFusionActive = usingImu
            cachedVarioResult = null
        }

        val verticalAccelForFusion = if (usingImu) {
            rawAccel.coerceIn(-MAX_ACCEL_FOR_FUSION, MAX_ACCEL_FOR_FUSION)
        } else {
            0.0
        }

        val varioResults = mapOf(
            "optimized" to varioOptimized.update(
                baroAltitude = baroResult.altitudeMeters,
                verticalAccel = verticalAccelForFusion,
                deltaTime = deltaTime,
                gpsSpeed = cachedGPSSpeed,
                gpsAltitude = cachedGPSAltitude
            ),
            "legacy" to varioLegacy.update(
                baroAltitude = baroResult.altitudeMeters,
                verticalAccel = verticalAccelForFusion,
                deltaTime = deltaTime,
                gpsSpeed = cachedGPSSpeed,
                gpsAltitude = cachedGPSAltitude
            ),
            "raw" to varioRaw.update(
                baroAltitude = baroResult.altitudeMeters,
                verticalAccel = 0.0,
                deltaTime = deltaTime,
                gpsSpeed = cachedGPSSpeed,
                gpsAltitude = cachedGPSAltitude
            ),
            "gps" to varioGPS.update(
                baroAltitude = 0.0,
                verticalAccel = 0.0,
                deltaTime = deltaTime,
                gpsSpeed = cachedGPSSpeed,
                gpsAltitude = cachedGPSAltitude
            ),
            "complementary" to varioComplementary.update(
                baroAltitude = baroResult.altitudeMeters,
                verticalAccel = verticalAccelForFusion,
                deltaTime = deltaTime,
                gpsSpeed = cachedGPSSpeed,
                gpsAltitude = cachedGPSAltitude
            )
        )

        val varioResult = if (usingImu) {
            modernVarioFilter.update(
                baroAltitude = baroResult.altitudeMeters,
                verticalAccel = verticalAccelForFusion,
                deltaTime = deltaTime,
                gpsSpeed = cachedGPSSpeed
            )
        } else {
            val filteredBaro = baroFilter.processReading(
                rawBaroAltitude = baroResult.altitudeMeters,
                gpsAltitude = cachedGPSAltitude,
                gpsAccuracy = cachedGPSAccuracy
            )
            com.example.dfcards.filters.ModernVarioResult(
                altitude = filteredBaro.displayAltitude,
                verticalSpeed = filteredBaro.verticalSpeed,
                acceleration = 0.0,
                confidence = filteredBaro.confidence
            )
        }

        audioEngine.updateVerticalSpeed(varioResult.verticalSpeed)

        cachedVarioResult = varioResult
        cachedBaroResult = baroResult
        cachedBaroData = baro

        lastVarioUpdateTime = currentTime

    }
    /**
     * ✅ PRIORITY 2: SLOW GPS LOOP (10Hz)
     *
     * Updates navigation data (wind, thermal average, L/D, netto)
     * Applies TE compensation using GPS speed changes
     * Publishes complete flight data to UI
     */
    private fun updateGPSData(gps: GPSData?, compass: CompassData?) {
        if (gps == null) {
            Log.d(TAG, "No GPS data - skipping GPS update")
            return
        }

        val currentTime = System.currentTimeMillis()

        // Update cached GPS data for high-speed vario loop
        cachedGPSSpeed = gps.speed
        cachedGPSAltitude = gps.altitude
        cachedGPSAccuracy = gps.accuracy.toDouble()
        cachedIsGPSFixed = gps.isHighAccuracy
        cachedGPSLat = gps.latLng.latitude   // 🚀 For SRTM-based QNH calibration
        cachedGPSLon = gps.latLng.longitude  // 🚀 For SRTM-based QNH calibration
        cachedGPS = gps
        cachedCompassData = compass

        // Calculate delta time for GPS-based calculations
        val deltaTime = if (lastUpdateTime > 0) {
            (currentTime - lastUpdateTime) / 1000.0
        } else {
            0.1 // 10Hz = 100ms = 0.1s default
        }

        // Get vario result from high-speed loop (or fallback to GPS altitude)
        val varioResult = cachedVarioResult ?: com.example.dfcards.filters.ModernVarioResult(
            altitude = gps.altitude,
            verticalSpeed = 0.0,
            acceleration = 0.0,
            confidence = 0.3
        )

        val baroResult = cachedBaroResult
        val baro = cachedBaroData

        // ✅ TOTAL ENERGY (TE) COMPENSATION: Remove false lift from pilot maneuvers
        val rawVerticalSpeed = varioResult.verticalSpeed
        val currentSpeed = gps.speed

        val teVerticalSpeed = flightHelpers.calculateTotalEnergy(
            rawVario = rawVerticalSpeed,
            currentSpeed = currentSpeed,
            previousSpeed = previousGPSSpeed,
            deltaTime = deltaTime
        )

        // Update previous speed for next calculation
        previousGPSSpeed = currentSpeed

        // Use TE-compensated vertical speed (removes stick thermals!)
        val baroAltitude = varioResult.altitude
        val verticalSpeed = teVerticalSpeed  // TE-compensated (no false lift!)
        val qnh = baroResult?.qnh ?: 1013.25
        val isQNHCalibrated = baroResult?.isCalibrated ?: false

        val pressureAltitude = baroResult?.pressureAltitudeMeters ?: baroAltitude
        val baroGpsDelta = baroResult?.gpsDeltaMeters
            ?: if (!gps.altitude.isNaN()) baroAltitude - gps.altitude else null
        val baroConfidence = baroResult?.confidenceLevel ?: ConfidenceLevel.LOW
        val qnhCalibrationAgeSeconds = baroResult?.lastCalibrationTime?.takeIf { it > 0L }?.let {
            val delta = (currentTime - it) / 1000L
            if (delta < 0) 0L else delta
        } ?: -1L

        // Update AGL (async network call) - uses baro altitude and speed for ground detection
        flightHelpers.updateAGL(baroAltitude, gps, gps.speed)

        // Calculate wind speed and direction
        val windData = flightHelpers.calculateWindSpeed(gps)

        // Calculate thermal average
        val thermalAvg = flightHelpers.calculateThermalAverage(verticalSpeed.toFloat(), baroAltitude)

        // Calculate L/D ratio
        val calculatedLD = flightHelpers.calculateCurrentLD(gps, baroAltitude)

        // Calculate netto variometer (uses TE-compensated vertical speed)
        val netto = flightHelpers.calculateNetto(verticalSpeed.toFloat(), gps.speed.toFloat())
        val altitudeForAirspeed = when {
            baroAltitude.isFinite() && baroAltitude != 0.0 -> baroAltitude
            gps.altitude.isFinite() -> gps.altitude
            else -> 0.0
        }
        val airspeedEstimate = estimateAirspeeds(
            netto = netto,
            verticalSpeed = verticalSpeed,
            altitudeMeters = altitudeForAirspeed,
            qnhHpa = qnh
        )
        val indicatedAirspeedMs = airspeedEstimate?.indicatedMs ?: 0.0
        val trueAirspeedMs = airspeedEstimate?.trueMs
            ?: if (gps.speed.isFinite()) gps.speed.toDouble() else indicatedAirspeedMs

        // Get all vario results from cached updates
        val varioResults = mapOf(
            "optimized" to varioOptimized.getVerticalSpeed(),
            "legacy" to varioLegacy.getVerticalSpeed(),
            "raw" to varioRaw.getVerticalSpeed(),
            "gps" to varioGPS.getVerticalSpeed(),
            "complementary" to varioComplementary.getVerticalSpeed()
        )

        // Determine data quality string
        val dataQuality = buildString {
            append("GPS")
            if (baro != null) append("+BARO")
            if (compass != null) append("+COMPASS")
            if (cachedVarioResult != null) append("+IMU")
            append("+TE")  // Total Energy always active (uses GPS speed)
            if (flightHelpers.currentAGL > 0) append("+AGL")
            append("+50Hz")  // Priority 2: High-speed vario
        }

        // Create complete flight data
        val flightData = CompleteFlightData(
            gps = gps,
            baro = baro,
            compass = compass,
            baroAltitude = baroAltitude,
            qnh = qnh,
            isQNHCalibrated = isQNHCalibrated,
            verticalSpeed = verticalSpeed,
            pressureAltitude = pressureAltitude,
            baroGpsDelta = baroGpsDelta,
            baroConfidence = baroConfidence,
            qnhCalibrationAgeSeconds = qnhCalibrationAgeSeconds,
            agl = flightHelpers.currentAGL,
            windSpeed = windData.speed,
            windDirection = windData.direction,
            thermalAverage = thermalAvg,
            currentLD = calculatedLD,
            netto = netto,
            trueAirspeed = trueAirspeedMs,
            indicatedAirspeed = indicatedAirspeedMs,
            varioOptimized = varioResults["optimized"] ?: 0.0,
            varioLegacy = varioResults["legacy"] ?: 0.0,
            varioRaw = varioResults["raw"] ?: 0.0,
            varioGPS = varioResults["gps"] ?: 0.0,
            varioComplementary = varioResults["complementary"] ?: 0.0,
            timestamp = currentTime,
            dataQuality = dataQuality
        )

        _flightDataFlow.value = flightData

        // Update last update time for delta calculation
        lastUpdateTime = currentTime

        // Log occasionally (every second)
        if (currentTime % 1000 < 100) {
            val varioMode = if (cachedVarioResult != null) "PRIORITY2-50Hz(IMU+BARO)+TE" else "PRIORITY2-50Hz(BARO)+TE"

            Log.d(TAG, "[SLOW GPS 10Hz] $varioMode: GPS alt=${gps.altitude.toInt()}m, " +
                      "Baro alt=${baroAltitude.toInt()}m, " +
                      "Raw V/S=${String.format("%.2f", rawVerticalSpeed)}m/s, " +
                      "TE V/S=${String.format("%.2f", verticalSpeed)}m/s, " +
                      "Speed=${String.format("%.1f", currentSpeed)}m/s, " +
                      "AGL=${flightHelpers.currentAGL.toInt()}m")
        }
    }

    /**
     * Stop audio engine (SRTM terrain database needs no cleanup)
     */
    fun stop() {
        audioEngine.stop()
        audioEngine.release()
        Log.d(TAG, "FlightDataCalculator stopped")
    }

    private data class AirspeedEstimate(val indicatedMs: Double, val trueMs: Double)

    /**
     * Estimate IAS/TAS by matching the observed still-air sink to the selected glider's polar.
     * The polar lookup yields TAS; IAS is derived by applying the local air-density correction.
     */
    private fun estimateAirspeeds(
        netto: Float,
        verticalSpeed: Double,
        altitudeMeters: Double,
        qnhHpa: Double
    ): AirspeedEstimate? {
        val sinkEstimate = kotlin.math.abs(netto.toDouble() - verticalSpeed)
        if (!sinkEstimate.isFinite() || sinkEstimate < MIN_SINK_FOR_IAS_MS) {
            return null
        }
        val tasMs = findSpeedForSink(sinkEstimate) ?: return null
        val densityRatio = computeDensityRatio(altitudeMeters, qnhHpa)
        val indicatedMs = if (densityRatio > 0.0) tasMs * sqrt(densityRatio) else tasMs
        return AirspeedEstimate(indicatedMs = indicatedMs, trueMs = tasMs)
    }

    private fun findSpeedForSink(targetSinkMs: Double): Double? {
        var speed = IAS_SCAN_MIN_MS
        var bestSpeed: Double? = null
        var bestError = Double.POSITIVE_INFINITY
        while (speed <= IAS_SCAN_MAX_MS) {
            val sink = sinkProvider.sinkAtSpeed(speed) ?: break
            val error = abs(sink - targetSinkMs)
            if (error < bestError) {
                bestError = error
                bestSpeed = speed
            }
            speed += IAS_SCAN_STEP_MS
        }
        return bestSpeed
    }

    private fun computeDensityRatio(altitudeMeters: Double, qnhHpa: Double): Double {
        val tempSeaLevelK = SEA_LEVEL_TEMP_CELSIUS + 273.15
        val tempAtAltitude = tempSeaLevelK + (TEMP_LAPSE_RATE_C_PER_M * altitudeMeters)
        val base = 1.0 + (TEMP_LAPSE_RATE_C_PER_M * altitudeMeters) / tempSeaLevelK
        val exponent = (-GRAVITY * 0.0289644) / (GAS_CONSTANT * TEMP_LAPSE_RATE_C_PER_M)
        val pressureRatio = base.pow(exponent)
        val pressureAtAltPa = (qnhHpa * 100.0) * pressureRatio
        val densityAtAlt = pressureAtAltPa / (GAS_CONSTANT * tempAtAltitude)
        val seaLevelDensity = (SEA_LEVEL_PRESSURE_HPA * 100.0) / (GAS_CONSTANT * tempSeaLevelK)
        return densityAtAlt / seaLevelDensity
    }

    fun setImuAssistEnabled(enabled: Boolean) {
        imuAssistEnabled = enabled
        Log.d(TAG, "IMU assist toggled -> $enabled")
    }

    /**
     * Manually set QNH based on pilot input.
     */
    fun setManualQnh(qnhHPa: Double) {
        baroCalculator.setQNH(qnhHPa)
        cachedBaroResult = null
        cachedVarioResult = null
        Log.i(TAG, "Manual QNH applied: ${qnhHPa}")
    }

    /**
     * Reset to standard atmosphere and allow auto calibration.
     */
    fun resetQnhToStandard() {
        baroCalculator.resetToStandardAtmosphere()
        cachedBaroResult = null
        cachedVarioResult = null
        Log.i(TAG, "QNH reset to standard atmosphere (auto calibration enabled)")
    }
}
