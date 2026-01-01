package com.example.xcpro.sensors
import android.content.Context
import android.util.Log
import com.example.dfcards.calculations.BarometricAltitudeCalculator
import com.example.dfcards.dfcards.calculations.SimpleAglCalculator
import com.example.xcpro.audio.VarioAudioController
import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.flightdata.FlightDisplayMapper
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.FlightDataConstants
import com.example.xcpro.sensors.FlightFilters
import com.example.xcpro.sensors.VarioDiagnosticsSample
import com.example.xcpro.sensors.domain.CalculateFlightMetricsUseCase
import com.example.xcpro.sensors.domain.WindEstimator
import com.example.xcpro.weather.wind.data.WindState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.util.Locale

/**
 * Combines sensor streams into [CompleteFlightData] (SSOT).
 *
 * Sensor access lives in [SensorDataSource]; this class only fuses/filters and publishes.
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
        private const val MAX_LOCATION_HISTORY = FlightDataConstants.MAX_LOCATION_HISTORY
        private const val MAX_VSPEED_HISTORY = FlightDataConstants.MAX_VSPEED_HISTORY
        private const val LD_CALCULATION_INTERVAL = FlightDataConstants.LD_CALCULATION_INTERVAL
        private const val QNH_JUMP_THRESHOLD_HPA = FlightDataConstants.QNH_JUMP_THRESHOLD_HPA
        private const val QNH_CALIBRATION_ACCURACY_THRESHOLD = FlightDataConstants.QNH_CALIBRATION_ACCURACY_THRESHOLD
        private const val AUTO_QNH_MAX_SPEED_MS = 5.0
        private const val AUTO_QNH_SESSION_TIMEOUT_MS = 90_000L
        private const val VARIO_VALIDITY_MS = FlightDataConstants.VARIO_VALIDITY_MS
        private const val VARIO_VALIDITY_FLOOR_MS = 5_000L
        private const val EMIT_MIN_INTERVAL_MS = 100L
        private const val BARO_EMIT_STALE_MS = 500L
        private const val ACCEL_FRESHNESS_MS = 250L
        private const val ACCEL_SMOOTH_TAU_S = 0.15
        private const val MAX_VERTICAL_ACCEL_MS2 = 8.0
    }
    private val locationHistory = mutableListOf<LocationWithTime>()
    private val aglCalculator = SimpleAglCalculator(context)  // KISS: SRTM terrain database
    private val baroCalculator = BarometricAltitudeCalculator(aglCalculator)  // 🚀 SRTM-based QNH calibration
    private val filters = FlightFilters()
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
    private var lastGpsFixTimestampForGpsVario: Long = 0L
    private val varioSuite = VarioSuite()
    private val audioController = VarioAudioController(context, scope, enableAudio)
    val audioEngine get() = audioController.engine
    private val flightDisplayMapper = FlightDisplayMapper()
    private val _flightDataFlow = MutableStateFlow<CompleteFlightData?>(null)
    override val flightDataFlow: StateFlow<CompleteFlightData?> = _flightDataFlow.asStateFlow()
    private val _diagnosticsFlow = MutableStateFlow<VarioDiagnosticsSample?>(null)
    override val diagnosticsFlow: StateFlow<VarioDiagnosticsSample?> = _diagnosticsFlow.asStateFlow()
    override val audioSettings: StateFlow<VarioAudioSettings> = audioController.engine.settings
    private val emissionState = FlightDataEmissionState()
    private val emitter = FlightDataEmitter(
        state = emissionState,
        flightMetricsUseCase = flightMetricsUseCase,
        flightDisplayMapper = flightDisplayMapper,
        flightHelpers = flightHelpers,
        varioSuite = varioSuite,
        isReplayMode = isReplayMode,
        flightDataFlow = _flightDataFlow,
        logThermalMetrics = LOG_THERMAL_METRICS,
        tag = TAG
    )
    @Volatile private var autoQnhSessionActive = false
    @Volatile private var autoQnhSessionDeadlineMs = 0L
    @Volatile private var replayRealVarioMs: Double? = null
    @Volatile private var replayRealVarioTimestamp: Long = 0L
    private var lastReplayBaroTimestamp: Long = 0L
    private var lastReplayBaroLogTime: Long = 0L
    private var lastReplayGpsLogTime: Long = 0L

    // Tracking for delta-time calculations
    private var lastVarioUpdateTime = 0L

    // Cached GPS data for the high-speed vario loop (GPS updates slower than baro/IMU).
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

     // IMU vertical acceleration smoothing for 3‑state Kalman / complementary fusion.
     private var lastAccelTimestamp: Long = 0L
     private var smoothedVerticalAccel: Double? = null
    private var macCreadySetting = DEFAULT_MACCREADY
    private var macCreadyRisk = DEFAULT_MACCREADY

    init {
        scope.launch { windStateFlow.collect { latestWindState = it } }

        // Decoupled sample rates: high-speed baro+IMU loop and slower GPS loop.
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

    private fun updateVarioFilter(baro: BaroData?, accel: AccelData?) {
        if (baro == null) {
            Log.d(TAG, "No barometer data - skipping vario update")
            return
        }

        val wallTime = System.currentTimeMillis()

        // In replay mode, downstream estimators (wind, circling, etc.) use the sensor timestamps as
        // the "simulation clock". Keep the vario validity clock in the same time base.
        val currentTime = if (isReplayMode) baro.timestamp else System.currentTimeMillis()

        if (!isReplayMode && autoQnhSessionActive && wallTime > autoQnhSessionDeadlineMs) {
            autoQnhSessionActive = false
            Log.w(TAG, "Auto QNH calibration timed out; ignoring further samples until requested again")
        }

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
        val canAutoCalibrateNow = !isReplayMode &&
            autoQnhSessionActive &&
            cachedGPSSpeed.isFinite() &&
            cachedGPSSpeed <= AUTO_QNH_MAX_SPEED_MS

        val hasCalibrationFix = canAutoCalibrateNow &&
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

        if (!isReplayMode && autoQnhSessionActive && baroCalculator.isCalibrationFinished()) {
            autoQnhSessionActive = false
            val qnhLabel = String.format(Locale.US, "%.1f", baroResult.qnh)
            Log.i(TAG, "Auto QNH calibration completed (QNH=$qnhLabel)")
        }

        if (previousBaroResult != null) {
            val qnhDelta = abs(baroResult.qnh - previousBaroResult.qnh)
            val altitudeDelta = abs(baroResult.altitudeMeters - previousBaroResult.altitudeMeters)
            val qnhJumpDetected = qnhDelta > QNH_JUMP_THRESHOLD_HPA
            if (qnhJumpDetected) {
                val qnhLabel = String.format(Locale.US, "%.2f", qnhDelta)
                val altitudeLabel = String.format(Locale.US, "%.1f", altitudeDelta)
                if (isReplayMode) {
                    Log.w(
                        TAG,
                        "Replay QNH jump detected Δ${qnhLabel} hPa / Δ${altitudeLabel} m - ignoring reset to keep vario stable"
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
                    emissionState.varioValidUntil = 0L
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

        val replayWindowMs = if (isReplayMode) {
            replayDeltaTime
                ?.times(1000.0)
                ?.toLong()
                ?: 1_000L
        } else {
            0L
        }
        val validityWindowMs = maxOf(VARIO_VALIDITY_MS, replayWindowMs, VARIO_VALIDITY_FLOOR_MS)
        emissionState.varioValidUntil = currentTime + validityWindowMs
        updateAudioFeed(currentTime, varioResult.verticalSpeed)

        cachedVarioResult = varioResult
        cachedBaroResult = baroResult
        cachedBaroData = baro

        val shouldEmit = cachedGPS != null &&
            (currentTime - emissionState.lastUpdateTime) >= EMIT_MIN_INTERVAL_MS
        if (shouldEmit) {
            // Emit display frames on the baro loop (throttled) so UI/audio follow fast vario cadence.
            val emitDeltaTime = if (emissionState.lastUpdateTime > 0L) {
                (currentTime - emissionState.lastUpdateTime) / 1000.0
            } else {
                deltaTime
            }
            cachedGPS?.let { gps ->
                emitter.emit(
                    gps = gps,
                    compass = cachedCompassData,
                    currentTime = currentTime,
                    deltaTime = emitDeltaTime,
                    varioResultInput = varioResult,
                    baroResult = cachedBaroResult,
                    baro = cachedBaroData,
                    cachedVarioResult = cachedVarioResult,
                    windState = latestWindState,
                    replayRealVarioMs = replayRealVarioMs,
                    replayRealVarioTimestamp = replayRealVarioTimestamp,
                    macCreadySetting = macCreadySetting,
                    macCreadyRisk = macCreadyRisk,
                    autoQnhSessionActive = autoQnhSessionActive
                )
            }
        }

        lastVarioUpdateTime = currentTime

        if (isReplayMode && wallTime - lastReplayBaroLogTime >= 1_000L) {
            lastReplayBaroLogTime = wallTime
            Log.d(
                TAG,
                "REPLAY_BARO ts=${baro.timestamp} p=${"%.2f".format(baro.pressureHPa.value)} " +
                    "pSmooth=${"%.2f".format(smoothedPressure)} alt=${"%.1f".format(baroResult.altitudeMeters)} " +
                    "dispAlt=${"%.1f".format(filteredBaro.displayAltitude)} vs=${"%.3f".format(varioResult.verticalSpeed)} " +
                    "dt=${"%.3f".format(deltaTime)} gpsAlt=${"%.1f".format(cachedGPSAltitude)} " +
                    "gs=${"%.2f".format(cachedGPSSpeed)} validUntil=${emissionState.varioValidUntil}"
            )
        }

    }
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

        if (gps.timestamp != lastGpsFixTimestampForGpsVario && gps.altitude.value.isFinite()) {
            varioSuite.updateGpsVario(gpsAltitudeMeters = gps.altitude.value, gpsTimestampMillis = gps.timestamp)
            lastGpsFixTimestampForGpsVario = gps.timestamp
        }

        val deltaTime = if (emissionState.lastUpdateTime > 0) {
            (currentTime - emissionState.lastUpdateTime) / 1000.0
        } else {
            0.1 // 10Hz = 100ms = 0.1s default
        }

        val baroAgeMs = currentTime - lastVarioUpdateTime
        val baroFresh = cachedBaroData != null && baroAgeMs in 0..BARO_EMIT_STALE_MS

        // Emit display/UI data on GPS tick only when baro is stale or unavailable.
        if (!baroFresh) {
            emitter.emit(
                gps = gps,
                compass = compass,
                currentTime = currentTime,
                deltaTime = deltaTime,
                varioResultInput = cachedVarioResult ?: com.example.dfcards.filters.ModernVarioResult(
                    altitude = gps.altitude.value,
                    verticalSpeed = 0.0,
                    acceleration = 0.0,
                    confidence = 0.3
                ),
                baroResult = cachedBaroResult,
                baro = cachedBaroData,
                cachedVarioResult = cachedVarioResult,
                windState = latestWindState,
                replayRealVarioMs = replayRealVarioMs,
                replayRealVarioTimestamp = replayRealVarioTimestamp,
                macCreadySetting = macCreadySetting,
                macCreadyRisk = macCreadyRisk,
                autoQnhSessionActive = autoQnhSessionActive
            )
        }
    }
    override fun updateAudioSettings(settings: VarioAudioSettings) {
        audioController.engine.updateSettings(settings)
    }
    override fun requestAutoQnhCalibration() {
        if (isReplayMode) {
            Log.i(TAG, "Auto QNH calibration ignored in replay mode")
            return
        }
        baroCalculator.beginAutoCalibration()
        autoQnhSessionActive = true
        autoQnhSessionDeadlineMs = System.currentTimeMillis() + AUTO_QNH_SESSION_TIMEOUT_MS
        Log.i(TAG, "Auto QNH calibration requested")
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
        emissionState.reset()
        replayRealVarioMs = null
        replayRealVarioTimestamp = 0L
        lastReplayBaroTimestamp = 0L
        lastReplayBaroLogTime = 0L
        lastReplayGpsLogTime = 0L
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
        lastGpsFixTimestampForGpsVario = 0L
        smoothedVerticalAccel = null
        lastAccelTimestamp = 0L
        autoQnhSessionActive = false
        autoQnhSessionDeadlineMs = 0L
        _flightDataFlow.value = null
        _diagnosticsFlow.value = null
        Log.d(TAG, "FlightDataCalculator stopped")
    }
    override fun setManualQnh(qnhHPa: Double) {
        baroCalculator.setQNH(qnhHPa)
        autoQnhSessionActive = false
        autoQnhSessionDeadlineMs = 0L
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
    override fun updateReplayRealVario(realVarioMs: Double?, timestampMillis: Long) {
        if (!isReplayMode) return
        replayRealVarioMs = realVarioMs
        replayRealVarioTimestamp = timestampMillis
    }
    override fun resetQnhToStandard() {
        baroCalculator.resetToStandardAtmosphere()
        autoQnhSessionActive = false
        autoQnhSessionDeadlineMs = 0L
        cachedBaroResult = null
        cachedVarioResult = null
        Log.i(TAG, "QNH reset to standard atmosphere")
    }
    private fun updateAudioFeed(currentTime: Long, rawVario: Double) {
        audioController.update(emissionState.latestTeVario, rawVario, currentTime, emissionState.varioValidUntil)
    }
}
