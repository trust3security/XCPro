package com.example.xcpro.sensors

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.dfcards.calculations.BarometricAltitudeCalculator
import com.example.dfcards.calculations.ConfidenceLevel
import com.example.dfcards.filters.AdvancedBarometricFilter
import com.example.dfcards.dfcards.calculations.SimpleAglCalculator
import com.example.xcpro.audio.VarioAudioEngine
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.vario.*  // NEW: Vario implementations for side-by-side testing
import com.example.xcpro.sensors.VarioDiagnosticsSample
import com.example.xcpro.weather.wind.data.WindState
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindVector
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.VerticalSpeedMs
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
    private val sensorDataSource: SensorDataSource,
    private val scope: CoroutineScope,
    private val sinkProvider: StillAirSinkProvider,
    private val windStateFlow: StateFlow<WindState>,
    private val enableAudio: Boolean = true,
    private val isReplayMode: Boolean = false
) {

    companion object {
        private const val TAG = "FlightDataCalculator"
        private const val LOG_THERMAL_METRICS = false
        private const val DEFAULT_MACCREADY = 0.0

        // History sizes
        private const val MAX_LOCATION_HISTORY = 20
        private const val MAX_VSPEED_HISTORY = 10

        // Wind calculation

        // L/D calculation
        private const val LD_CALCULATION_INTERVAL = 5000L     // ms

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
        private const val VARIO_VALIDITY_MS = 500L
        private const val AVERAGE_WINDOW_SECONDS = 30
        private const val DISPLAY_VAR_CLAMP = 7.0
        private const val DISPLAY_SMOOTH_TIME_S = 0.6
        private const val DISPLAY_DECAY_FACTOR = 0.9
        private const val NETTO_DISPLAY_WINDOW_MS = 5_000L
        private const val REPLAY_VARIO_MAX_AGE_MS = 5_000L
    }

    // History for calculations (shared with helper) - must be initialized first
    private val locationHistory = mutableListOf<LocationWithTime>()

    // Calculation modules (reuse existing code) - aglCalculator must be first!
    private val aglCalculator = SimpleAglCalculator(context)  // KISS: SRTM terrain database
    private val baroCalculator = BarometricAltitudeCalculator(aglCalculator)  // 🚀 SRTM-based QNH calibration
    private val baroFilter = AdvancedBarometricFilter()  // Old 2-state filter (fallback)
    private val pressureKalmanFilter = PressureKalmanFilter()

    // Flight calculation helpers (extracted to maintain 500-line limit)
    private val flightHelpers = FlightCalculationHelpers(
        scope = scope,
        aglCalculator = aglCalculator,
        locationHistory = locationHistory,
        sinkProvider = sinkProvider
    )
    private val circlingDetector = CirclingDetector()

    private var latestWindState: WindState? = null

    init {
        scope.launch {
            windStateFlow.collect { latestWindState = it }
        }
    }

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

    private val _diagnosticsFlow = MutableStateFlow<VarioDiagnosticsSample?>(null)
    val diagnosticsFlow: StateFlow<VarioDiagnosticsSample?> = _diagnosticsFlow.asStateFlow()

    @Volatile
    private var replayRealVarioMs: Double? = null
    @Volatile
    private var replayRealVarioTimestamp: Long = 0L

    private var lastReplayBaroTimestamp: Long = 0L

    // Tracking for delta time calculation
    private var lastUpdateTime = 0L
    private var lastVarioUpdateTime = 0L  // For high-speed vario loop
    private var varioValidUntil = 0L

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

    // Cached results from vario loop for GPS loop to use
    private var cachedVarioResult: com.example.dfcards.filters.ModernVarioResult? = null
    private var cachedBaroResult: com.example.dfcards.calculations.BarometricAltitudeData? = null
    private var cachedBaroData: BaroData? = null
    private var cachedCompassData: CompassData? = null
    @Volatile private var latestTeVario: Double? = null
    private val bruttoAverageWindow = FixedSampleAverageWindow(AVERAGE_WINDOW_SECONDS)
    private val nettoAverageWindow = FixedSampleAverageWindow(AVERAGE_WINDOW_SECONDS)
    private val nettoDisplayWindow = TimedAverageWindow(NETTO_DISPLAY_WINDOW_MS)
    private var displayVarioState = 0.0
    private var displayNettoState = 0.0
    private var lastBruttoSampleTime = 0L
    private var lastNettoSampleTime = 0L
    private var lastNettoValue = Double.NaN
    private var lastThermalState = false
    private var lastThermalLogTime = 0L
    private var macCreadySetting = DEFAULT_MACCREADY
    private var macCreadyRisk = DEFAULT_MACCREADY

    init {
        // ✅ PRIORITY 2: DECOUPLED SAMPLE RATES
        // High-speed vario loop (50Hz) + Slow GPS loop (10Hz)

        // HIGH-SPEED VARIO LOOP: Barometer + IMU (50Hz - unleashed!)
        scope.launch {
            combine(
                sensorDataSource.baroFlow,
                sensorDataSource.accelFlow
            ) { baro, accel ->
                Pair(baro, accel)
            }.collect { (baro, accel) ->
                updateVarioFilter(baro, accel)
            }
        }

        // SLOW GPS LOOP: GPS + Compass (10Hz - navigation data)
        scope.launch {
            combine(
                sensorDataSource.gpsFlow,
                sensorDataSource.compassFlow
            ) { gps, compass ->
                Pair(gps, compass)
            }.collect { (gps, compass) ->
                updateGPSData(gps, compass)
            }
        }

        // Initialize audio engine for professional vario audio
        if (enableAudio) {
            scope.launch {
                val audioInitialized = audioEngine.initialize()
                if (audioInitialized) {
                    audioEngine.start()
                    Log.i(TAG, "Vario audio engine initialized and started (PRIORITY 2: High-speed mode)")
                } else {
                    Log.w(TAG, "Failed to initialize vario audio engine")
                }
            }
        }

        Log.d(TAG, "FlightDataCalculator initialized with PRIORITY 2: Decoupled sample rates (50Hz vario + 10Hz GPS)")
    }

    /**
     * ✅ PRIORITY 2: HIGH-SPEED VARIO LOOP (50Hz)
     *
     * Updates vario filter with barometer data at maximum speed
     * Uses cached GPS data (last known values) for motion detection
     * Immediately updates audio engine for zero-lag thermal detection
     */
    private fun updateVarioFilter(baro: BaroData?, accel: AccelData?) {
        if (baro == null) {
            Log.d(TAG, "No barometer data - skipping vario update")
            return
        }

        val currentTime = System.currentTimeMillis()

        val replayDeltaTime = if (isReplayMode && lastReplayBaroTimestamp > 0L) {
            val deltaMs = (baro.timestamp - lastReplayBaroTimestamp).coerceAtLeast(1L)
            deltaMs / 1000.0
        } else {
            null
        }
        val deltaTime = when {
            replayDeltaTime != null -> replayDeltaTime
            lastVarioUpdateTime > 0 -> (currentTime - lastVarioUpdateTime) / 1000.0
            else -> 0.02 // 50Hz = 20ms = 0.02s default
        }
        if (isReplayMode) {
            lastReplayBaroTimestamp = baro.timestamp
        }

        if (deltaTime < 0.01) {
            return
        }

        val smoothedPressure = pressureKalmanFilter.update(baro.pressureHPa.value, baro.timestamp)

        val previousBaroResult = cachedBaroResult
        val hasCalibrationFix = cachedIsGPSFixed &&
            !cachedGPSAltitude.isNaN() &&
            cachedGPSAccuracy <= QNH_CALIBRATION_ACCURACY_THRESHOLD

        val baroResult = baroCalculator.calculateBarometricAltitude(
            rawPressureHPa = smoothedPressure,
            gpsAltitudeMeters = if (hasCalibrationFix) cachedGPSAltitude else null,
            gpsAccuracy = if (hasCalibrationFix) cachedGPSAccuracy else null,
            isGPSFixed = hasCalibrationFix,
            gpsLat = cachedGPSLat.takeIf { hasCalibrationFix },
            gpsLon = cachedGPSLon.takeIf { hasCalibrationFix }
        )

        if (previousBaroResult != null) {
            val qnhDelta = abs(baroResult.qnh - previousBaroResult.qnh)
            val altitudeDelta = abs(baroResult.altitudeMeters - previousBaroResult.altitudeMeters)
            val qnhJumpDetected = qnhDelta > QNH_JUMP_THRESHOLD_HPA ||
                altitudeDelta > QNH_ALTITUDE_JUMP_THRESHOLD_METERS
            if (qnhJumpDetected) {
                val qnhLabel = String.format(Locale.US, "%.2f", qnhDelta)
                val altitudeLabel = String.format(Locale.US, "%.1f", altitudeDelta)
                if (isReplayMode) {
                    Log.w(
                        TAG,
                        "Replay QNH jump Δ${qnhLabel} hPa / Δ${altitudeLabel} m - ignoring reset to keep vario stable"
                    )
                } else {
                    Log.w(
                        TAG,
                        "QNH jump detected Δ${qnhLabel} hPa / Δ${altitudeLabel} m - resetting vario filters"
                    )
                    varioOptimized.reset()
                    varioLegacy.reset()
                    varioRaw.reset()
                    varioGPS.reset()
                    varioComplementary.reset()
                    baroFilter.reset()
                    pressureKalmanFilter.reset(smoothedPressure, baro.timestamp)
                    cachedVarioResult = null
                    varioValidUntil = 0L
                }
            }
        }

        val verticalAccelForFusion = 0.0

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

        val filteredBaro = baroFilter.processReading(
            rawBaroAltitude = baroResult.altitudeMeters,
            gpsAltitude = cachedGPSAltitude,
            gpsAccuracy = cachedGPSAccuracy
        )
        val varioResult = com.example.dfcards.filters.ModernVarioResult(
            altitude = filteredBaro.displayAltitude,
            verticalSpeed = filteredBaro.verticalSpeed,
            acceleration = 0.0,
            confidence = filteredBaro.confidence
        )

        varioValidUntil = currentTime + VARIO_VALIDITY_MS
        updateAudioFeed(currentTime, varioResult.verticalSpeed)

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
        cachedGPSSpeed = gps.speed.value
        cachedGPSAltitude = gps.altitude.value
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
            altitude = gps.altitude.value,
            verticalSpeed = 0.0,
            acceleration = 0.0,
            confidence = 0.3
        )

        val baroResult = cachedBaroResult
        val baro = cachedBaroData

        // ✅ TOTAL ENERGY (TE) COMPENSATION: Remove false lift from pilot maneuvers
        val rawVerticalSpeed = varioResult.verticalSpeed
        val currentSpeed = gps.speed.value

        val teVerticalSpeed = flightHelpers.calculateTotalEnergy(
            rawVario = rawVerticalSpeed,
            currentSpeed = currentSpeed,
            previousSpeed = previousGPSSpeed,
            deltaTime = deltaTime
        )

        // Update previous speed for next calculation
        previousGPSSpeed = currentSpeed

        // Use TE-compensated vertical speed (removes stick thermals!)
        val teVario = if (currentTime <= varioValidUntil) teVerticalSpeed else null
        latestTeVario = teVario

        val gpsVarioValue = varioGPS.getVerticalSpeed().takeIf { it.isFinite() } ?: 0.0
        val bruttoVario = teVario ?: gpsVarioValue
        val varioSource = if (teVario != null) "TE" else "GPS"
        val varioValid = teVario != null

        val baroAltitude = varioResult.altitude
        val verticalSpeed = bruttoVario  // Selected V/S (TE preferred, GPS fallback)
        val qnh = baroResult?.qnh ?: 1013.25
        val isQNHCalibrated = baroResult?.isCalibrated ?: false

        val pressureAltitude = baroResult?.pressureAltitudeMeters ?: baroAltitude
        val baroGpsDelta = baroResult?.gpsDeltaMeters
            ?: if (!gps.altitude.value.isNaN()) baroAltitude - gps.altitude.value else null
        val baroConfidence = baroResult?.confidenceLevel ?: ConfidenceLevel.LOW
        val qnhCalibrationAgeSeconds = baroResult?.lastCalibrationTime?.takeIf { it > 0L }?.let {
            val delta = (currentTime - it) / 1000L
            if (delta < 0) 0L else delta
        } ?: -1L
        val altitudeForAirspeed = when {
            baroAltitude.isFinite() && baroAltitude != 0.0 -> baroAltitude
            gps.altitude.value.isFinite() -> gps.altitude.value
            else -> 0.0
        }
        val windState = latestWindState
        val windVector = windState?.vector

        val isCircling = circlingDetector.update(
            trackDegrees = gps.bearing,
            timestampMillis = gps.timestamp,
            groundSpeed = gps.speed.value
        )

        // Update AGL (async network call) - uses baro altitude and speed for ground detection
        flightHelpers.updateAGL(baroAltitude, gps, gps.speed.value)

        // Maintain history for L/D and replay analytics
        flightHelpers.recordLocationSample(gps)

        // Calculate L/D ratio
        val calculatedLD = flightHelpers.calculateCurrentLD(gps, baroAltitude)

        val airspeedFromWind = estimateFromWind(
            gpsSpeed = gps.speed.value,
            gpsBearingDeg = gps.bearing,
            altitudeMeters = altitudeForAirspeed,
            qnhHpa = qnh,
            windVector = windVector
        )

        val nettoResult = flightHelpers.calculateNetto(
            currentVerticalSpeed = verticalSpeed,
            trueAirspeed = airspeedFromWind?.trueMs,
            fallbackGroundSpeed = gps.speed.value
        )
        val netto = nettoResult.value.toFloat()
        val nettoValid = nettoResult.valid
        val thermalActive = isCircling
        val nettoSampleValue = resolveNettoSampleValue(nettoResult.value, nettoValid)
        updateAverageWindows(
            currentTime = currentTime,
            bruttoSample = bruttoVario,
            nettoSample = nettoSampleValue,
            thermalActive = thermalActive
        )
        if (nettoValid) {
            lastNettoValue = nettoResult.value
            nettoDisplayWindow.addSample(currentTime, nettoResult.value)
        } else {
            nettoDisplayWindow.clear()
        }
        val bruttoAverage30s = bruttoAverageWindow.average()
        val nettoAverage30s = nettoAverageWindow.average()
        val displayVario = smoothDisplayVario(bruttoVario, deltaTime, varioValid)
        val rawDisplayNetto = if (!nettoDisplayWindow.isEmpty()) {
            nettoDisplayWindow.average()
        } else {
            nettoResult.value
        }
        val displayNetto = smoothDisplayNetto(rawDisplayNetto, deltaTime, nettoValid)
        val airspeedEstimate = airspeedFromWind ?: if (nettoValid) {
            estimateFromPolarSink(
                netto = netto,
                verticalSpeed = verticalSpeed,
                altitudeMeters = altitudeForAirspeed,
                qnhHpa = qnh
            )
        } else {
            null
        }
        val indicatedAirspeedMs = airspeedEstimate?.indicatedMs ?: 0.0
        val trueAirspeedMs = airspeedEstimate?.trueMs
            ?: if (gps.speed.value.isFinite()) gps.speed.value else indicatedAirspeedMs
        val airspeedSourceLabel = (airspeedEstimate?.source ?: AirspeedSource.GPS_GROUND).label
        val tasValid = trueAirspeedMs.isFinite() && trueAirspeedMs > 0.5

        val teAltitude = computeTotalEnergyAltitude(baroAltitude, trueAirspeedMs)
        flightHelpers.updateThermalState(
            timestampMillis = currentTime,
            teAltitudeMeters = teAltitude,
            verticalSpeedMs = bruttoVario,
            isCircling = isCircling
        )
        val thermalAvgCircle = flightHelpers.thermalAverageCurrent
        val thermalAvgTotal = flightHelpers.thermalAverageTotal
        val thermalGain = flightHelpers.thermalGainCurrent

        val windSpeedValue = windVector?.speed?.toFloat() ?: 0f
        val windDirectionFrom = windVector?.directionFromDeg?.toFloat() ?: 0f
        val windHeadwind = windState?.headwind ?: 0.0
        val windCrosswind = windState?.crosswind ?: 0.0
        val windQuality = windState?.quality ?: 0
        val windSource = windState?.source ?: WindSource.NONE

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
            append("+VARIO:")
            append(varioSource)
            if (flightHelpers.currentAGL > 0) append("+AGL")
            append("+50Hz")  // Priority 2: High-speed vario
        }

        val replayIgcVario = if (isReplayMode && replayRealVarioTimestamp != 0L) {
            val ageMs = currentTime - replayRealVarioTimestamp
            if (ageMs in 0..REPLAY_VARIO_MAX_AGE_MS) replayRealVarioMs else null
        } else {
            null
        }

        // Create complete flight data
        val flightData = CompleteFlightData(
            gps = gps,
            baro = baro,
            compass = compass,
            baroAltitude = AltitudeM(baroAltitude),
            qnh = PressureHpa(qnh),
            isQNHCalibrated = isQNHCalibrated,
            verticalSpeed = VerticalSpeedMs(verticalSpeed),
            bruttoVario = VerticalSpeedMs(bruttoVario),
            displayVario = VerticalSpeedMs(displayVario),
            bruttoAverage30s = VerticalSpeedMs(bruttoAverage30s),
            nettoAverage30s = VerticalSpeedMs(nettoAverage30s),
            varioSource = varioSource,
            varioValid = varioValid,
            pressureAltitude = AltitudeM(pressureAltitude),
            baroGpsDelta = baroGpsDelta?.let { AltitudeM(it) },
            baroConfidence = baroConfidence,
            qnhCalibrationAgeSeconds = qnhCalibrationAgeSeconds,
            agl = AltitudeM(flightHelpers.currentAGL),
            windSpeed = SpeedMs(windSpeedValue.toDouble()),
            windDirection = windDirectionFrom,
            windHeadwind = SpeedMs(windHeadwind),
            windCrosswind = SpeedMs(windCrosswind),
            windQuality = windQuality,
            windSource = windSource,
            windLastUpdatedMillis = windState?.lastUpdatedMillis ?: 0L,
            thermalAverage = VerticalSpeedMs(bruttoAverage30s),
            thermalAverageCircle = VerticalSpeedMs(thermalAvgCircle.toDouble()),
            thermalAverageTotal = VerticalSpeedMs(thermalAvgTotal.toDouble()),
            thermalGain = AltitudeM(thermalGain),
            currentLD = calculatedLD,
            netto = VerticalSpeedMs(netto.toDouble()),
            displayNetto = VerticalSpeedMs(displayNetto),
            nettoValid = nettoValid,
            trueAirspeed = SpeedMs(trueAirspeedMs),
            indicatedAirspeed = SpeedMs(indicatedAirspeedMs),
            airspeedSource = airspeedSourceLabel,
            tasValid = tasValid,
            varioOptimized = VerticalSpeedMs(varioResults["optimized"] ?: 0.0),
            varioLegacy = VerticalSpeedMs(varioResults["legacy"] ?: 0.0),
            varioRaw = VerticalSpeedMs(varioResults["raw"] ?: 0.0),
            varioGPS = VerticalSpeedMs(varioResults["gps"] ?: 0.0),
            varioComplementary = VerticalSpeedMs(varioResults["complementary"] ?: 0.0),
            realIgcVario = replayIgcVario?.let { VerticalSpeedMs(it) },
            teAltitude = AltitudeM(teAltitude),
            macCready = macCreadySetting,
            macCreadyRisk = macCreadyRisk,
            timestamp = currentTime,
            dataQuality = dataQuality
        )

        if (LOG_THERMAL_METRICS && currentTime - lastThermalLogTime >= 1000L) {
            Log.d(
                TAG,
                "Thermal metrics: TC30=${flightData.thermalAverage.value} " +
                    "TC_AVG=${flightData.thermalAverageCircle.value} " +
                    "T_AVG=${flightData.thermalAverageTotal.value} " +
                    "TC_GAIN=${flightData.thermalGain.value}"
            )
            lastThermalLogTime = currentTime
        }

        _flightDataFlow.value = flightData

        // Update last update time for delta calculation
        lastUpdateTime = currentTime

        // Log occasionally (every second)
        if (currentTime % 1000 < 100) {
            val varioMode = if (cachedVarioResult != null) "PRIORITY2-50Hz(IMU+BARO)+TE" else "PRIORITY2-50Hz(BARO)+TE"

            Log.d(TAG, "[SLOW GPS 10Hz] $varioMode: GPS alt=${gps.altitude.value.toInt()}m, " +
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
        pressureKalmanFilter.reset()
        varioValidUntil = 0L
        replayRealVarioMs = null
        replayRealVarioTimestamp = 0L
        lastReplayBaroTimestamp = 0L
        bruttoAverageWindow.clear()
        nettoAverageWindow.clear()
        nettoDisplayWindow.clear()
        displayVarioState = 0.0
        displayNettoState = 0.0
        lastBruttoSampleTime = 0L
        lastNettoSampleTime = 0L
        lastNettoValue = Double.NaN
        lastThermalState = false
        circlingDetector.reset()
        Log.d(TAG, "FlightDataCalculator stopped")
    }

    private data class AirspeedEstimate(
        val indicatedMs: Double,
        val trueMs: Double,
        val source: AirspeedSource
    )

    private enum class AirspeedSource(val label: String) {
        WIND_VECTOR("WIND"),
        POLAR_SINK("POLAR"),
        GPS_GROUND("GPS")
    }

    private fun computeTotalEnergyAltitude(baroAltitude: Double, trueAirspeed: Double): Double {
        val potential = if (baroAltitude.isFinite()) baroAltitude else 0.0
        val kinetic = if (trueAirspeed.isFinite()) {
            (trueAirspeed * trueAirspeed) / (2.0 * GRAVITY)
        } else {
            0.0
        }
        return potential + kinetic
    }

    private fun estimateFromWind(
        gpsSpeed: Double,
        gpsBearingDeg: Double,
        altitudeMeters: Double,
        qnhHpa: Double,
        windVector: WindVector?
    ): AirspeedEstimate? {
        if (windVector == null || !gpsSpeed.isFinite() || gpsSpeed <= 0.1) return null
        if (!gpsBearingDeg.isFinite()) return null
        val bearingRad = Math.toRadians(gpsBearingDeg)
        val groundEast = gpsSpeed * sin(bearingRad)
        val groundNorth = gpsSpeed * cos(bearingRad)
        val tasEast = groundEast + windVector.east
        val tasNorth = groundNorth + windVector.north
        val tas = hypot(tasEast, tasNorth)
        if (!tas.isFinite() || tas <= 0.1) return null
        val densityRatio = computeDensityRatio(altitudeMeters, qnhHpa)
        val indicated = if (densityRatio > 0.0) tas * sqrt(densityRatio) else tas
        return AirspeedEstimate(indicatedMs = indicated, trueMs = tas, source = AirspeedSource.WIND_VECTOR)
    }

    /**
     * Legacy fallback: match netto vs polar sink to infer TAS, then derive IAS via density ratio.
     */
    private fun estimateFromPolarSink(
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
        return AirspeedEstimate(indicatedMs = indicatedMs, trueMs = tasMs, source = AirspeedSource.POLAR_SINK)
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

    private fun smoothDisplayVario(raw: Double, deltaTime: Double, isValid: Boolean): Double {
        val targetAlpha = (deltaTime / DISPLAY_SMOOTH_TIME_S).coerceIn(0.0, 1.0)
        displayVarioState += targetAlpha * (raw - displayVarioState)
        if (!isValid) {
            displayVarioState *= DISPLAY_DECAY_FACTOR
        }
        if (!displayVarioState.isFinite()) {
            displayVarioState = 0.0
        }
        return displayVarioState.coerceIn(-DISPLAY_VAR_CLAMP, DISPLAY_VAR_CLAMP)
    }

    private fun smoothDisplayNetto(raw: Double, deltaTime: Double, isValid: Boolean): Double {
        val targetAlpha = (deltaTime / DISPLAY_SMOOTH_TIME_S).coerceIn(0.0, 1.0)
        displayNettoState += targetAlpha * (raw - displayNettoState)
        if (!isValid) {
            displayNettoState *= DISPLAY_DECAY_FACTOR
        }
        if (!displayNettoState.isFinite()) {
            displayNettoState = 0.0
        }
        return displayNettoState.coerceIn(-DISPLAY_VAR_CLAMP, DISPLAY_VAR_CLAMP)
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

    private fun updateAverageWindows(
        currentTime: Long,
        bruttoSample: Double,
        nettoSample: Double,
        thermalActive: Boolean
    ) {
        val timeWentBack = currentTime < lastBruttoSampleTime || currentTime < lastNettoSampleTime
        val thermalToggled = thermalActive != lastThermalState
        if (timeWentBack || thermalToggled) {
            resetAverageWindows(bruttoSample, nettoSample, currentTime)
        } else {
            lastBruttoSampleTime = addSamplesForElapsedSeconds(
                window = bruttoAverageWindow,
                lastTimestamp = lastBruttoSampleTime,
                currentTime = currentTime,
                sampleValue = bruttoSample
            )
            lastNettoSampleTime = addSamplesForElapsedSeconds(
                window = nettoAverageWindow,
                lastTimestamp = lastNettoSampleTime,
                currentTime = currentTime,
                sampleValue = nettoSample
            )
        }
        lastThermalState = thermalActive
    }

    private fun addSamplesForElapsedSeconds(
        window: FixedSampleAverageWindow,
        lastTimestamp: Long,
        currentTime: Long,
        sampleValue: Double
    ): Long {
        if (lastTimestamp == 0L) {
            window.seed(sampleValue)
            return currentTime
        }

        if (currentTime < lastTimestamp) {
            window.seed(sampleValue)
            return currentTime
        }

        if (currentTime == lastTimestamp) {
            return lastTimestamp
        }

        var nextTimestamp = lastTimestamp + 1000L
        var latestTimestamp = lastTimestamp
        while (nextTimestamp <= currentTime) {
            window.addSample(sampleValue)
            latestTimestamp = nextTimestamp
            nextTimestamp += 1000L
        }
        return latestTimestamp
    }

    private fun resetAverageWindows(bruttoSample: Double, nettoSample: Double, timestamp: Long) {
        bruttoAverageWindow.seed(bruttoSample)
        nettoAverageWindow.seed(nettoSample)
        lastBruttoSampleTime = timestamp
        lastNettoSampleTime = timestamp
    }

    private fun resolveNettoSampleValue(rawNetto: Double, nettoValid: Boolean): Double {
        if (nettoValid) {
            return rawNetto
        }
        val fallback = lastNettoValue
        return if (!fallback.isNaN()) fallback else rawNetto
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

    fun setMacCreadySetting(value: Double) {
        macCreadySetting = value
    }

    fun setMacCreadyRisk(value: Double) {
        macCreadyRisk = value
    }

    fun updateReplayRealVario(realVarioMs: Double?) {
        if (!isReplayMode) return
        replayRealVarioMs = realVarioMs
        replayRealVarioTimestamp = System.currentTimeMillis()
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

    private fun updateAudioFeed(currentTime: Long, rawVario: Double) {
        if (!enableAudio) {
            return
        }

        val teSample = latestTeVario
        when {
            teSample != null && currentTime <= varioValidUntil -> audioEngine.updateVerticalSpeed(teSample)
            currentTime <= varioValidUntil -> audioEngine.updateVerticalSpeed(rawVario)
            else -> audioEngine.setSilence()
        }
    }
}

