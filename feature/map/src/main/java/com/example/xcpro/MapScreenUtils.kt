package com.example.xcpro

import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.PressureUnit
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.sensors.CompleteFlightData
import java.util.Locale
import kotlin.math.roundToInt


internal fun pressurePrecision(preferences: UnitsPreferences): Int =
    if (preferences.pressure == PressureUnit.INHG) 2 else 1

internal fun seedQnhInputValue(qnhHpa: Double, preferences: UnitsPreferences): String {
    val decimals = pressurePrecision(preferences)
    val displayValue = preferences.pressure.fromSi(PressureHpa(qnhHpa))
    return "%.${decimals}f".format(Locale.US, displayValue)
}

internal fun formatQnhDisplay(
    qnhHpa: Double,
    preferences: UnitsPreferences,
    decimals: Int = pressurePrecision(preferences)
): String {
    return UnitsFormatter.pressure(PressureHpa(qnhHpa), preferences, decimals).text
}

internal fun convertQnhInputToHpa(
    inputValue: Double,
    preferences: UnitsPreferences
): Double {
    return preferences.pressure.toSi(inputValue).value
}

internal fun formatBaroGpsDelta(
    deltaMeters: Double,
    preferences: UnitsPreferences
): String {
    val unit = preferences.altitude
    val converted = unit.fromSi(AltitudeM(deltaMeters))
    val rounded = converted.roundToInt()
    val sign = if (rounded >= 0) "+" else ""
    return "$sign$rounded ${unit.abbreviation}"
}


internal fun convertToRealTimeFlightData(completeData: CompleteFlightData): RealTimeFlightData {
    val gps = completeData.gps
    val baro = completeData.baro

    // Calculate flight time (simple implementation - starts from app launch)
    val flightTimeMs = System.currentTimeMillis() - completeData.timestamp
    val flightTimeMinutes = flightTimeMs / 60000
    val hours = flightTimeMinutes / 60
    val minutes = flightTimeMinutes % 60
    val formattedFlightTime = "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"

    return RealTimeFlightData(
        // GPS data
        latitude = gps?.latLng?.latitude ?: 0.0,
        longitude = gps?.latLng?.longitude ?: 0.0,
        gpsAltitude = gps?.altitude ?: 0.0,
        groundSpeed = gps?.speed ?: 0.0,
        track = gps?.bearing ?: 0.0,
        accuracy = gps?.accuracy?.toDouble() ?: 0.0,
        satelliteCount = 0, // Not available from new system (phones don't expose this)

        // Barometric data
        baroAltitude = completeData.baroAltitude,
        currentPressureHPa = baro?.pressureHPa ?: 1013.25,
        qnh = completeData.qnh,
        isQNHCalibrated = completeData.isQNHCalibrated,

        // Calculated values
        verticalSpeed = completeData.verticalSpeed,
        agl = completeData.agl,
        pressureAltitude = completeData.pressureAltitude,
        baroGpsDelta = completeData.baroGpsDelta,
        baroConfidence = completeData.baroConfidence,
        qnhCalibrationAgeSeconds = completeData.qnhCalibrationAgeSeconds,

        // Performance data
        windSpeed = completeData.windSpeed,
        windDirection = completeData.windDirection,
        thermalAverage = completeData.thermalAverage,
        currentLD = completeData.currentLD,
        netto = completeData.netto,

        // NEW: Vario variants for side-by-side testing (VARIO_IMPROVEMENTS.md)
        varioOptimized = completeData.varioOptimized,
        varioLegacy = completeData.varioLegacy,
        varioRaw = completeData.varioRaw,
        varioGPS = completeData.varioGPS,
        varioComplementary = completeData.varioComplementary,

        // Metadata
        flightTime = formattedFlightTime,
        timestamp = completeData.timestamp,
        lastUpdateTime = System.currentTimeMillis(),
        calculationSource = completeData.dataQuality
    )
}
