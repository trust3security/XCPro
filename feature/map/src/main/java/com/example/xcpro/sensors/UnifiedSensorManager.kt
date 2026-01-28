package com.example.xcpro.sensors

import android.content.Context
import android.hardware.SensorManager
import android.location.LocationManager
import com.example.xcpro.core.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Public facade exposing raw sensor flows.
 * Delegates registration and updates to [SensorRegistry].
 */
class UnifiedSensorManager(
    private val context: Context,
    private val clock: Clock
) : SensorDataSource {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _gpsFlow = MutableStateFlow<GPSData?>(null)
    override val gpsFlow: StateFlow<GPSData?> = _gpsFlow.asStateFlow()

    private val _baroFlow = MutableStateFlow<BaroData?>(null)
    override val baroFlow: StateFlow<BaroData?> = _baroFlow.asStateFlow()

    private val _compassFlow = MutableStateFlow<CompassData?>(null)
    override val compassFlow: StateFlow<CompassData?> = _compassFlow.asStateFlow()

    private val _rawAccelFlow = MutableStateFlow<RawAccelData?>(null)
    override val rawAccelFlow: StateFlow<RawAccelData?> = _rawAccelFlow.asStateFlow()

    private val _accelFlow = MutableStateFlow<AccelData?>(null)
    override val accelFlow: StateFlow<AccelData?> = _accelFlow.asStateFlow()

    private val gpsStatusMonitor = GpsStatusMonitor(GpsStatus.Searching)
    val gpsStatusFlow: StateFlow<GpsStatus> = gpsStatusMonitor.status
    private var lastFixMonotonicMs: Long? = null
    private var lastFixWallMs: Long? = null
    private var lastAccuracy: Float? = null
    private var statusTickerJob: kotlinx.coroutines.Job? = null

    private val _attitudeFlow = MutableStateFlow<AttitudeData?>(null)
    override val attitudeFlow: StateFlow<AttitudeData?> = _attitudeFlow.asStateFlow()

    private val orientationProcessor = OrientationProcessor(clock)

    private val registry = SensorRegistry(
        context = context,
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager,
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager,
        orientationProcessor = orientationProcessor,
        clock = clock,
        updateGps = { gps ->
            _gpsFlow.value = gps
            lastFixMonotonicMs = gps.monotonicTimestampMillis.takeIf { it > 0L }
            lastFixWallMs = gps.timestamp.takeIf { it > 0L }
            lastAccuracy = gps.accuracy
            updateGpsStatus()
        },
        updateBaro = { _baroFlow.value = it },
        updateCompass = { _compassFlow.value = it },
        updateRawAccel = { _rawAccelFlow.value = it },
        updateAttitude = { _attitudeFlow.value = it },
        updateAccel = { _accelFlow.value = it }
    )

    /**
     * Start all sensors. Call after permissions are granted.
     */
    fun startAllSensors(): Boolean {
        val started = registry.startAll()
        updateGpsStatus()
        if (started && statusTickerJob == null) {
            statusTickerJob = scope.launch {
                while (true) {
                    updateGpsStatus()
                    delay(5_000)
                }
            }
        }
        return started
    }

    /**
     * Stop all sensors. Call when app backgrounds or shuts down.
     */
    fun stopAllSensors() {
        registry.stopAll()
        statusTickerJob?.cancel()
        statusTickerJob = null
    }

    fun setGpsUpdateIntervalMs(intervalMs: Long) {
        registry.setGpsUpdateIntervalMs(intervalMs)
    }

    fun isGpsEnabled(): Boolean = registry.isGpsEnabled()

    fun hasLocationPermissions(): Boolean = registry.hasLocationPermissions()

    fun getSensorStatus(): SensorStatus = registry.status()

    private fun updateGpsStatus() {
        scope.launch {
            val status = registry.status()
            val nowMono = clock.nowMonoMs()
            val nowWall = clock.nowWallMs()
            val age = when {
                lastFixMonotonicMs != null -> nowMono - lastFixMonotonicMs!!
                lastFixWallMs != null -> nowWall - lastFixWallMs!!
                else -> null
            }?.coerceAtLeast(0L)

            val derived = when {
                !status.hasLocationPermissions -> GpsStatus.NoPermission
                !status.gpsAvailable -> GpsStatus.Disabled
                !status.gpsStarted -> GpsStatus.Searching
                age == null -> GpsStatus.Searching
                age > 15_000 -> GpsStatus.LostFix(age)
                else -> GpsStatus.Ok(age, lastAccuracy)
            }
            gpsStatusMonitor.update(derived)
        }
    }
}
