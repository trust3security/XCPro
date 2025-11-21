package com.example.xcpro.flightdata

import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.sensors.BaroData
import com.example.xcpro.sensors.CompassData
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.sensors.domain.FlightMetricsResult

/**
 * Maps pure domain flight metrics to the UI-facing [CompleteFlightData] model.
 * All smoothing/clamping must happen upstream; this class only performs value wiring and units.
 */
class FlightDisplayMapper {

    fun map(snapshot: FlightDisplaySnapshot): CompleteFlightData {
        val metrics = snapshot.metrics
        val varioResults = snapshot.varioResults
        val replayIgcVario = snapshot.replayIgcVario

        return CompleteFlightData(
            gps = snapshot.gps,
            baro = snapshot.baro,
            compass = snapshot.compass,
            baroAltitude = AltitudeM(metrics.baroAltitude),
            qnh = PressureHpa(metrics.qnh),
            isQNHCalibrated = metrics.isQnhCalibrated,
            verticalSpeed = VerticalSpeedMs(metrics.verticalSpeed),
            bruttoVario = VerticalSpeedMs(metrics.bruttoVario),
            displayVario = VerticalSpeedMs(metrics.displayVario),
            bruttoAverage30s = VerticalSpeedMs(metrics.bruttoAverage30s),
            nettoAverage30s = VerticalSpeedMs(metrics.nettoAverage30s),
            varioSource = metrics.varioSource,
            varioValid = metrics.varioValid,
            pressureAltitude = AltitudeM(metrics.pressureAltitude),
            baroGpsDelta = metrics.baroGpsDelta?.let { AltitudeM(it) },
            baroConfidence = metrics.baroConfidence,
            qnhCalibrationAgeSeconds = metrics.qnhCalibrationAgeSeconds,
            agl = AltitudeM(snapshot.aglMeters),
            windSpeed = SpeedMs(metrics.windSpeedValue.toDouble()),
            windDirection = metrics.windDirectionFrom,
            windHeadwind = SpeedMs(metrics.windHeadwind),
            windCrosswind = SpeedMs(metrics.windCrosswind),
            windQuality = metrics.windQuality,
            windSource = metrics.windSource,
            thermalAverage = VerticalSpeedMs(metrics.bruttoAverage30s),
            thermalAverageCircle = VerticalSpeedMs(metrics.thermalAverageCircle.toDouble()),
            thermalAverageTotal = VerticalSpeedMs(metrics.thermalAverageTotal.toDouble()),
            thermalGain = AltitudeM(metrics.thermalGain),
            currentLD = metrics.calculatedLD,
            netto = VerticalSpeedMs(metrics.netto.toDouble()),
            displayNetto = VerticalSpeedMs(metrics.displayNetto),
            nettoValid = metrics.nettoValid,
            trueAirspeed = SpeedMs(metrics.trueAirspeedMs),
            indicatedAirspeed = SpeedMs(metrics.indicatedAirspeedMs),
            airspeedSource = metrics.airspeedSourceLabel,
            tasValid = metrics.tasValid,
            varioOptimized = VerticalSpeedMs(varioResults["optimized"] ?: 0.0),
            varioLegacy = VerticalSpeedMs(varioResults["legacy"] ?: 0.0),
            varioRaw = VerticalSpeedMs(varioResults["raw"] ?: 0.0),
            varioGPS = VerticalSpeedMs(varioResults["gps"] ?: 0.0),
            varioComplementary = VerticalSpeedMs(varioResults["complementary"] ?: 0.0),
            realIgcVario = replayIgcVario?.let { VerticalSpeedMs(it) },
            teAltitude = AltitudeM(metrics.teAltitude),
            macCready = snapshot.macCready,
            macCreadyRisk = snapshot.macCreadyRisk,
            timestamp = snapshot.timestamp,
            dataQuality = snapshot.dataQuality
        )
    }
}

data class FlightDisplaySnapshot(
    val gps: GPSData,
    val baro: BaroData?,
    val compass: CompassData?,
    val metrics: FlightMetricsResult,
    val aglMeters: Double,
    val varioResults: Map<String, Double>,
    val replayIgcVario: Double?,
    val dataQuality: String,
    val timestamp: Long,
    val macCready: Double,
    val macCreadyRisk: Double
)
