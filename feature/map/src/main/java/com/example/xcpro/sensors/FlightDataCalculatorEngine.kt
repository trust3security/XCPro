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
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.sensors.domain.WindEstimator
import com.example.xcpro.weather.wind.model.WindState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Combines sensor streams into [CompleteFlightData] (SSOT).
 *
 * Extracted from FlightDataCalculator.kt to maintain the 500-line file limit.
 *
 * Sensor access lives in [SensorDataSource]; this class only fuses/filters and publishes.
 */
internal class FlightDataCalculatorEngine(
    private val context: Context,
    private val sensorDataSource: SensorDataSource,
    private val scope: CoroutineScope,
    private val sinkProvider: StillAirSinkProvider,
    private val windStateFlow: StateFlow<WindState>,
    private val flightStateSource: FlightStateSource,
    internal val enableAudio: Boolean = true,
    internal val isReplayMode: Boolean = false
): SensorFusionRepository {

    companion object {
        internal const val TAG = FlightDataConstants.TAG
        internal const val LOG_THERMAL_METRICS = FlightDataConstants.LOG_THERMAL_METRICS
        internal const val DEFAULT_MACCREADY = FlightDataConstants.DEFAULT_MACCREADY
        internal const val QNH_JUMP_THRESHOLD_HPA = FlightDataConstants.QNH_JUMP_THRESHOLD_HPA
        internal const val QNH_CALIBRATION_ACCURACY_THRESHOLD = FlightDataConstants.QNH_CALIBRATION_ACCURACY_THRESHOLD
        internal const val AUTO_QNH_MAX_SPEED_MS = 5.0
        internal const val AUTO_QNH_SESSION_TIMEOUT_MS = 90_000L
        internal const val VARIO_VALIDITY_MS = FlightDataConstants.VARIO_VALIDITY_MS
        internal const val VARIO_VALIDITY_FLOOR_MS = 5_000L
        internal const val EMIT_MIN_INTERVAL_MS = 100L
        internal const val BARO_EMIT_STALE_MS = 500L
        internal const val ACCEL_FRESHNESS_MS = 250L
        internal const val ACCEL_SMOOTH_TAU_S = 0.15
        internal const val MAX_VERTICAL_ACCEL_MS2 = 8.0
        internal const val DIAGNOSTICS_EMIT_MIN_INTERVAL_MS = 100L
    }
    internal val locationHistory = mutableListOf<LocationWithTime>()
    internal val aglCalculator = SimpleAglCalculator(context)  // KISS: SRTM terrain database
    internal val baroCalculator = BarometricAltitudeCalculator(aglCalculator)  // ???? SRTM-based QNH calibration
    internal val filters = FlightFilters()
    internal val flightHelpers = FlightCalculationHelpers(
        scope = scope,
        aglCalculator = aglCalculator,
        locationHistory = locationHistory,
        sinkProvider = sinkProvider
    )
    internal val flightMetricsUseCase = CalculateFlightMetricsUseCase(
        flightHelpers = flightHelpers,
        sinkProvider = sinkProvider,
        windEstimator = WindEstimator(sinkProvider)
    )
    internal var latestWindState: WindState? = null
    internal var latestFlightState: FlyingState? = null
    internal var lastGpsFixTimestampForGpsVario: Long = 0L
    internal val varioSuite = VarioSuite()
    internal val audioController = VarioAudioController(context, scope, enableAudio)
    val audioEngine get() = audioController.engine
    internal val flightDisplayMapper = FlightDisplayMapper()
    internal val _flightDataFlow = MutableStateFlow<CompleteFlightData?>(null)
    override val flightDataFlow: StateFlow<CompleteFlightData?> = _flightDataFlow.asStateFlow()
    internal val _diagnosticsFlow = MutableStateFlow<VarioDiagnosticsSample?>(null)
    override val diagnosticsFlow: StateFlow<VarioDiagnosticsSample?> = _diagnosticsFlow.asStateFlow()
    override val audioSettings: StateFlow<VarioAudioSettings> = audioController.engine.settings
    internal val emissionState = FlightDataEmissionState()
    internal val emitter = FlightDataEmitter(
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
    @Volatile internal var autoQnhSessionActive = false
    @Volatile internal var autoQnhSessionDeadlineMs = 0L
    @Volatile internal var replayRealVarioMs: Double? = null
    @Volatile internal var replayRealVarioTimestamp: Long = 0L
    internal var lastReplayBaroTimestamp: Long = 0L
    internal var lastReplayBaroLogTime: Long = 0L
    internal var lastReplayGpsLogTime: Long = 0L
    // Tracking for delta-time calculations
    internal var lastVarioUpdateTime = 0L
    // Cached GPS data for the high-speed vario loop (GPS updates slower than baro/IMU).
    internal var cachedGPSSpeed = 0.0
    // Use NaN as a sentinel until we have a real GPS altitude (prevents false calibration)
    internal var cachedGPSAltitude = Double.NaN
    internal var cachedGPSAccuracy = 15.0
    internal var cachedIsGPSFixed = false
    internal var cachedGPSLat = 0.0  // ???? For SRTM-based QNH calibration
    internal var cachedGPSLon = 0.0  // ???? For SRTM-based QNH calibration
    internal var cachedGPS: GPSData? = null  // Full GPS data for calculations
    // Cached results from vario loop for GPS loop to use
    internal var cachedVarioResult: com.example.dfcards.filters.ModernVarioResult? = null
     internal var cachedBaroResult: com.example.dfcards.calculations.BarometricAltitudeData? = null
    internal var cachedBaroData: BaroData? = null
    internal var cachedCompassData: CompassData? = null
    internal var lastDiagnosticsEmitTime: Long = 0L
     // IMU vertical acceleration smoothing for 3???state Kalman / complementary fusion.
     internal var lastAccelTimestamp: Long = 0L
     internal var smoothedVerticalAccel: Double? = null
    internal var macCreadySetting = DEFAULT_MACCREADY
    internal var macCreadyRisk = DEFAULT_MACCREADY
    init {
        scope.launch { windStateFlow.collect { latestWindState = it } }
        scope.launch { flightStateSource.flightState.collect { latestFlightState = it } }

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
        lastDiagnosticsEmitTime = 0L
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
}
