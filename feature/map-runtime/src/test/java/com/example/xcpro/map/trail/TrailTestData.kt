package com.example.xcpro.map.trail

import com.example.dfcards.calculations.ConfidenceLevel
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.GPSData

internal fun defaultGps(
    latitude: Double = 46.0,
    longitude: Double = 7.0,
    altitudeMeters: Double = 1000.0,
    speedMs: Double = 0.0,
    bearingDeg: Double = 0.0,
    accuracyMeters: Float = 5f,
    timestampMillis: Long = 1000L,
    monotonicTimestampMillis: Long = 0L
): GPSData = GPSData(
    position = GeoPoint(latitude, longitude),
    altitude = AltitudeM(altitudeMeters),
    speed = SpeedMs(speedMs),
    bearing = bearingDeg,
    accuracy = accuracyMeters,
    timestamp = timestampMillis,
    monotonicTimestampMillis = monotonicTimestampMillis
)

internal fun buildCompleteFlightData(
    gps: GPSData? = defaultGps(),
    baroAltitudeMeters: Double = 1000.0,
    verticalSpeedMs: Double = 0.0,
    displayVarioMs: Double = 0.0,
    nettoMs: Double = 0.0,
    displayNettoMs: Double = 0.0,
    nettoValid: Boolean = false,
    baselineDisplayVarioMs: Double = 0.0,
    baselineVarioValid: Boolean = false,
    realIgcVarioMs: Double? = null,
    isCircling: Boolean = false,
    currentThermalValid: Boolean = false,
    thermalAverageValid: Boolean = false,
    airspeedSource: String = "UNKNOWN",
    timestampMillis: Long = 1000L
): CompleteFlightData {
    return CompleteFlightData(
        gps = gps,
        baro = null,
        compass = null,
        baroAltitude = AltitudeM(baroAltitudeMeters),
        qnh = PressureHpa(1013.25),
        isQNHCalibrated = false,
        verticalSpeed = VerticalSpeedMs(verticalSpeedMs),
        displayVario = VerticalSpeedMs(displayVarioMs),
        displayNeedleVario = VerticalSpeedMs(0.0),
        displayNeedleVarioFast = VerticalSpeedMs(0.0),
        audioVario = VerticalSpeedMs(0.0),
        baselineVario = VerticalSpeedMs(0.0),
        baselineDisplayVario = VerticalSpeedMs(baselineDisplayVarioMs),
        baselineVarioValid = baselineVarioValid,
        bruttoVario = VerticalSpeedMs(0.0),
        bruttoAverage30s = VerticalSpeedMs(0.0),
        bruttoAverage30sValid = false,
        nettoAverage30s = VerticalSpeedMs(0.0),
        varioSource = "TEST",
        varioValid = true,
        pressureAltitude = AltitudeM(0.0),
        baroGpsDelta = null,
        baroConfidence = ConfidenceLevel.LOW,
        qnhCalibrationAgeSeconds = -1,
        agl = AltitudeM(0.0),
        thermalAverage = VerticalSpeedMs(0.0),
        thermalAverageCircle = VerticalSpeedMs(0.0),
        thermalAverageTotal = VerticalSpeedMs(0.0),
        thermalGain = AltitudeM(0.0),
        thermalGainValid = false,
        currentThermalLiftRate = VerticalSpeedMs(0.0),
        currentThermalValid = currentThermalValid,
        currentLD = 0f,
        netto = VerticalSpeedMs(nettoMs),
        displayNetto = VerticalSpeedMs(displayNettoMs),
        nettoValid = nettoValid,
        trueAirspeed = SpeedMs(0.0),
        indicatedAirspeed = SpeedMs(0.0),
        airspeedSource = airspeedSource,
        tasValid = true,
        varioOptimized = VerticalSpeedMs(0.0),
        varioLegacy = VerticalSpeedMs(0.0),
        varioRaw = VerticalSpeedMs(0.0),
        varioGPS = VerticalSpeedMs(0.0),
        varioComplementary = VerticalSpeedMs(0.0),
        realIgcVario = realIgcVarioMs?.let { VerticalSpeedMs(it) },
        teAltitude = AltitudeM(0.0),
        macCready = 0.0,
        macCreadyRisk = 0.0,
        isCircling = isCircling,
        thermalAverageValid = thermalAverageValid,
        timestamp = timestampMillis,
        dataQuality = "TEST"
    )
}
