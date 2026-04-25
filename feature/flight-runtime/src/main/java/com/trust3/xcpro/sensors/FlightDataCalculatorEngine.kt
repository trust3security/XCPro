package com.trust3.xcpro.sensors
import com.trust3.xcpro.core.flight.calculations.BarometricAltitudeCalculator
import com.trust3.xcpro.core.flight.calculations.SimpleAglCalculator
import com.trust3.xcpro.core.flight.calculations.TerrainElevationReadPort
import com.trust3.xcpro.audio.VarioAudioControllerPort
import com.trust3.xcpro.audio.VarioAudioSettings
import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.external.ExternalFlightSettingsReadPort
import com.trust3.xcpro.external.ExternalFlightSettingsSnapshot
import com.trust3.xcpro.external.ExternalInstrumentFlightSnapshot
import com.trust3.xcpro.external.ExternalInstrumentReadPort
import com.trust3.xcpro.flightdata.FlightDisplayMapper
import com.trust3.xcpro.glider.StillAirSinkProvider
import com.trust3.xcpro.hawk.HawkAudioVarioReadPort
import com.trust3.xcpro.sensors.FlightDataConstants
import com.trust3.xcpro.sensors.FlightFilters
import com.trust3.xcpro.sensors.VarioDiagnosticsSample
import com.trust3.xcpro.sensors.domain.CalculateFlightMetricsUseCase
import com.trust3.xcpro.sensors.domain.FlyingState
import com.trust3.xcpro.sensors.domain.WindEstimator
import com.trust3.xcpro.weather.wind.data.AirspeedDataSource
import com.trust3.xcpro.weather.wind.model.AirspeedSample
import com.trust3.xcpro.weather.wind.model.WindState
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
    private val sensorDataSource: SensorDataSource,
    private val airspeedDataSource: AirspeedDataSource,
    private val scope: CoroutineScope,
    private val sinkProvider: StillAirSinkProvider,
    private val windStateFlow: StateFlow<WindState>,
    private val flightStateSource: FlightStateSource,
    internal val audioController: VarioAudioControllerPort,
    internal val clock: Clock,
    private val hawkAudioVarioReadPort: HawkAudioVarioReadPort,
    private val externalInstrumentReadPort: ExternalInstrumentReadPort,
    private val externalFlightSettingsReadPort: ExternalFlightSettingsReadPort,
    private val terrainElevationReadPort: TerrainElevationReadPort,
    internal val isReplayMode: Boolean = false
): SensorFusionRepository {

    companion object {
        internal const val TAG = FlightDataConstants.TAG
        internal const val LOG_THERMAL_METRICS = FlightDataConstants.LOG_THERMAL_METRICS
        internal const val DEFAULT_MACCREADY = FlightDataConstants.DEFAULT_MACCREADY
        internal const val QNH_JUMP_THRESHOLD_HPA = FlightDataConstants.QNH_JUMP_THRESHOLD_HPA
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
    internal val aglCalculator = SimpleAglCalculator(
        terrainElevationReadPort = terrainElevationReadPort
    )
    internal val baroCalculator = BarometricAltitudeCalculator()
    internal val filters = FlightFilters()
    internal val flightHelpers = FlightCalculationHelpers(
        scope = scope,
        aglCalculator = aglCalculator,
        locationHistory = locationHistory,
        sinkProvider = sinkProvider,
        nowMonoMsProvider = { clock.nowMonoMs() }
    )
    internal val flightMetricsUseCase = CalculateFlightMetricsUseCase(
        flightHelpers = flightHelpers,
        sinkProvider = sinkProvider,
        windEstimator = WindEstimator()
    )
    @Volatile internal var latestWindState: WindState? = null
    @Volatile internal var latestFlightState: FlyingState? = null
    @Volatile internal var latestAirspeedSample: AirspeedSample? = null
    @Volatile internal var latestExternalInstrumentSnapshot: ExternalInstrumentFlightSnapshot =
        ExternalInstrumentFlightSnapshot()
    @Volatile internal var latestExternalFlightSettingsSnapshot: ExternalFlightSettingsSnapshot =
        ExternalFlightSettingsSnapshot()
    @Volatile internal var lastGpsFixTimestampForGpsVario: Long = 0L
    internal val varioSuite = VarioSuite()
    internal val flightDisplayMapper = FlightDisplayMapper()
    internal val _flightDataFlow = MutableStateFlow<CompleteFlightData?>(null)
    override val flightDataFlow: StateFlow<CompleteFlightData?> = _flightDataFlow.asStateFlow()
    internal val _diagnosticsFlow = MutableStateFlow<VarioDiagnosticsSample?>(null)
    override val diagnosticsFlow: StateFlow<VarioDiagnosticsSample?> = _diagnosticsFlow.asStateFlow()
    override val audioSettings: StateFlow<VarioAudioSettings> = audioController.settings
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
    @Volatile internal var replayRealVarioMs: Double? = null
    @Volatile internal var replayRealVarioTimestamp: Long = 0L
    internal var lastReplayBaroTimestamp: Long = 0L
    internal var lastReplayBaroLogTime: Long = 0L
    internal var lastReplayGpsLogTime: Long = 0L
    @Volatile internal var hawkAudioEnabled: Boolean = false
    @Volatile internal var hawkAudioVarioMps: Double? = null
    // Tracking for delta-time calculations
    @Volatile internal var lastVarioUpdateTime = 0L
    @Volatile internal var lastBaroSampleTime = 0L
    // Cached GPS data for the high-speed vario loop (GPS updates slower than baro/IMU).
    @Volatile internal var cachedGPSSpeed = 0.0
    // Use NaN as a sentinel until we have a real GPS altitude (prevents false calibration)
    @Volatile internal var cachedGPSAltitude = Double.NaN
    @Volatile internal var cachedGPSAccuracy = 15.0
    @Volatile internal var cachedIsGPSFixed = false
    @Volatile internal var cachedGPSLat = 0.0  // Reserved for terrain-aware metrics
    @Volatile internal var cachedGPSLon = 0.0  // Reserved for terrain-aware metrics
    @Volatile internal var cachedGPS: GPSData? = null  // Full GPS data for calculations
    // Cached results from vario loop for GPS loop to use
    @Volatile internal var cachedVarioResult: com.trust3.xcpro.core.flight.filters.ModernVarioResult? = null
    @Volatile internal var cachedBaroResult: com.trust3.xcpro.core.flight.calculations.BarometricAltitudeData? = null
    @Volatile internal var cachedBaroData: BaroData? = null
    @Volatile internal var cachedCompassData: CompassData? = null
    @Volatile internal var lastDiagnosticsEmitTime: Long = 0L
     // IMU vertical acceleration smoothing for 3-state Kalman / complementary fusion.
    @Volatile internal var lastAccelTimestamp: Long = 0L
    @Volatile internal var smoothedVerticalAccel: Double? = null
    @Volatile internal var macCreadySetting = DEFAULT_MACCREADY
    @Volatile internal var macCreadyRisk = DEFAULT_MACCREADY
    @Volatile internal var autoMcEnabled: Boolean = true
    @Volatile internal var totalEnergyCompensationEnabled: Boolean = true
    @Volatile internal var flightMode: FlightMode = FlightMode.CRUISE
    init {
        scope.launch { windStateFlow.collect { latestWindState = it } }
        scope.launch { flightStateSource.flightState.collect { latestFlightState = it } }
        scope.launch { airspeedDataSource.airspeedFlow.collect { latestAirspeedSample = it } }
        scope.launch {
            externalInstrumentReadPort.externalFlightSnapshot.collect { snapshot ->
                latestExternalInstrumentSnapshot = snapshot
            }
        }
        scope.launch {
            externalFlightSettingsReadPort.externalFlightSettingsSnapshot.collect { snapshot ->
                latestExternalFlightSettingsSnapshot = snapshot
            }
        }
        scope.launch {
            hawkAudioVarioReadPort.audioVarioMps.collect { sample ->
                hawkAudioVarioMps = sample?.takeIf { it.isFinite() }
            }
        }

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
                    AppLogger.e(TAG, "Vario loop error", t)
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
                    AppLogger.e(TAG, "GPS loop error", t)
                }
            }
        }

        AppLogger.d(TAG, "FlightDataCalculator initialized with PRIORITY 2: Decoupled sample rates (50Hz vario + 10Hz GPS)")
    }

    override fun updateAudioSettings(settings: VarioAudioSettings) {
        audioController.updateSettings(settings)
    }
    override fun setHawkAudioEnabled(enabled: Boolean) {
        hawkAudioEnabled = enabled
    }
    override fun stop() {
        if (isReplayMode) {
            // Replay sessions frequently call stop() to reset smoothing/state; keep the audio engine
            // alive so replay can still emit tones after a seek/reload.
            audioController.silence()
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
        lastBaroSampleTime = 0L
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
        latestWindState = null
        latestFlightState = null
        latestAirspeedSample = null
        latestExternalInstrumentSnapshot = ExternalInstrumentFlightSnapshot()
        latestExternalFlightSettingsSnapshot = ExternalFlightSettingsSnapshot()
        lastGpsFixTimestampForGpsVario = 0L
        smoothedVerticalAccel = null
        lastAccelTimestamp = 0L
        _flightDataFlow.value = null
        _diagnosticsFlow.value = null
        AppLogger.d(TAG, "FlightDataCalculator stopped")
    }
    override fun setManualQnh(qnhHPa: Double) {
        baroCalculator.setQNH(qnhHPa, clock.nowWallMs())
        cachedBaroResult = null
        cachedVarioResult = null
        AppLogger.i(TAG, "Manual QNH applied: ${qnhHPa}")
    }
    override fun setMacCreadySetting(value: Double) {
        macCreadySetting = value
    }
    override fun setMacCreadyRisk(value: Double) {
        macCreadyRisk = value
    }
    override fun setAutoMcEnabled(enabled: Boolean) {
        autoMcEnabled = enabled
    }
    override fun setTotalEnergyCompensationEnabled(enabled: Boolean) {
        totalEnergyCompensationEnabled = enabled
        if (!enabled) {
            emissionState.latestTeVario = null
        }
    }
    override fun setFlightMode(mode: FlightMode) {
        flightMode = mode
    }
    override fun updateReplayRealVario(realVarioMs: Double?, timestampMillis: Long) {
        if (!isReplayMode) return
        replayRealVarioMs = realVarioMs
        replayRealVarioTimestamp = timestampMillis
    }
    override fun resetQnhToStandard() {
        baroCalculator.resetToStandardAtmosphere()
        cachedBaroResult = null
        cachedVarioResult = null
        AppLogger.i(TAG, "QNH reset to standard atmosphere")
    }
}
