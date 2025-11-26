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
        gpsAltitude = gps?.altitude?.value ?: 0.0,
        groundSpeed = gps?.speed?.value ?: 0.0,
        track = gps?.bearing ?: 0.0,
        accuracy = gps?.accuracy?.toDouble() ?: 0.0,
        satelliteCount = 0, // Not available from new system (phones don't expose this)

        // Barometric data
        baroAltitude = completeData.baroAltitude.value,
        currentPressureHPa = baro?.pressureHPa?.value ?: 1013.25,
        qnh = completeData.qnh.value,
        isQNHCalibrated = completeData.isQNHCalibrated,

        // Calculated values
        verticalSpeed = completeData.verticalSpeed.value,
        displayVario = completeData.displayVario.value,
        agl = completeData.agl.value,
        pressureAltitude = completeData.pressureAltitude.value,
        baroGpsDelta = completeData.baroGpsDelta?.value,
        baroConfidence = completeData.baroConfidence,
        qnhCalibrationAgeSeconds = completeData.qnhCalibrationAgeSeconds,

        // Performance data
        windSpeed = completeData.windSpeed.value.toFloat(),
        windDirection = completeData.windDirection,
        thermalAverage = completeData.thermalAverage.value.toFloat(),
        thermalAverageCircle = completeData.thermalAverageCircle.value.toFloat(),
        thermalAverageTotal = completeData.thermalAverageTotal.value.toFloat(),
        thermalGain = completeData.thermalGain.value,
        thermalGainValid = completeData.thermalGainValid,
        currentLD = completeData.currentLD,
        netto = completeData.netto.value.toFloat(),
        displayNetto = completeData.displayNetto.value,
        nettoValid = completeData.nettoValid,
        trueAirspeed = completeData.trueAirspeed.value,
        indicatedAirspeed = completeData.indicatedAirspeed.value,
        windQuality = completeData.windQuality,
        windSource = completeData.windSource.name,
        windHeadwind = completeData.windHeadwind.value,
        windCrosswind = completeData.windCrosswind.value,
        windAgeSeconds = windAgeSeconds(completeData.windLastUpdatedMillis),

        // NEW: Vario variants for side-by-side testing (VARIO_IMPROVEMENTS.md)
        varioOptimized = completeData.varioOptimized.value,
        varioLegacy = completeData.varioLegacy.value,
        varioRaw = completeData.varioRaw.value,
        varioGPS = completeData.varioGPS.value,
        varioComplementary = completeData.varioComplementary.value,
        realIgcVario = completeData.realIgcVario?.value,
        bruttoAverage30s = completeData.bruttoAverage30s.value,
        nettoAverage30s = completeData.nettoAverage30s.value,
        varioSource = completeData.varioSource,
        varioValid = completeData.varioValid,
        isCircling = completeData.isCircling,
        thermalAverageValid = completeData.thermalAverageValid,

        // Metadata
        flightTime = formattedFlightTime,
        timestamp = completeData.timestamp,
        lastUpdateTime = System.currentTimeMillis(),
        calculationSource = completeData.dataQuality,
        airspeedSource = completeData.airspeedSource,
        tasValid = completeData.tasValid,
        teAltitude = completeData.teAltitude.value,
        macCready = completeData.macCready,
        macCreadyRisk = completeData.macCreadyRisk
    )
}

private fun windAgeSeconds(lastUpdatedMillis: Long): Long {
    if (lastUpdatedMillis <= 0L) return -1
    val ageMs = System.currentTimeMillis() - lastUpdatedMillis
    return (ageMs / 1000L).coerceAtLeast(0L)
}
