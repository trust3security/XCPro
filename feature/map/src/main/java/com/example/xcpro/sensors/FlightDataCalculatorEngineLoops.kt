package com.example.xcpro.sensors

import com.example.xcpro.core.common.logging.AppLogger
import java.util.Locale
import kotlin.math.abs

internal fun FlightDataCalculatorEngine.updateVarioFilter(baro: BaroData?, accel: AccelData?) {
    if (baro == null) {
        if (AppLogger.rateLimit(FlightDataCalculatorEngine.TAG, "no_baro", 1_000L)) {
            AppLogger.d(FlightDataCalculatorEngine.TAG, "No barometer data - skipping vario update")
        }
        return
    }

    val wallTime = System.currentTimeMillis()

    val calcTime = if (isReplayMode) {
        baro.timestamp
    } else {
        baro.monotonicTimestampMillis.takeIf { it > 0L } ?: baro.timestamp
    }

    if (lastBaroSampleTime != 0L && calcTime == lastBaroSampleTime) {
        return
    }
    lastBaroSampleTime = calcTime

    val outputTime = if (isReplayMode) calcTime else wallTime

    // In replay mode, downstream estimators (wind, circling, etc.) use the sensor timestamps as
    // the "simulation clock". Keep the vario validity clock in the same time base.
    val currentTime = calcTime

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
    val smoothedPressure = filters.pressureKalmanFilter.update(baro.pressureHPa.value, currentTime)

    val previousBaroResult = cachedBaroResult
    val baroResult = baroCalculator.calculateBarometricAltitude(
        rawPressureHPa = smoothedPressure,
        gpsAltitudeMeters = null,
        gpsAccuracy = null,
        isGPSFixed = false
    )

    if (previousBaroResult != null) {
        val qnhDelta = abs(baroResult.qnh - previousBaroResult.qnh)
        val altitudeDelta = abs(baroResult.altitudeMeters - previousBaroResult.altitudeMeters)
        val qnhJumpDetected = qnhDelta > FlightDataCalculatorEngine.QNH_JUMP_THRESHOLD_HPA
        if (qnhJumpDetected) {
            val qnhLabel = String.format(Locale.US, "%.2f", qnhDelta)
            val altitudeLabel = String.format(Locale.US, "%.1f", altitudeDelta)
            if (isReplayMode) {
                AppLogger.w(
                    FlightDataCalculatorEngine.TAG,
                    "Replay QNH jump detected ??${qnhLabel} hPa / ??${altitudeLabel} m - ignoring reset to keep vario stable"
                )
            } else {
                AppLogger.w(
                    FlightDataCalculatorEngine.TAG,
                    "QNH jump detected ??${qnhLabel} hPa / ??${altitudeLabel} m - resetting vario filters"
                )
                varioSuite.resetAll()
                filters.baroFilter.reset()
                filters.pressureKalmanFilter.reset(smoothedPressure, currentTime)
                cachedVarioResult = null
                emissionState.varioValidUntil = 0L
            }
        }
    }

    val verticalAccelForFusion = accel?.let { accelSample ->
        val accelTimestamp = accelSample.monotonicTimestampMillis.takeIf { it > 0L } ?: accelSample.timestamp
        val ageMs = currentTime - accelTimestamp
        val fresh = ageMs in 0..FlightDataCalculatorEngine.ACCEL_FRESHNESS_MS
        if (!accelSample.isReliable || !fresh) {
            0.0
        } else {
            val clamped = accelSample.verticalAcceleration
                .coerceIn(-FlightDataCalculatorEngine.MAX_VERTICAL_ACCEL_MS2, FlightDataCalculatorEngine.MAX_VERTICAL_ACCEL_MS2)
            val dt = deltaTime.coerceAtLeast(1e-3)
            val alpha = dt / (FlightDataCalculatorEngine.ACCEL_SMOOTH_TAU_S + dt)
            val prev = smoothedVerticalAccel
            val next = if (prev == null || accelTimestamp <= lastAccelTimestamp) {
                clamped
            } else {
                prev + alpha * (clamped - prev)
            }
            lastAccelTimestamp = accelTimestamp
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
        gpsAccuracy = cachedGPSAccuracy,
        timestampMillis = currentTime
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
    val validityWindowMs = maxOf(FlightDataCalculatorEngine.VARIO_VALIDITY_MS, replayWindowMs, FlightDataCalculatorEngine.VARIO_VALIDITY_FLOOR_MS)
    emissionState.varioValidUntil = currentTime + validityWindowMs
    audioController.update(emissionState.latestTeVario, varioResult.verticalSpeed, currentTime, emissionState.varioValidUntil)

    cachedVarioResult = varioResult
    cachedBaroResult = baroResult
    cachedBaroData = baro

    val shouldEmit = cachedGPS != null &&
        (currentTime - emissionState.lastUpdateTime) >= FlightDataCalculatorEngine.EMIT_MIN_INTERVAL_MS
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
                outputTimestampMillis = outputTime,
                deltaTime = emitDeltaTime,
                varioResultInput = varioResult,
                baroResult = cachedBaroResult,
                baro = cachedBaroData,
                cachedVarioResult = cachedVarioResult,
                windState = latestWindState,
                isFlying = latestFlightState?.isFlying == true,
                replayRealVarioMs = replayRealVarioMs,
                replayRealVarioTimestamp = replayRealVarioTimestamp,
                macCreadySetting = macCreadySetting,
                macCreadyRisk = macCreadyRisk
            )
        }
    }

    val diagnosticsTime = currentTime
    if (diagnosticsTime - lastDiagnosticsEmitTime >= FlightDataCalculatorEngine.DIAGNOSTICS_EMIT_MIN_INTERVAL_MS) {
        val gpsAccuracy = cachedGPSAccuracy.takeIf { it.isFinite() } ?: Double.POSITIVE_INFINITY
        val diagnostics = varioSuite.optimizedDiagnostics(
            gpsAccuracy = gpsAccuracy,
            gpsSatelliteCount = 0
        )
        _diagnosticsFlow.value = VarioDiagnosticsSample(
            timestamp = diagnosticsTime,
            teVerticalSpeed = emissionState.latestTeVario,
            rawVerticalSpeed = diagnostics.filteredVerticalSpeed,
            diagnostics = diagnostics
        )
        lastDiagnosticsEmitTime = diagnosticsTime
    }

    lastVarioUpdateTime = currentTime

    if (isReplayMode && wallTime - lastReplayBaroLogTime >= 1_000L) {
        lastReplayBaroLogTime = wallTime
        logReplayBaroSample(FlightDataCalculatorEngine.TAG, baro.timestamp, baro.pressureHPa.value, smoothedPressure, baroResult.altitudeMeters, filteredBaro.displayAltitude, varioResult.verticalSpeed, deltaTime, cachedGPSAltitude, cachedGPSSpeed, emissionState.varioValidUntil)
    }

}

