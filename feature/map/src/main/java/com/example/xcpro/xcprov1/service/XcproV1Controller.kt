package com.example.xcpro.xcprov1.service

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.xcpro.sensors.AccelData
import com.example.xcpro.sensors.AttitudeData
import com.example.xcpro.sensors.BaroData
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.sensors.SensorStatus
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.xcprov1.audio.XcproV1AudioEngine
import com.example.xcpro.xcprov1.bluetooth.GloGpsFix
import com.example.xcpro.xcprov1.filters.Js1cAeroModel
import com.example.xcpro.xcprov1.filters.XcproV1KalmanFilter
import com.example.xcpro.xcprov1.model.DiagnosticsSnapshot
import com.example.xcpro.xcprov1.model.FlightDataV1Snapshot
import com.example.xcpro.xcprov1.model.XcproV1State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt
import java.util.Locale

/**
 * Coordinates sensor ingestion, filter updates and snapshot publication for XCPro V1.
 */
class XcproV1Controller(
    private val context: Context,
    private val sensorManager: UnifiedSensorManager,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "XcproV1Controller"
        private const val MIN_GPS_VERTICAL_INTERVAL_MS = 250L
        private const val G = 9.80665
        private const val EXTERNAL_FIX_TIMEOUT_MS = 1500L
        private const val MIN_TRUE_AIRSPEED_MS = 15.0
        private const val MIN_QNH_CAL_INTERVAL_MS = 10_000L
        private const val MAX_QNH_ALTITUDE_ERROR_METERS = 60.0
        private const val LARGE_QNH_ALTITUDE_STEP_METERS = 3.0
        private const val MAX_QNH_GPS_ACCURACY_METERS = 5.5
    }

    private val filter = XcproV1KalmanFilter()
    private val audioEngine = XcproV1AudioEngine(context, scope)

    private val _snapshotFlow = MutableStateFlow<FlightDataV1Snapshot?>(null)
    val snapshotFlow: StateFlow<FlightDataV1Snapshot?> = _snapshotFlow

    private val _audioEnabled = MutableStateFlow(false)
    val audioEnabled: StateFlow<Boolean> = _audioEnabled

    val audioTelemetry: StateFlow<XcproV1AudioEngine.AudioTelemetry> = audioEngine.audioStats

    private val externalGpsFixFlow = MutableStateFlow<GloGpsFix?>(null)

    private var qnhHpa = 1013.25
    private var lastHandsetAltitude = Double.NaN
    private var lastHandsetTimestamp = 0L
    private var lastExternalAltitude = Double.NaN
    private var lastExternalTimestamp = 0L
    private var lastTrackRad: Double? = null
    private var lastTrackTimestamp = 0L
    private var lastQnhCalibrationMs = 0L
    private var lastState = XcproV1State(
        altitude = 0.0,
        climbRate = 0.0,
        accelBias = 0.0,
        verticalWind = 0.0,
        windX = 0.0,
        windY = 0.0
    )
    private var lastBaroSample: BaroData? = null
    private var lastSensorUpdateElapsedMs: Long = SystemClock.elapsedRealtime()

    init {
        audioEngine.initialize()
        audioEngine.setEnabled(_audioEnabled.value)
        start()
    }

    /**
     * Attach an external GPS flow (e.g. Garmin GLO 2). The latest fix will be
     * used whenever it is fresher than the handset GPS.
     */
    fun attachExternalGpsFlow(flow: StateFlow<GloGpsFix?>) {
        scope.launch(Dispatchers.Default) {
            flow.collect { fix ->
                externalGpsFixFlow.value = fix
            }
        }
    }

    private fun start() {
        scope.launch(Dispatchers.Default) {
            combine(
                sensorManager.baroFlow,
                sensorManager.accelFlow,
                sensorManager.gpsFlow,
                sensorManager.attitudeFlow,
                externalGpsFixFlow
            ) { baro, accel, gps, attitude, external ->
                SensorInputs(
                    baro = baro,
                    accel = accel,
                    handsetGps = gps,
                    attitude = attitude,
                    externalGps = external
                )
            }.collect { inputs ->
                processSensorInputs(inputs)
            }
        }
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(5_000)
                val elapsed = SystemClock.elapsedRealtime() - lastSensorUpdateElapsedMs
                if (elapsed > 5_000) {
                    Log.w(TAG, "No sensor updates for ${elapsed}ms – resetting HAWK filter")
                    resetFilterState()
                    lastSensorUpdateElapsedMs = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    private fun processSensorInputs(inputs: SensorInputs) {
        val accel = inputs.accel ?: return
        val fusedGps = selectGpsFrame(inputs.handsetGps, inputs.externalGps) ?: return

        inputs.baro?.let { lastBaroSample = it }
        val baroSample = lastBaroSample ?: return

        var calibrationAltitudeStep: Double? = null
        if (inputs.baro != null && shouldCalibrateQnh(fusedGps, baroSample.pressureHPa)) {
            calibrationAltitudeStep = calibrateQnh(baroSample.pressureHPa, fusedGps)
        }

        val baroAltitude = pressureToAltitude(baroSample.pressureHPa)

        if (calibrationAltitudeStep != null && abs(calibrationAltitudeStep) >= LARGE_QNH_ALTITUDE_STEP_METERS) {
            handleLargeQnhJump(calibrationAltitudeStep, baroAltitude)
        }

        val verticalAccel = if (accel.isReliable) accel.verticalAcceleration else 0.0
        val gpsVertical = computeGpsVertical(fusedGps)
        val trackRad = fusedGps.trackDegrees?.let { Math.toRadians(it) }

        val reliableAttitude = inputs.attitude?.takeIf { it.isReliable }
        val pitchDeg = reliableAttitude?.pitchDeg
        val rollDeg = reliableAttitude?.rollDeg

        val bankDeg = rollDeg ?: computeBank(trackRad, fusedGps.timestamp, fusedGps.speed)

        val windX = lastState.windX
        val windY = lastState.windY
        val groundVector = trackRad?.let { vectorFromPolar(fusedGps.speed, it) } ?: Pair(0.0, 0.0)
        val airVector = Pair(groundVector.first - windX, groundVector.second - windY)
        val airVectorMagnitude = airVector.magnitude()
        val tas = maxOf(airVectorMagnitude, MIN_TRUE_AIRSPEED_MS)
        val airBearing = when {
            airVectorMagnitude > 0.1 -> atan2(airVector.second, airVector.first)
            reliableAttitude != null -> Math.toRadians(reliableAttitude.headingDeg)
            else -> trackRad
        }

        val headingDeg = reliableAttitude?.headingDeg ?: airBearing?.let { Math.toDegrees(it) }

        Log.v(
            TAG,
            String.format(
                Locale.US,
                "HAWK inputs: baro=%.1f hPa, accel=%.3f m/s^2, gpsAlt=%.1f m, extAlt=%.1f m",
                baroSample.pressureHPa,
                verticalAccel,
                fusedGps.altitude,
                inputs.externalGps?.altitudeMeters ?: Double.NaN
            )
        )

        val result = filter.update(
            timestamp = maxOf(baroSample.timestamp, fusedGps.timestamp),
            baroAltitude = baroAltitude,
            verticalAccel = verticalAccel,
            gpsVerticalSpeed = gpsVertical,
            gpsGroundSpeed = fusedGps.speed,
            gpsTrackRad = trackRad,
            trueAirspeed = tas,
            airBearingRad = airBearing,
            headingDeg = headingDeg,
            wingLoading = Js1cAeroModel.defaultWingLoading(),
            bankDeg = bankDeg ?: 0.0,
            attitudePitchDeg = pitchDeg,
            attitudeRollDeg = rollDeg ?: bankDeg
        )

        lastState = result.state

        result.snapshot?.let { snapshot ->
            Log.v(
                TAG,
                String.format(
                    Locale.US,
                    "HAWK snapshot: climb=%.2f, netto=%.2f, confidence=%.2f",
                    snapshot.actualClimb,
                    snapshot.netto,
                    snapshot.confidence
                )
            )
        }

        _snapshotFlow.value = result.snapshot
        audioEngine.updateFromSnapshot(result.snapshot)
        lastSensorUpdateElapsedMs = SystemClock.elapsedRealtime()
    }

    fun reset() {
        filter.reset()
    }

    fun setAudioEnabled(enabled: Boolean) {
        _audioEnabled.value = enabled
        audioEngine.setEnabled(enabled)
    }

    fun release() {
        audioEngine.release()
    }

    fun getSnapshot(): FlightDataV1Snapshot? = snapshotFlow.value

    fun currentState(): XcproV1State = lastState

    fun sensorStatus(): SensorStatus = sensorManager.getSensorStatus()

    private fun calibrateQnh(pressure: Double, frame: GpsFrame): Double? {
        val estimatedBefore = pressureToAltitude(pressure, qnhHpa)
        val error = frame.altitude - estimatedBefore
        if (abs(error) < 200 && abs(error) > 0.5) {
            val exponent = 1.0 / 0.190284
            val pressureEquivalent = pressure / (1.0 - frame.altitude / 44330.0).pow(exponent)
            val updatedQnh = (0.98 * qnhHpa) + 0.02 * pressureEquivalent
            val estimatedAfter = pressureToAltitude(pressure, updatedQnh)
            qnhHpa = updatedQnh
            lastQnhCalibrationMs = System.currentTimeMillis()
            return estimatedAfter - estimatedBefore
        }
        return null
    }

    private fun shouldCalibrateQnh(frame: GpsFrame, pressure: Double): Boolean {
        if (!frame.isHighAccuracy || frame.accuracy > MAX_QNH_GPS_ACCURACY_METERS) {
            return false
        }
        val now = System.currentTimeMillis()
        if (lastQnhCalibrationMs != 0L && now - lastQnhCalibrationMs < MIN_QNH_CAL_INTERVAL_MS) {
            return false
        }
        val estimatedAltitude = pressureToAltitude(pressure)
        val delta = abs(frame.altitude - estimatedAltitude)
        if (delta > MAX_QNH_ALTITUDE_ERROR_METERS) {
            val deltaLabel = String.format(Locale.US, "%.1f", delta)
            Log.d(TAG, "Skipping QNH auto-calibration: gps/pressure delta=${deltaLabel}m")
            return false
        }
        return true
    }

    private fun handleLargeQnhJump(stepMeters: Double, baroAltitude: Double) {
        val stepLabel = String.format(Locale.US, "%.1f", stepMeters)
        Log.w(TAG, "QNH calibration adjusted altitude by ${stepLabel}m – resetting Hawk filter")
        filter.reset()
        lastState = XcproV1State(
            altitude = baroAltitude,
            climbRate = 0.0,
            accelBias = 0.0,
            verticalWind = 0.0,
            windX = 0.0,
            windY = 0.0
        )
        lastHandsetAltitude = Double.NaN
        lastExternalAltitude = Double.NaN
        lastHandsetTimestamp = 0L
        lastExternalTimestamp = 0L
    }

    private fun pressureToAltitude(pressure: Double, qnh: Double = qnhHpa): Double {
        val ratio = pressure / qnh
        return 44330.0 * (1.0 - ratio.pow(0.190284))
    }

    private fun computeGpsVertical(frame: GpsFrame): Double? {
        if (!frame.isMoving) return null
        val (lastAlt, lastTs) = when (frame.source) {
            GpsFrame.Source.HANDSET -> lastHandsetAltitude to lastHandsetTimestamp
            GpsFrame.Source.GARMIN -> lastExternalAltitude to lastExternalTimestamp
        }
        val deltaT = frame.timestamp - lastTs
        if (lastTs == 0L || deltaT < MIN_GPS_VERTICAL_INTERVAL_MS) {
            updateAltitudeState(frame.altitude, frame.timestamp, frame.source)
            return null
        }
        val deltaAlt = frame.altitude - lastAlt
        updateAltitudeState(frame.altitude, frame.timestamp, frame.source)
        return deltaAlt / (deltaT / 1000.0)
    }

    private fun computeBank(trackRad: Double?, timestamp: Long, groundSpeed: Double): Double? {
        if (trackRad == null) {
            lastTrackRad = null
            lastTrackTimestamp = timestamp
            return null
        }
        val previousTrack = lastTrackRad
        val previousTime = lastTrackTimestamp
        lastTrackRad = trackRad
        lastTrackTimestamp = timestamp
        if (previousTrack == null || previousTime == 0L) return null
        val dt = (timestamp - previousTime) / 1000.0
        if (dt <= 0.01) return null
        var delta = trackRad - previousTrack
        while (delta > Math.PI) delta -= 2 * Math.PI
        while (delta < -Math.PI) delta += 2 * Math.PI
        val turnRate = delta / dt
        if (groundSpeed <= 1.0) return null
        val bankRad = kotlin.math.atan((groundSpeed * turnRate) / G)
        return Math.toDegrees(bankRad)
    }

    private fun updateAltitudeState(altitude: Double, timestamp: Long, source: GpsFrame.Source) {
        when (source) {
            GpsFrame.Source.HANDSET -> {
                lastHandsetAltitude = altitude
                lastHandsetTimestamp = timestamp
            }
            GpsFrame.Source.GARMIN -> {
                lastExternalAltitude = altitude
                lastExternalTimestamp = timestamp
            }
        }
    }

    private fun selectGpsFrame(handset: GPSData?, external: GloGpsFix?): GpsFrame? {
        val now = System.currentTimeMillis()
        val externalFresh = external?.let { now - it.timestampMillis <= EXTERNAL_FIX_TIMEOUT_MS } ?: false
        return when {
            externalFresh -> {
                val ext = external!!
                val fallbackTrack = handset?.bearing?.takeIf { handset.isMoving }
                val fallbackSpeed = handset?.speed
                val fallbackAltitude = handset?.altitude
                val altitude = ext.altitudeMeters ?: fallbackAltitude ?: 0.0
                val speed = ext.groundSpeedMps ?: fallbackSpeed ?: 0.0
                val track = ext.trackDegrees ?: fallbackTrack
                val accuracy = (ext.hdop ?: 1.2) * 5.0
                GpsFrame(
                    latitude = ext.latitude,
                    longitude = ext.longitude,
                    altitude = altitude,
                    speed = speed,
                    trackDegrees = track,
                    accuracy = accuracy,
                    timestamp = ext.timestampMillis,
                    isHighAccuracy = accuracy <= 5.0,
                    isMoving = speed > 0.3,
                    source = GpsFrame.Source.GARMIN
                )
            }
            handset != null -> {
                GpsFrame(
                    latitude = handset.latLng.latitude,
                    longitude = handset.latLng.longitude,
                    altitude = handset.altitude,
                    speed = handset.speed,
                    trackDegrees = handset.bearing.takeIf { handset.isMoving },
                    accuracy = handset.accuracy.toDouble(),
                    timestamp = handset.timestamp,
                    isHighAccuracy = handset.isHighAccuracy,
                    isMoving = handset.isMoving,
                    source = GpsFrame.Source.HANDSET
                )
            }
            else -> null
        }
    }

    private data class SensorInputs(
        val baro: BaroData?,
        val accel: AccelData?,
        val handsetGps: GPSData?,
        val attitude: AttitudeData?,
        val externalGps: GloGpsFix?
    )

    private fun resetFilterState() {
        filter.reset()
        lastState = XcproV1State(
            altitude = 0.0,
            climbRate = 0.0,
            accelBias = 0.0,
            verticalWind = 0.0,
            windX = 0.0,
            windY = 0.0
        )
        lastBaroSample = null
        lastHandsetAltitude = Double.NaN
        lastExternalAltitude = Double.NaN
        lastHandsetTimestamp = 0L
        lastExternalTimestamp = 0L
        val resetSnapshot = FlightDataV1Snapshot(
            timestampMillis = System.currentTimeMillis(),
            actualClimb = 0.0,
            potentialClimb = 0.0,
            netto = 0.0,
            verticalWind = 0.0,
            windX = 0.0,
            windY = 0.0,
            confidence = 0.0,
            climbTrend = 0.0,
            aoaDeg = null,
            sideslipDeg = null,
            sourceLabel = "reset",
            diagnostics = DiagnosticsSnapshot(
                covarianceTrace = 0.0,
                baroInnovation = 0.0,
                accelInnovation = 0.0,
                gpsInnovation = 0.0,
                residualRms = 0.0
            )
        )
        _snapshotFlow.value = resetSnapshot
        audioEngine.updateFromSnapshot(resetSnapshot)
        lastSensorUpdateElapsedMs = SystemClock.elapsedRealtime()
    }

    private data class GpsFrame(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val speed: Double,
        val trackDegrees: Double?,
        val accuracy: Double,
        val timestamp: Long,
        val isHighAccuracy: Boolean,
        val isMoving: Boolean,
        val source: Source
    ) {
        enum class Source { HANDSET, GARMIN }
    }

    private fun vectorFromPolar(speed: Double, headingRad: Double): Pair<Double, Double> =
        Pair(speed * kotlin.math.cos(headingRad), speed * kotlin.math.sin(headingRad))

    private fun Pair<Double, Double>.magnitude(): Double =
        sqrt(first * first + second * second)
}
