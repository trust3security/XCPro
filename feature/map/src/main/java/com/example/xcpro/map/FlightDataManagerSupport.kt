package com.example.xcpro.map

import com.example.dfcards.FlightModeSelection
import com.example.xcpro.core.flight.RealTimeFlightData
import com.example.xcpro.common.flight.FlightMode
import kotlin.math.abs

internal fun FlightMode.toFlightModeSelection(): FlightModeSelection =
    when (this) {
        FlightMode.CRUISE -> FlightModeSelection.CRUISE
        FlightMode.THERMAL -> FlightModeSelection.THERMAL
        FlightMode.FINAL_GLIDE -> FlightModeSelection.FINAL_GLIDE
    }

internal fun FlightModeSelection.toFlightMode(): FlightMode =
    when (this) {
        FlightModeSelection.CRUISE -> FlightMode.CRUISE
        FlightModeSelection.THERMAL -> FlightMode.THERMAL
        FlightModeSelection.FINAL_GLIDE -> FlightMode.FINAL_GLIDE
    }

internal fun RealTimeFlightData.toDisplayBucket(
    varioBucketMs: Float,
    altitudeBucketM: Double,
    windSpeedBucketKt: Float,
    windDirBucketDeg: Float,
    ldBucket: Float
): RealTimeFlightData =
    copy(
        displayVario = displayVario.takeIf { it.isFinite() }?.bucket(varioBucketMs.toDouble()) ?: 0.0,
        baselineDisplayVario = baselineDisplayVario.takeIf { it.isFinite() }?.bucket(varioBucketMs.toDouble()) ?: 0.0,
        netto = netto.takeIf { it.isFinite() }?.bucket(varioBucketMs) ?: 0f,
        displayNetto = displayNetto.takeIf { it.isFinite() }?.bucket(varioBucketMs.toDouble()) ?: 0.0,
        baroAltitude = baroAltitude.takeIf { it.isFinite() }?.bucket(altitudeBucketM) ?: 0.0,
        gpsAltitude = gpsAltitude.takeIf { it.isFinite() }?.bucket(altitudeBucketM) ?: 0.0,
        agl = agl.takeIf { it.isFinite() }?.bucket(altitudeBucketM) ?: Double.NaN,
        windSpeed = windSpeed.takeIf { it.isFinite() }?.bucket(windSpeedBucketKt) ?: 0f,
        windDirection = windDirection.takeIf { it.isFinite() }?.bucket(windDirBucketDeg) ?: 0f,
        currentLD = currentLD.takeIf { it.isFinite() }?.bucket(ldBucket) ?: 0f,
        polarLdCurrentSpeed = polarLdCurrentSpeed.takeIf { it.isFinite() }?.bucket(ldBucket) ?: 0f,
        polarBestLd = polarBestLd.takeIf { it.isFinite() }?.bucket(ldBucket) ?: 0f
    )

internal fun RealTimeFlightData?.resolveDisplayVario(
    varioBucketMs: Float,
    varioNoiseFloor: Double
): Float {
    if (this == null) return 0f

    val display = displayVario
    val fallback = verticalSpeed
    val selected = when {
        varioValid && display.isFinite() -> display
        display.isFinite() && abs(display) > varioNoiseFloor -> display
        fallback.isFinite() -> fallback
        else -> 0.0
    }
    return selected.toFloat().bucket(varioBucketMs)
}

internal fun deriveWindIndicatorState(
    previous: WindIndicatorState,
    data: RealTimeFlightData?
): WindIndicatorState {
    if (data == null) {
        return previous.copy(
            isValid = false,
            quality = 0,
            ageSeconds = -1
        )
    }
    val quality = data.windQuality
    val isValid = data.windValid
    // AI-NOTE: Keep last valid direction when wind is invalid so the UI arrow does not snap to north.
    val direction = if (isValid) normalizeAngleDeg(data.windDirection) else previous.directionFromDeg
    return WindIndicatorState(
        directionFromDeg = direction,
        isValid = isValid,
        quality = if (isValid) quality else 0,
        ageSeconds = data.windAgeSeconds
    )
}

private fun normalizeAngleDeg(angle: Float): Float {
    var normalized = angle % 360f
    if (normalized < 0f) normalized += 360f
    return normalized
}
