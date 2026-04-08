package com.example.xcpro

import android.hardware.SensorManager
import com.example.xcpro.core.flight.RealTimeFlightData
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.PressureUnit
import com.example.xcpro.common.units.UnitsConverter
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.glide.GlideSolution
import com.example.xcpro.hawk.HawkVarioUiState
import com.example.xcpro.navigation.WaypointNavigationSnapshot
import com.example.xcpro.orientation.HeadingResolver
import com.example.xcpro.orientation.HeadingResolverInput
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.domain.LiveWindValidityPolicy
import com.example.xcpro.taskperformance.TaskPerformanceSnapshot
import com.example.xcpro.weather.wind.model.WindState
import java.util.Locale
import kotlin.math.roundToInt


fun pressurePrecision(preferences: UnitsPreferences): Int =
    if (preferences.pressure == PressureUnit.INHG) 2 else 1

fun seedQnhInputValue(qnhHpa: Double, preferences: UnitsPreferences): String {
    val decimals = pressurePrecision(preferences)
    val displayValue = preferences.pressure.fromSi(PressureHpa(qnhHpa))
    return "%.${decimals}f".format(Locale.US, displayValue)
}

fun formatQnhDisplay(
    qnhHpa: Double,
    preferences: UnitsPreferences,
    decimals: Int = pressurePrecision(preferences)
): String {
    return UnitsFormatter.pressure(PressureHpa(qnhHpa), preferences, decimals).text
}

fun convertQnhInputToHpa(
    inputValue: Double,
    preferences: UnitsPreferences
): Double {
    return preferences.pressure.toSi(inputValue).value
}

fun formatBaroGpsDelta(
    deltaMeters: Double,
    preferences: UnitsPreferences
): String {
    val unit = preferences.altitude
    val converted = unit.fromSi(AltitudeM(deltaMeters))
    val rounded = converted.roundToInt()
    val sign = if (rounded >= 0) "+" else ""
    return "$sign$rounded ${unit.abbreviation}"
}


