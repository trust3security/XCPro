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

         // IMU fusion tuning (real-world phone robustness)
         private const val ACCEL_FRESHNESS_MS = 250L
         private const val ACCEL_SMOOTH_TAU_S = 0.15
         private const val MAX_VERTICAL_ACCEL_MS2 = 8.0
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
    private val varioSuite = VarioSuite()

    // ✅ PROFESSIONAL VARIO AUDIO ENGINE (zero-lag audio feedback)

    private val audioController = VarioAudioController(context, scope, enableAudio)
    val audioEngine get() = audioController.engine
    private val flightDisplayMapper = FlightDisplayMapper()

    // Disable GPS-based QNH auto-calibration by default; can be toggled from UI.
    @Volatile
    private var autoQnhEnabled = false

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
    private var lastReplayGpsLogTime: Long = 0L

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

     // IMU vertical acceleration smoothing for 3‑state Kalman / complementary fusion.
     private var lastAccelTimestamp: Long = 0L
     private var smoothedVerticalAccel: Double? = null
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
                try {
                    updateVarioFilter(baro, accel)
                } catch (t: Throwable) {
                    Log.e(TAG, "Vario loop error", t)
                }
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
                try {
                    updateGPSData(gps, compass)
                } catch (t: Throwable) {
                    Log.e(TAG, "GPS loop error", t)
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

        // In replay mode, downstream estimators (wind, circling, etc.) use the sensor timestamps as
        // the "simulation clock". Keep the vario validity clock in the same time base.
        val currentTime = if (isReplayMode) baro.timestamp else System.currentTimeMillis()

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
        val hasCalibrationFix = autoQnhEnabled &&
            cachedIsGPSFixed &&
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
                    varioSuite.resetAll()
                    filters.baroFilter.reset()
                    filters.pressureKalmanFilter.reset(smoothedPressure, baro.timestamp)
                    cachedVarioResult = null
                    varioValidUntil = 0L
                }
            }
        }

        val verticalAccelForFusion = accel?.let { accelSample ->
            val ageMs = currentTime - accelSample.timestamp
            val fresh = ageMs in 0..ACCEL_FRESHNESS_MS
            if (!accelSample.isReliable || !fresh) {
                0.0
            } else {
                val clamped = accelSample.verticalAcceleration
                    .coerceIn(-MAX_VERTICAL_ACCEL_MS2, MAX_VERTICAL_ACCEL_MS2)
                val dt = deltaTime.coerceAtLeast(1e-3)
                val alpha = dt / (ACCEL_SMOOTH_TAU_S + dt)
                val prev = smoothedVerticalAccel
                val next = if (prev == null || accelSample.timestamp <= lastAccelTimestamp) {
                    clamped
                } else {
                    prev + alpha * (clamped - prev)
                }
                lastAccelTimestamp = accelSample.timestamp
                smoothedVerticalAccel = next
                next
            }
        } ?: 0.0

        varioSuite.updateAll(
            baroAltitude = baroResult.altitudeMeters,
            verticalAccel = verticalAccelForFusion,
            deltaTime = deltaTime,
            gpsSpeed = cachedGPSSpeed,
            gpsAltitude = cachedGPSAltitude
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

        val wallTime = System.currentTimeMillis()
        // Use GPS timestamps as the "simulation clock" in replay mode so time-based metrics (wind,
        // thermal windows, circling detection) advance with the IGC log instead of wall clock.
        val currentTime = if (isReplayMode) gps.timestamp else wallTime
        if (isReplayMode && wallTime - lastReplayGpsLogTime >= 1_000L) {
            lastReplayGpsLogTime = wallTime
            Log.d(
                TAG,
                "REPLY_GPS_SAMPLE lat=${gps.latLng.latitude}, lon=${gps.latLng.longitude} " +
                    "alt=${gps.altitude.value} gs=${gps.speed.value} track=${gps.bearing} ts=${gps.timestamp}"
            )
        }

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

        val gpsVarioValue = varioSuite.gpsVerticalSpeed().takeIf { it.isFinite() } ?: 0.0
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
        val varioResults = varioSuite.verticalSpeeds()

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
            val ageMs = wallTime - replayRealVarioTimestamp
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
            val varioMode = if (cachedVarioResult != null) "PRIORITY2-50Hz(IMU+BARO)" else "PRIORITY2-50Hz(BARO)"
            val calibrated = (cachedBaroResult?.isCalibrated == true)
            val qnh = cachedBaroResult?.qnh ?: Double.NaN
            val pressureVario = metrics.varioSource.takeIf { it == "PRESSURE" }?.let { metrics.verticalSpeed }
            val gpsVario = metrics.varioSource.takeIf { it == "GPS" }?.let { metrics.verticalSpeed }

            Log.d(
                TAG,
                "[SLOW] $varioMode " +
                    "GPSalt=${gps.altitude.value.toInt()}m BaroAlt=${baroAltitude.toInt()}m " +
                    "RawBaro=${String.format("%.2f", varioResult.verticalSpeed)} " +
                    "Levo=${String.format("%.2f", verticalSpeed)}(src=$varioSource,val=${metrics.varioValid}) " +
                    "XC=${String.format("%.2f", metrics.xcsoarVario)}(val=${metrics.xcsoarVarioValid}) " +
                    "GPSv=${gpsVario?.let { String.format("%.2f", it) } ?: "--"} " +
                    "PressV=${pressureVario?.let { String.format("%.2f", it) } ?: "--"} " +
                    "Spd=${String.format("%.1f", gps.speed.value)} " +
                    "AGL=${flightHelpers.currentAGL.toInt()} " +
                    "QNH=${String.format("%.1f", qnh)} cal=$calibrated autoQNH=$autoQnhEnabled"
            )
        }
    }
    /**
     * Stop audio engine (SRTM terrain database needs no cleanup)
     */
    override fun updateAudioSettings(settings: VarioAudioSettings) {
        audioController.engine.updateSettings(settings)
    }

    override fun setAutoQnhEnabled(enabled: Boolean) {
        autoQnhEnabled = enabled
        Log.i(TAG, "Auto QNH calibration enabled=$enabled")
    }

    override fun stop() {
        if (isReplayMode) {
            // Replay sessions frequently call stop() to reset smoothing/state; keep the audio engine
            // alive so replay can still emit tones after a seek/reload.
            audioController.engine.setSilence()
        } else {
            audioController.stop()
        }
        varioSuite.resetAll()
        filters.baroFilter.reset()
        filters.pressureKalmanFilter.reset()
        flightMetricsUseCase.reset()
        flightHelpers.resetAll()

        varioValidUntil = 0L

        replayRealVarioMs = null
        replayRealVarioTimestamp = 0L

        lastReplayBaroTimestamp = 0L
        lastReplayGpsLogTime = 0L

        lastUpdateTime = 0L
        lastVarioUpdateTime = 0L

        cachedGPSSpeed = 0.0
        cachedGPSAltitude = Double.NaN
        cachedGPSAccuracy = 15.0
        cachedIsGPSFixed = false
        cachedGPSLat = 0.0
        cachedGPSLon = 0.0
        cachedGPS = null

        cachedVarioResult = null
        cachedBaroResult = null
        cachedBaroData = null
        cachedCompassData = null
        latestTeVario = null

        smoothedVerticalAccel = null
        lastAccelTimestamp = 0L
        lastThermalLogTime = 0L

        _flightDataFlow.value = null
        _diagnosticsFlow.value = null

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
        Log.i(TAG, "QNH reset to standard atmosphere (auto calibration=$autoQnhEnabled)")
    }

    private fun updateAudioFeed(currentTime: Long, rawVario: Double) {
        audioController.update(latestTeVario, rawVario, currentTime, varioValidUntil)
    }
}