internal fun FlightDataCalculatorEngine.updateGPSData(gps: GPSData?, compass: CompassData?) {
    if (gps == null) {
        if (AppLogger.rateLimit(FlightDataCalculatorEngine.TAG, "no_gps", 1_000L)) {
            AppLogger.d(FlightDataCalculatorEngine.TAG, "No GPS data - skipping GPS update")
        }
        return
    }

    val wallTime = System.currentTimeMillis()
    // Use GPS timestamps as the "simulation clock" in replay mode so time-based metrics (wind,
    // thermal windows, circling detection) advance with the IGC log instead of wall clock.
    val calcTime = if (isReplayMode) {
        gps.timestamp
    } else {
        gps.monotonicTimestampMillis.takeIf { it > 0L } ?: gps.timestamp
    }
    val currentTime = calcTime
    val outputTime = if (isReplayMode) calcTime else wallTime
    if (isReplayMode && wallTime - lastReplayGpsLogTime >= 1_000L) {
        lastReplayGpsLogTime = wallTime
        logReplayGpsSample(FlightDataCalculatorEngine.TAG, gps.position.latitude, gps.position.longitude, gps.altitude.value, gps.speed.value, gps.bearing.toDouble(), gps.timestamp)
    }

    // Update cached GPS data for high-speed vario loop
    cachedGPSSpeed = gps.speed.value
    cachedGPSAltitude = gps.altitude.value
    cachedGPSAccuracy = gps.accuracy.toDouble()
    cachedIsGPSFixed = gps.isHighAccuracy
    cachedGPSLat = gps.position.latitude   // Reserved for terrain-aware metrics
    cachedGPSLon = gps.position.longitude  // Reserved for terrain-aware metrics
    cachedGPS = gps
    cachedCompassData = compass

    if (currentTime != lastGpsFixTimestampForGpsVario && gps.altitude.value.isFinite()) {
        varioSuite.updateGpsVario(gpsAltitudeMeters = gps.altitude.value, gpsTimestampMillis = currentTime)
        lastGpsFixTimestampForGpsVario = currentTime
    }

    val deltaTime = if (emissionState.lastUpdateTime > 0) {
        (currentTime - emissionState.lastUpdateTime) / 1000.0
    } else {
        0.1 // 10Hz = 100ms = 0.1s default
    }

    val baroAgeMs = currentTime - lastVarioUpdateTime
    val baroFresh = cachedBaroData != null && baroAgeMs in 0..FlightDataCalculatorEngine.BARO_EMIT_STALE_MS

    // Emit display/UI data on GPS tick only when baro is stale or unavailable.
    if (!baroFresh) {
        // Fallback: if the baro/IMU loop hasn't produced a vario yet, drive the UI with GPS vario
        // so the needle doesn't stick at zero during startup or brief baro gaps.
        val gpsFallbackVario = varioSuite.gpsVerticalSpeed().takeIf { it.isFinite() } ?: 0.0
        val varioResultInput = cachedVarioResult ?: com.example.dfcards.filters.ModernVarioResult(
            altitude = gps.altitude.value,
            verticalSpeed = gpsFallbackVario,
            acceleration = 0.0,
            confidence = 0.3
        )
        if (cachedVarioResult == null) {
            emissionState.varioValidUntil = currentTime + FlightDataCalculatorEngine.VARIO_VALIDITY_FLOOR_MS
        }
        emitter.emit(
            gps = gps,
            compass = compass,
            currentTime = currentTime,
            outputTimestampMillis = outputTime,
            deltaTime = deltaTime,
            varioResultInput = varioResultInput,
            baroResult = cachedBaroResult,
            baro = cachedBaroData,
            cachedVarioResult = cachedVarioResult,
            windState = latestWindState,
            isFlying = latestFlightState?.isFlying == true,
            replayRealVarioMs = replayRealVarioMs,
            replayRealVarioTimestamp = replayRealVarioTimestamp,
            macCreadySetting = macCreadySetting,
            macCreadyRisk = macCreadyRisk
        )
    }
}