internal fun convertToRealTimeFlightData(
    completeData: CompleteFlightData,
    windState: WindState?,
    isFlying: Boolean,
    glideSolution: GlideSolution? = null,
    waypointNavigation: WaypointNavigationSnapshot? = null,
    taskPerformance: TaskPerformanceSnapshot? = null,
    hawkVarioUiState: HawkVarioUiState = HawkVarioUiState(),
    flightTime: String = "00:00",
    lastUpdateTimeMillis: Long = completeData.timestamp
): RealTimeFlightData {
    // AI-NOTE: Wind is sourced from WindState only; CompleteFlightData no longer carries wind.
    val gps = completeData.gps
    val baro = completeData.baro

    val compass = completeData.compass
    val compassReliable = compass != null && compass.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
    val hasGpsFix = gps != null
    val windVector = windState?.vector
    val hasWind = LiveWindValidityPolicy.isLiveWindUsable(
        windState = windState,
        airspeedSourceLabel = completeData.airspeedSource
    ) && windVector != null
    val windFromDeg = if (hasWind) {
        ((windVector!!.directionFromDeg % 360.0) + 360.0) % 360.0
    } else {
        null
    }
    val windSpeedMs = if (hasWind) windVector!!.speed else 0.0
    val headingSolution = HeadingResolver().resolve(
        HeadingResolverInput(
            primaryHeadingDeg = compass?.heading,
            primaryHeadingReliable = compassReliable,
            gpsTrackDeg = gps?.bearing,
            groundSpeedMs = gps?.speed?.value ?: 0.0,
            hasGpsFix = hasGpsFix,
            windFromDeg = windFromDeg,
            windSpeedMs = windSpeedMs,
            minTrackSpeedMs = 2.0,
            isFlying = isFlying
        )
    )

    return RealTimeFlightData(
        // GPS data
        latitude = gps?.position?.latitude ?: 0.0,
        longitude = gps?.position?.longitude ?: 0.0,
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
        displayNeedleVario = completeData.displayNeedleVario.value,
        displayNeedleVarioFast = completeData.displayNeedleVarioFast.value,
        audioVario = completeData.audioVario.value,
        agl = completeData.agl.value,
        pressureAltitude = completeData.pressureAltitude.value,
        navAltitude = completeData.navAltitude.value,
        baroGpsDelta = completeData.baroGpsDelta?.value,
        baroConfidence = completeData.baroConfidence,
        qnhCalibrationAgeSeconds = completeData.qnhCalibrationAgeSeconds,

        // Performance data
        windSpeed = 0f,
        windDirection = 0f,
        thermalAverage = completeData.thermalAverage.value.toFloat(),
        thermalAverageCircle = completeData.thermalAverageCircle.value.toFloat(),
        thermalAverageTotal = completeData.thermalAverageTotal.value.toFloat(),
        thermalGain = completeData.thermalGain.value,
        thermalGainValid = completeData.thermalGainValid,
        currentThermalLiftRate = completeData.currentThermalLiftRate.value,
        currentThermalValid = completeData.currentThermalValid,
        currentLD = completeData.currentLD,
        currentLDValid = completeData.currentLDValid,
        currentLDAir = completeData.currentLDAir,
        currentLDAirValid = completeData.currentLDAirValid,
        polarLdCurrentSpeed = completeData.polarLdCurrentSpeed,
        polarLdCurrentSpeedValid = completeData.polarLdCurrentSpeedValid,
        polarBestLd = completeData.polarBestLd,
        polarBestLdValid = completeData.polarBestLdValid,
        netto = completeData.netto.value.toFloat(),
        displayNetto = completeData.displayNetto.value,
        nettoValid = completeData.nettoValid,
        trueAirspeed = completeData.trueAirspeed.value,
        indicatedAirspeed = completeData.indicatedAirspeed.value,
        windQuality = 0,
        windConfidence = 0.0,
        windValid = false,
        windSource = "",
        windHeadwind = 0.0,
        windCrosswind = 0.0,
        windAgeSeconds = -1,

        // NEW: Vario variants for side-by-side testing (VARIO_IMPROVEMENTS.md)
        varioOptimized = completeData.varioOptimized.value,
        varioLegacy = completeData.varioLegacy.value,
        varioRaw = completeData.varioRaw.value,
        varioGPS = completeData.varioGPS.value,
        varioComplementary = completeData.varioComplementary.value,
        realIgcVario = completeData.realIgcVario?.value,
        baselineVario = completeData.baselineVario.value,
        baselineDisplayVario = completeData.baselineDisplayVario.value,
        baselineVarioValid = completeData.baselineVarioValid,
        bruttoAverage30s = completeData.bruttoAverage30s.value,
        bruttoAverage30sValid = completeData.bruttoAverage30sValid,
        nettoAverage30s = completeData.nettoAverage30s.value,
        nettoAverage30sValid = completeData.nettoAverage30sValid,
        varioSource = completeData.varioSource,
        varioValid = completeData.varioValid,
        isCircling = completeData.isCircling,
        thermalAverageValid = completeData.thermalAverageValid,

        // Metadata
        flightTime = flightTime,
        timestamp = completeData.timestamp,
        lastUpdateTime = lastUpdateTimeMillis,
        calculationSource = completeData.dataQuality,
        airspeedSource = completeData.airspeedSource,
        tasValid = completeData.tasValid,
        teAltitude = completeData.teAltitude.value,
        teVario = completeData.teVario?.value,
        macCready = completeData.macCready,
        macCreadyRisk = completeData.macCreadyRisk,
        headingDeg = headingSolution.bearingDeg,
        headingValid = headingSolution.isValid,
        headingSource = headingSolution.source.name,
        levoNetto = completeData.levoNetto.value,
        levoNettoValid = completeData.levoNettoValid,
        levoNettoHasWind = completeData.levoNettoHasWind,
        levoNettoHasPolar = completeData.levoNettoHasPolar,
        levoNettoConfidence = completeData.levoNettoConfidence,
        autoMacCready = completeData.autoMacCready,
        autoMacCreadyValid = completeData.autoMacCreadyValid,
        speedToFlyIas = completeData.speedToFlyIas.value,
        speedToFlyDelta = completeData.speedToFlyDelta.value,
        speedToFlyValid = completeData.speedToFlyValid,
        speedToFlyMcSourceAuto = completeData.speedToFlyMcSourceAuto,
        speedToFlyHasPolar = completeData.speedToFlyHasPolar,
        requiredGlideRatio = glideSolution?.requiredGlideRatio ?: Double.NaN,
        arrivalHeightM = glideSolution?.arrivalHeightMeters ?: Double.NaN,
        requiredAltitudeM = glideSolution?.requiredAltitudeMeters ?: Double.NaN,
        arrivalHeightMc0M = glideSolution?.arrivalHeightMc0Meters ?: Double.NaN,
        taskFinishDistanceRemainingM = glideSolution?.distanceRemainingMeters ?: Double.NaN,
        glideSolutionValid = glideSolution?.valid ?: false,
        glideDegraded = glideSolution?.degraded ?: false,
        glideDegradedReason = glideSolution?.degradedReason?.name.orEmpty(),
        glideInvalidReason = glideSolution?.invalidReason?.name.orEmpty(),
        waypointDistanceMeters = waypointNavigation?.distanceMeters ?: Double.NaN,
        waypointValid = waypointNavigation?.valid ?: false,
        waypointBearingTrueDegrees = waypointNavigation?.bearingTrueDegrees ?: Double.NaN,
        waypointInvalidReason = waypointNavigation?.invalidReason?.name.orEmpty(),
        waypointEtaEpochMillis = waypointNavigation?.etaEpochMillis ?: 0L,
        waypointEtaValid = waypointNavigation?.etaValid ?: false,
        waypointEtaSource = waypointNavigation?.etaSource?.name.orEmpty(),
        waypointEtaInvalidReason = waypointNavigation?.etaInvalidReason?.name.orEmpty(),
        taskSpeedMs = taskPerformance?.taskSpeedMs ?: Double.NaN,
        taskSpeedValid = taskPerformance?.taskSpeedValid ?: false,
        taskSpeedInvalidReason = taskPerformance?.taskSpeedInvalidReason?.name.orEmpty(),
        taskDistanceMeters = taskPerformance?.taskDistanceMeters ?: Double.NaN,
        taskDistanceValid = taskPerformance?.taskDistanceValid ?: false,
        taskDistanceInvalidReason = taskPerformance?.taskDistanceInvalidReason?.name.orEmpty(),
        taskRemainingDistanceMeters = taskPerformance?.taskRemainingDistanceMeters ?: Double.NaN,
        taskRemainingDistanceValid = taskPerformance?.taskRemainingDistanceValid ?: false,
        taskRemainingDistanceInvalidReason = taskPerformance?.taskRemainingDistanceInvalidReason?.name.orEmpty(),
        taskRemainingTimeMillis = taskPerformance?.taskRemainingTimeMillis ?: 0L,
        taskRemainingTimeValid = taskPerformance?.taskRemainingTimeValid ?: false,
        taskRemainingTimeBasis = taskPerformance?.taskRemainingTimeBasis?.name.orEmpty(),
        taskRemainingTimeInvalidReason = taskPerformance?.taskRemainingTimeInvalidReason?.name.orEmpty(),
        startAltitudeMeters = taskPerformance?.startAltitudeMeters ?: Double.NaN,
        startAltitudeValid = taskPerformance?.startAltitudeValid ?: false,
        startAltitudeInvalidReason = taskPerformance?.startAltitudeInvalidReason?.name.orEmpty(),
        hawkVarioSmoothedMps = hawkVarioUiState.varioSmoothedMps?.toDouble(),
        hawkVarioRawMps = hawkVarioUiState.varioRawMps?.toDouble(),
        hawkAccelOk = hawkVarioUiState.accelOk,
        hawkBaroOk = hawkVarioUiState.baroOk,
        hawkConfidenceCode = hawkVarioUiState.confidence.code
    )
}

