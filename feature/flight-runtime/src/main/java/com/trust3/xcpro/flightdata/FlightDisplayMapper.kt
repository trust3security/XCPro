package com.trust3.xcpro.flightdata

import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.PressureHpa
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.common.units.VerticalSpeedMs
import com.trust3.xcpro.sensors.BaroData
import com.trust3.xcpro.sensors.CompassData
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.sensors.domain.FlightMetricsResult

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
            displayNeedleVario = VerticalSpeedMs(metrics.displayNeedleVario),
            displayNeedleVarioFast = VerticalSpeedMs(metrics.displayNeedleVarioFast),
            audioVario = VerticalSpeedMs(snapshot.audioVario),
            baselineVario = VerticalSpeedMs(metrics.baselineVario),
            baselineDisplayVario = VerticalSpeedMs(metrics.displayBaselineVario),
            baselineVarioValid = metrics.baselineVarioValid,
            bruttoAverage30s = VerticalSpeedMs(metrics.bruttoAverage30s),
            bruttoAverage30sValid = metrics.bruttoAverage30sValid,
            nettoAverage30s = VerticalSpeedMs(metrics.nettoAverage30s),
            nettoAverage30sValid = metrics.nettoAverage30sValid,
            varioSource = metrics.varioSource,
            varioValid = metrics.varioValid,
            teVario = metrics.teVario?.let { VerticalSpeedMs(it) },
            pressureAltitude = AltitudeM(metrics.pressureAltitude),
            navAltitude = AltitudeM(metrics.navAltitude),
            baroGpsDelta = metrics.baroGpsDelta?.let { AltitudeM(it) },
            baroConfidence = metrics.baroConfidence,
            qnhCalibrationAgeSeconds = metrics.qnhCalibrationAgeSeconds,
            agl = AltitudeM(snapshot.aglMeters),
            aglTimestampMonoMs = snapshot.aglUpdatedAtMonoMs,
            thermalAverage = VerticalSpeedMs(metrics.thermalAverage30s.toDouble()),
            thermalAverageCircle = VerticalSpeedMs(metrics.thermalAverageCircle.toDouble()),
            thermalAverageTotal = VerticalSpeedMs(metrics.thermalAverageTotal.toDouble()),
            thermalGain = AltitudeM(metrics.thermalGain),
            thermalGainValid = metrics.thermalGainValid,
            currentThermalLiftRate = VerticalSpeedMs(metrics.currentThermalLiftRate),
            currentThermalValid = metrics.currentThermalValid,
            currentLD = metrics.calculatedLD,
            currentLDValid = metrics.currentLDValid,
            currentLDAir = metrics.currentLDAir,
            currentLDAirValid = metrics.currentLDAirValid,
            polarLdCurrentSpeed = metrics.polarLdCurrentSpeed,
            polarLdCurrentSpeedValid = metrics.polarLdCurrentSpeedValid,
            polarBestLd = metrics.polarBestLd,
            polarBestLdValid = metrics.polarBestLdValid,
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
            isCircling = metrics.isCircling,
            isTurning = metrics.isTurning,
            thermalAverageValid = metrics.thermalAverage30sValid,
            timestamp = snapshot.timestamp,
            dataQuality = snapshot.dataQuality,
            levoNetto = VerticalSpeedMs(metrics.levoNettoMs),
            levoNettoValid = metrics.levoNettoValid,
            levoNettoHasWind = metrics.levoNettoHasWind,
            levoNettoHasPolar = metrics.levoNettoHasPolar,
            levoNettoConfidence = metrics.levoNettoConfidence,
            autoMacCready = metrics.autoMcMs,
            autoMacCreadyValid = metrics.autoMcValid,
            speedToFlyIas = SpeedMs(metrics.speedToFlyIasMs),
            speedToFlyDelta = SpeedMs(metrics.speedToFlyDeltaMs),
            speedToFlyValid = metrics.speedToFlyValid,
            speedToFlyMcSourceAuto = metrics.speedToFlyMcSourceAuto,
            speedToFlyHasPolar = metrics.speedToFlyHasPolar
        )
    }
}

data class FlightDisplaySnapshot(
    val gps: GPSData,
    val baro: BaroData?,
    val compass: CompassData?,
    val metrics: FlightMetricsResult,
    val aglMeters: Double,
    val aglUpdatedAtMonoMs: Long = 0L,
    val varioResults: Map<String, Double>,
    val replayIgcVario: Double?,
    val audioVario: Double,
    val dataQuality: String,
    val timestamp: Long,
    val macCready: Double,
    val macCreadyRisk: Double
)
