package com.example.xcpro.sensors

import android.content.Context
import android.util.Log
import com.example.dfcards.calculations.BarometricAltitudeCalculator
import com.example.dfcards.calculations.ConfidenceLevel
import com.example.dfcards.dfcards.calculations.SimpleAglCalculator
import com.example.xcpro.audio.VarioAudioController
import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.flightdata.FlightDisplayMapper
import com.example.xcpro.flightdata.FlightDisplaySnapshot
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.vario.*  // NEW: Vario implementations for side-by-side testing
import com.example.xcpro.sensors.FlightDataConstants
import com.example.xcpro.sensors.FlightFilters
import com.example.xcpro.sensors.VarioDiagnosticsSample
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.sensors.domain.CalculateFlightMetricsUseCase
import com.example.xcpro.sensors.domain.FlightMetricsRequest
import com.example.xcpro.sensors.domain.WindEstimator
import com.example.xcpro.weather.wind.data.WindState
import com.example.xcpro.weather.wind.model.WindSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.abs
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
): SensorFusionRepository {

    companion object {
        private const val TAG = FlightDataConstants.TAG
        private const val LOG_THERMAL_METRICS = FlightDataConstants.LOG_THERMAL_METRICS
        private const val DEFAULT_MACCREADY = FlightDataConstants.DEFAULT_MACCREADY

        // History sizes
        private const val MAX_LOCATION_HISTORY = FlightDataConstants.MAX_LOCATION_HISTORY
        private const val MAX_VSPEED_HISTORY = FlightDataConstants.MAX_VSPEED_HISTORY

        // L/D calculation
        private const val LD_CALCULATION_INTERVAL = FlightDataConstants.LD_CALCULATION_INTERVAL

        // QNH jump suppression
        private const val QNH_JUMP_THRESHOLD_HPA = FlightDataConstants.QNH_JUMP_THRESHOLD_HPA
        private const val QNH_ALTITUDE_JUMP_THRESHOLD_METERS = FlightDataConstants.QNH_ALTITUDE_JUMP_THRESHOLD_METERS
        private const val QNH_CALIBRATION_ACCURACY_THRESHOLD = FlightDataConstants.QNH_CALIBRATION_ACCURACY_THRESHOLD
        private const val VARIO_VALIDITY_MS = FlightDataConstants.VARIO_VALIDITY_MS
        private const val REPLAY_VARIO_MAX_AGE_MS = FlightDataConstants.REPLAY_VARIO_MAX_AGE_MS
    }

    // History for calculations (shared with helper) - must be initialized first
    private val locationHistory = mutableListOf<LocationWithTime>()

    // Calculation modules (reuse existing code) - aglCalculator must be first!
    private val aglCalculator = SimpleAglCalculator(context)  // KISS: SRTM terrain database
    private val baroCalculator = BarometricAltitudeCalculator(aglCalculator)  // 🚀 SRTM-based QNH calibration
    private val filters = FlightFilters()

    // Flight calculation helpers (extracted to maintain 500-line limit)
    private val flightHelpers = FlightCalculationHelpers(
        scope = scope,
        aglCalculator = aglCalculator,
        locationHistory = locationHistory,
        sinkProvider = sinkProvider
    )
    private val flightMetricsUseCase = CalculateFlightMetricsUseCase(
        flightHelpers = flightHelpers,
        sinkProvider = sinkProvider,
        windEstimator = WindEstimator(sinkProvider)
    )

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

    private val audioController = VarioAudioController(context, scope, enableAudio)
    val audioEngine get() = audioController.engine
    private val flightDisplayMapper = FlightDisplayMapper()

    // StateFlow - Single Source of Truth for calculated flight data
    private val _flightDataFlow = MutableStateFlow<CompleteFlightData?>(null)
    override val flightDataFlow: StateFlow<CompleteFlightData?> = _flightDataFlow.asStateFlow()

    private val _diagnosticsFlow = MutableStateFlow<VarioDiagnosticsSample?>(null)
    override val diagnosticsFlow: StateFlow<VarioDiagnosticsSample?> = _diagnosticsFlow.asStateFlow()
    override val audioSettings: StateFlow<VarioAudioSettings> = audioController.engine.settings

    @Volatile
    private var replayRealVarioMs: Double? = null
    @Volatile
    private var replayRealVarioTimestamp: Long = 0L

    private var lastReplayBaroTimestamp: Long = 0L

    // Tracking for delta time calculation
    private var lastUpdateTime = 0L
    private var lastVarioUpdateTime = 0L  // For high-speed vario loop
    private var varioValidUntil = 0L

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

        val smoothedPressure = filters.pressureKalmanFilter.update(baro.pressureHPa.value, baro.timestamp)

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
                    filters.baroFilter.reset()
                    filters.pressureKalmanFilter.reset(smoothedPressure, baro.timestamp)
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

        val filteredBaro = filters.baroFilter.processReading(
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
        cachedGPSLat = gps.latLng.latitude   // dYs? For SRTM-based QNH calibration
        cachedGPSLon = gps.latLng.longitude  // dYs? For SRTM-based QNH calibration
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
        val windState = latestWindState

        val gpsVarioValue = varioGPS.getVerticalSpeed().takeIf { it.isFinite() } ?: 0.0
        val metrics = flightMetricsUseCase.execute(
            FlightMetricsRequest(
                gps = gps,
                currentTimeMillis = currentTime,
                deltaTimeSeconds = deltaTime,
                varioResult = varioResult,
                varioGpsValue = gpsVarioValue,
                baroResult = baroResult,
                windState = windState,
                varioValidUntil = varioValidUntil
            )
        )

        latestTeVario = metrics.teVario

        val baroAltitude = metrics.baroAltitude
        val verticalSpeed = metrics.verticalSpeed
        val varioSource = metrics.varioSource
        val varioResults = mapOf(
            "optimized" to varioOptimized.getVerticalSpeed(),
            "legacy" to varioLegacy.getVerticalSpeed(),
            "raw" to varioRaw.getVerticalSpeed(),
            "gps" to varioGPS.getVerticalSpeed(),
            "complementary" to varioComplementary.getVerticalSpeed()
        )

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

        val snapshot = FlightDisplaySnapshot(
            gps = gps,
            baro = baro,
            compass = compass,
            metrics = metrics,
            aglMeters = flightHelpers.currentAGL,
            varioResults = varioResults,
            replayIgcVario = replayIgcVario,
            dataQuality = dataQuality,
            timestamp = currentTime,
            macCready = macCreadySetting,
            macCreadyRisk = macCreadyRisk
        )
        val flightData = flightDisplayMapper.map(snapshot)

        if (LOG_THERMAL_METRICS && currentTime - lastThermalLogTime >= 1000L) {
            Log.d(
                TAG,
                "Thermal metrics: TC30=${flightData.thermalAverage.value} TC_AVG=${flightData.thermalAverageCircle.value} T_AVG=${flightData.thermalAverageTotal.value} TC_GAIN=${flightData.thermalGain.value}"
            )
            lastThermalLogTime = currentTime
        }

        _flightDataFlow.value = flightData

        // Update last update time for delta calculation
        lastUpdateTime = currentTime

        // Log occasionally (every second)
        if (currentTime % 1000 < 100) {
            val varioMode = if (cachedVarioResult != null) "PRIORITY2-50Hz(IMU+BARO)+TE" else "PRIORITY2-50Hz(BARO)+TE"

            Log.d(
                TAG,
                "[SLOW GPS 10Hz] $varioMode: GPS alt=${gps.altitude.value.toInt()}m, " +
                    "Baro alt=${baroAltitude.toInt()}m, " +
                    "Raw V/S=${String.format("%.2f", varioResult.verticalSpeed)}m/s, " +
                    "TE V/S=${String.format("%.2f", verticalSpeed)}m/s, " +
                    "Speed=${String.format("%.1f", gps.speed.value)}m/s, " +
                    "AGL=${flightHelpers.currentAGL.toInt()}m"
            )
        }
    }
    /**
     * Stop audio engine (SRTM terrain database needs no cleanup)
     */
    override fun updateAudioSettings(settings: VarioAudioSettings) {
        audioController.engine.updateSettings(settings)
    }

    override fun stop() {
        audioController.stop()
        filters.pressureKalmanFilter.reset()
        varioValidUntil = 0L
        replayRealVarioMs = null
        replayRealVarioTimestamp = 0L
        lastReplayBaroTimestamp = 0L
        latestTeVario = null
        flightMetricsUseCase.reset()
        Log.d(TAG, "FlightDataCalculator stopped")
    }

    /**
     * Manually set QNH based on pilot input.
     */
    override fun setManualQnh(qnhHPa: Double) {
        baroCalculator.setQNH(qnhHPa)
        cachedBaroResult = null
        cachedVarioResult = null
        Log.i(TAG, "Manual QNH applied: ${qnhHPa}")
    }

    override fun setMacCreadySetting(value: Double) {
        macCreadySetting = value
    }

    override fun setMacCreadyRisk(value: Double) {
        macCreadyRisk = value
    }

    override fun updateReplayRealVario(realVarioMs: Double?) {
        if (!isReplayMode) return
        replayRealVarioMs = realVarioMs
        replayRealVarioTimestamp = System.currentTimeMillis()
    }

    /**
     * Reset to standard atmosphere and allow auto calibration.
     */
    override fun resetQnhToStandard() {
        baroCalculator.resetToStandardAtmosphere()
        cachedBaroResult = null
        cachedVarioResult = null
        Log.i(TAG, "QNH reset to standard atmosphere (auto calibration enabled)")
    }

    private fun updateAudioFeed(currentTime: Long, rawVario: Double) {
        audioController.update(latestTeVario, rawVario, currentTime, varioValidUntil)
    }
}



