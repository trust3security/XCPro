package com.example.xcpro

import android.hardware.SensorManager
import android.util.Log
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.orientation.OrientationSensorData
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.orientation.HeadingResolver
import com.example.xcpro.orientation.HeadingResolverInput
import com.example.xcpro.orientation.OrientationClock
import com.example.xcpro.sensors.AttitudeData
import com.example.xcpro.sensors.CompassData
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.UnifiedSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OrientationDataSource(
    private val unifiedSensorManager: UnifiedSensorManager,
    private val scope: CoroutineScope,
    private val headingResolver: HeadingResolver,
    private val flightStateSource: FlightStateSource? = null,
    private val clock: OrientationClock,
    private val featureFlags: MapFeatureFlags
) : OrientationSensorSource {

    private val _orientationFlow = MutableStateFlow(OrientationSensorData())
    override val orientationFlow: StateFlow<OrientationSensorData> = _orientationFlow.asStateFlow()

    private var currentFlightData = RealTimeFlightData()

    private var sensorJob: Job? = null
    private var isStarted = false
    private var minSpeedThresholdMs: Double = 2.0

    private enum class HeadingSource { NONE, ATTITUDE, COMPASS }

    private var filteredCompassHeading = 0.0
    private var hasCompassHeading = false
    private var lastCompassUpdateTime = 0L
    private var lastCompassReliableTime = 0L
    private var compassReliableSince = 0L

    private var filteredAttitudeHeading = 0.0
    private var hasAttitudeHeading = false
    private var lastAttitudeUpdateTime = 0L
    private var lastAttitudeReliableTime = 0L
    private var attitudeReliableSince = 0L

    private var activeHeadingSource = HeadingSource.NONE
    private var lastSourceSwitchTime = 0L

    private var latestCompass: CompassData? = null
    private var latestAttitude: AttitudeData? = null
    private var lastCompassHeading = Double.NaN
    private var lastCompassTime = 0L
    private var lastCompassReliable = false
    private var lastAttitudeHeading = Double.NaN
    private var lastAttitudeTime = 0L
    private var lastAttitudeReliable = false
    private var lastHeadingInputSource = "NONE"
    private var isFlying = false
    private var attitudeSensorSeen = false

    companion object {
        private const val TAG = "OrientationDataSource"
        private const val HEADING_UPDATE_INTERVAL_MS = 50L     // 20Hz
        private const val HEADING_STALE_THRESHOLD_MS = 1_500L  // 1.5 seconds
        private const val SOURCE_SWITCH_STABLE_MS = 500L
        private const val SMOOTHING_FACTOR = 0.3
    }

    init {
        Log.d(TAG, " OrientationDataSource initializing with UnifiedSensorManager")
        Log.d(TAG, " Sensor availability: ${getSensorInfo()}")
    }

    override fun updateFromFlightData(flightData: RealTimeFlightData) {
        currentFlightData = flightData
        updateOrientationData()
    }

    override fun updateMinSpeedThreshold(thresholdMs: Double) {
        minSpeedThresholdMs = thresholdMs
    }

    override fun start() {
        if (isStarted) {
            Log.d(TAG, " OrientationDataSource already started")
            return
        }
        isStarted = true
        Log.d(TAG, " Starting OrientationDataSource (Unified)")

        sensorJob = scope.launch {
            launch {
                unifiedSensorManager.compassFlow.collect { compass ->
                    compass?.let { handleCompassUpdate(it) }
                }
            }
            launch {
                unifiedSensorManager.attitudeFlow.collect { attitude ->
                    attitude?.let { handleAttitudeUpdate(it) }
                }
            }
            launch {
                flightStateSource?.flightState?.collect { state ->
                    isFlying = state.isFlying
                }
            }
        }
    }

    override fun stop() {
        if (!isStarted) {
            Log.d(TAG, " OrientationDataSource already stopped")
            return
        }
        isStarted = false
        Log.d(TAG, " Stopping OrientationDataSource (Unified)")
        sensorJob?.cancel()
        sensorJob = null
        resetHeadingState()
        Log.d(TAG, " OrientationDataSource stopped")
    }

    private fun handleCompassUpdate(compass: CompassData) {
        latestCompass = compass
        val reliable = compass.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
        lastCompassHeading = compass.heading
        lastCompassTime = if (compass.monotonicTimestampMillis > 0L) {
            compass.monotonicTimestampMillis
        } else {
            clock.nowMonoMs()
        }
        lastCompassReliable = reliable
        lastHeadingInputSource = "COMPASS"
        updateCompassHeading(compass.heading, reliable)
    }

    private fun handleAttitudeUpdate(attitude: AttitudeData) {
        latestAttitude = attitude
        lastAttitudeHeading = attitude.headingDeg
        lastAttitudeTime = if (attitude.monotonicTimestampMillis > 0L) {
            attitude.monotonicTimestampMillis
        } else {
            clock.nowMonoMs()
        }
        lastAttitudeReliable = attitude.isReliable
        lastHeadingInputSource = "ATTITUDE"
        attitudeSensorSeen = true
        updateAttitudeHeading(attitude.headingDeg, attitude.isReliable)
    }

    private fun updateCompassHeading(newHeading: Double, reliable: Boolean) {
        val now = clock.nowMonoMs()

        if (hasCompassHeading && now - lastCompassUpdateTime < HEADING_UPDATE_INTERVAL_MS) {
            updateCompassReliability(now, reliable)
            return
        }

        filteredCompassHeading = if (!hasCompassHeading) {
            newHeading
        } else {
            smoothBearingTransition(filteredCompassHeading, newHeading)
        }

        hasCompassHeading = true
        lastCompassUpdateTime = now
        updateCompassReliability(now, reliable)

        updateOrientationData()
    }

    private fun updateAttitudeHeading(newHeading: Double, reliable: Boolean) {
        val now = clock.nowMonoMs()

        if (hasAttitudeHeading && now - lastAttitudeUpdateTime < HEADING_UPDATE_INTERVAL_MS) {
            updateAttitudeReliability(now, reliable)
            return
        }

        filteredAttitudeHeading = if (!hasAttitudeHeading) {
            newHeading
        } else {
            smoothBearingTransition(filteredAttitudeHeading, newHeading)
        }

        hasAttitudeHeading = true
        lastAttitudeUpdateTime = now
        updateAttitudeReliability(now, reliable)

        updateOrientationData()
    }

    private fun updateOrientationData() {
        val nowMono = clock.nowMonoMs()
        val nowWall = clock.nowWallMs()
        val allowDeviceHeading = featureFlags.allowHeadingWhileStationary ||
            isFlying ||
            currentFlightData.groundSpeed >= minSpeedThresholdMs
        val attitudeFresh = allowDeviceHeading &&
            hasAttitudeHeading &&
            (nowMono - lastAttitudeReliableTime) <= HEADING_STALE_THRESHOLD_MS
        val allowCompassFallback = !attitudeSensorSeen || !attitudeFresh
        val compassFresh = allowDeviceHeading &&
            allowCompassFallback &&
            hasCompassHeading &&
            (nowMono - lastCompassReliableTime) <= HEADING_STALE_THRESHOLD_MS

        val compassStable = compassFresh && (nowMono - compassReliableSince) >= SOURCE_SWITCH_STABLE_MS
        val attitudeStable = attitudeFresh && (nowMono - attitudeReliableSince) >= SOURCE_SWITCH_STABLE_MS

        updateActiveHeadingSource(
            now = nowMono,
            compassFresh = compassFresh,
            attitudeFresh = attitudeFresh,
            compassStable = compassStable,
            attitudeStable = attitudeStable
        )

        val primaryHeading = when (activeHeadingSource) {
            HeadingSource.ATTITUDE -> filteredAttitudeHeading
            HeadingSource.COMPASS -> filteredCompassHeading
            HeadingSource.NONE -> null
        }
        val primaryReliable = when (activeHeadingSource) {
            HeadingSource.ATTITUDE -> attitudeFresh
            HeadingSource.COMPASS -> compassFresh
            HeadingSource.NONE -> false
        }

        val headingSolution = headingResolver.resolve(
            HeadingResolverInput(
                primaryHeadingDeg = primaryHeading,
                primaryHeadingReliable = primaryReliable,
                gpsTrackDeg = currentFlightData.track.takeIf { it.isFinite() },
                groundSpeedMs = currentFlightData.groundSpeed,
                hasGpsFix = hasGpsFix(),
                windFromDeg = currentFlightData.windDirection.toDouble()
                    .takeIf { currentFlightData.windSpeed > 0f },
                windSpeedMs = currentFlightData.windSpeed.toDouble(),
                minTrackSpeedMs = minSpeedThresholdMs,
                isFlying = isFlying
            )
        )

        val orientationData = OrientationSensorData(
            track = currentFlightData.track,
            magneticHeading = primaryHeading ?: 0.0,
            groundSpeed = currentFlightData.groundSpeed,
            isGPSValid = hasGpsFix(),
            hasValidHeading = headingSolution.isValid,
            compassReliable = primaryReliable,
            windDirectionFrom = currentFlightData.windDirection.toDouble(),
            windSpeed = currentFlightData.windSpeed.toDouble(),
            headingSolution = headingSolution,
            timestamp = nowWall
        )

        if (nowWall % 3000 < 100) {
            Log.d(
                TAG,
                " Orientation update: " +
                    "track=${orientationData.track}, " +
                    "magHeading=${orientationData.magneticHeading.toInt()}, " +
                    "speed=${orientationData.groundSpeed}kt, " +
                    "gpsValid=${orientationData.isGPSValid}, " +
                    "headingValid=${orientationData.hasValidHeading}"
            )
        }

        _orientationFlow.value = orientationData
    }

    private fun smoothBearingTransition(oldBearing: Double, newBearing: Double): Double {
        var diff = newBearing - oldBearing
        if (diff > 180) diff -= 360.0
        else if (diff < -180) diff += 360.0
        return (oldBearing + diff * SMOOTHING_FACTOR + 360.0) % 360.0
    }

    private fun updateCompassReliability(now: Long, reliable: Boolean) {
        if (!reliable) return
        if (lastCompassReliableTime == 0L || now - lastCompassReliableTime > HEADING_STALE_THRESHOLD_MS) {
            compassReliableSince = now
        }
        lastCompassReliableTime = now
    }

    private fun updateAttitudeReliability(now: Long, reliable: Boolean) {
        if (!reliable) return
        if (lastAttitudeReliableTime == 0L || now - lastAttitudeReliableTime > HEADING_STALE_THRESHOLD_MS) {
            attitudeReliableSince = now
        }
        lastAttitudeReliableTime = now
    }

    private fun updateActiveHeadingSource(
        now: Long,
        compassFresh: Boolean,
        attitudeFresh: Boolean,
        compassStable: Boolean,
        attitudeStable: Boolean
    ) {
        val activeFresh = when (activeHeadingSource) {
            HeadingSource.ATTITUDE -> attitudeFresh
            HeadingSource.COMPASS -> compassFresh
            HeadingSource.NONE -> false
        }

        if (activeFresh) {
            if (activeHeadingSource == HeadingSource.COMPASS && attitudeStable) {
                if (now - lastSourceSwitchTime >= SOURCE_SWITCH_STABLE_MS) {
                    activeHeadingSource = HeadingSource.ATTITUDE
                    lastSourceSwitchTime = now
                }
            }
            return
        }

        val preferred = when {
            attitudeStable -> HeadingSource.ATTITUDE
            compassStable -> HeadingSource.COMPASS
            else -> HeadingSource.NONE
        }
        if (preferred != activeHeadingSource) {
            activeHeadingSource = preferred
            lastSourceSwitchTime = now
        }
    }

    private fun resetHeadingState() {
        filteredCompassHeading = 0.0
        hasCompassHeading = false
        lastCompassUpdateTime = 0L
        lastCompassReliableTime = 0L
        compassReliableSince = 0L
        filteredAttitudeHeading = 0.0
        hasAttitudeHeading = false
        lastAttitudeUpdateTime = 0L
        lastAttitudeReliableTime = 0L
        attitudeReliableSince = 0L
        activeHeadingSource = HeadingSource.NONE
        lastSourceSwitchTime = 0L
        latestCompass = null
        latestAttitude = null
        lastCompassHeading = Double.NaN
        lastCompassTime = 0L
        lastCompassReliable = false
        lastAttitudeHeading = Double.NaN
        lastAttitudeTime = 0L
        lastAttitudeReliable = false
        lastHeadingInputSource = "NONE"
        isFlying = false
        attitudeSensorSeen = false
    }

    override fun getCurrentData(): OrientationSensorData = _orientationFlow.value

    fun hasRequiredSensors(): Boolean {
        val status = unifiedSensorManager.getSensorStatus()
        return status.compassAvailable || status.rotationAvailable
    }

    fun getSensorInfo(): String {
        val status = unifiedSensorManager.getSensorStatus()
        val sensors = mutableListOf<String>()
        if (status.compassAvailable) sensors.add("Compass")
        if (status.accelAvailable) sensors.add("Accelerometer")
        if (status.rotationAvailable) sensors.add("Rotation Vector")
        return if (sensors.isNotEmpty()) {
            "Available: ${sensors.joinToString(", ")}"
        } else {
            "No orientation sensors available"
        }
    }

    internal data class HeadingDebugSnapshot(
        val inputSource: String,
        val activeSource: String,
        val activeHeading: Double?,
        val compassHeading: Double?,
        val compassAgeMs: Long?,
        val compassReliable: Boolean,
        val attitudeHeading: Double?,
        val attitudeAgeMs: Long?,
        val attitudeReliable: Boolean
    )

    internal fun getHeadingDebugSnapshot(now: Long): HeadingDebugSnapshot {
        val activeHeading = when (activeHeadingSource) {
            HeadingSource.ATTITUDE -> filteredAttitudeHeading.takeIf { hasAttitudeHeading }
            HeadingSource.COMPASS -> filteredCompassHeading.takeIf { hasCompassHeading }
            HeadingSource.NONE -> null
        }
        val compassHeading = if (lastCompassTime > 0L && lastCompassHeading.isFinite()) {
            lastCompassHeading
        } else {
            null
        }
        val attitudeHeading = if (lastAttitudeTime > 0L && lastAttitudeHeading.isFinite()) {
            lastAttitudeHeading
        } else {
            null
        }
        val compassAge = if (lastCompassTime > 0L) now - lastCompassTime else null
        val attitudeAge = if (lastAttitudeTime > 0L) now - lastAttitudeTime else null
        return HeadingDebugSnapshot(
            inputSource = lastHeadingInputSource,
            activeSource = activeHeadingSource.name,
            activeHeading = activeHeading,
            compassHeading = compassHeading,
            compassAgeMs = compassAge,
            compassReliable = lastCompassReliable,
            attitudeHeading = attitudeHeading,
            attitudeAgeMs = attitudeAge,
            attitudeReliable = lastAttitudeReliable
        )
    }

    private fun hasGpsFix(): Boolean {
        return currentFlightData.accuracy > 0.0 ||
            currentFlightData.latitude != 0.0 ||
            currentFlightData.longitude != 0.0
    }
}
