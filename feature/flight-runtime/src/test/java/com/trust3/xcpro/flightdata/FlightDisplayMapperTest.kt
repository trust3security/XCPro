package com.trust3.xcpro.flightdata

import com.trust3.xcpro.core.flight.calculations.ConfidenceLevel
import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.sensors.domain.FlightMetricsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightDisplayMapperTest {

    @Test
    fun map_wires_metrics_and_snapshot_fields_without_transforming_them() {
        val metrics = metricsResult(
            verticalSpeed = 1.2,
            displayVario = 1.1,
            displayNeedleVario = 1.0,
            displayNeedleVarioFast = 1.4,
            displayNetto = 0.8,
            displayBaselineVario = 0.6,
            navAltitude = 1325.0,
            nettoAverage30sValid = true,
            levoNettoMs = 0.55,
            autoMcMs = 1.7,
            autoMcValid = true,
            speedToFlyIasMs = 33.0,
            speedToFlyDeltaMs = 2.5,
            speedToFlyValid = true,
            speedToFlyMcSourceAuto = true,
            speedToFlyHasPolar = true,
            calculatedLD = 31f,
            currentLDValid = true,
            currentLDAir = 13.5f,
            currentLDAirValid = true,
            polarLdCurrentSpeed = 27.5f,
            polarLdCurrentSpeedValid = true,
            polarBestLd = 41.5f,
            polarBestLdValid = true
        )

        val snapshot = FlightDisplaySnapshot(
            gps = gpsSample(1_000L),
            baro = null,
            compass = null,
            metrics = metrics,
            aglMeters = 123.4,
            aglUpdatedAtMonoMs = 9_876L,
            varioResults = mapOf(
                "optimized" to 1.1,
                "legacy" to 0.9,
                "raw" to 1.4,
                "gps" to 0.8,
                "complementary" to 1.0
            ),
            replayIgcVario = 2.25,
            condorVario = 3.45,
            audioVario = 1.75,
            dataQuality = "TEST",
            timestamp = 12_345L,
            macCready = 1.5,
            macCreadyRisk = 0.25
        )

        val mapped = FlightDisplayMapper().map(snapshot)

        assertEquals(metrics.verticalSpeed, mapped.verticalSpeed.value, 1e-6)
        assertEquals(metrics.displayVario, mapped.displayVario.value, 1e-6)
        assertEquals(metrics.displayNeedleVario, mapped.displayNeedleVario.value, 1e-6)
        assertEquals(metrics.displayNeedleVarioFast, mapped.displayNeedleVarioFast.value, 1e-6)
        assertEquals(metrics.displayNetto, mapped.displayNetto.value, 1e-6)
        assertEquals(metrics.nettoValid, mapped.nettoValid)
        assertEquals(metrics.displayBaselineVario, mapped.baselineDisplayVario.value, 1e-6)
        assertEquals(metrics.navAltitude, mapped.navAltitude.value, 1e-6)
        assertEquals(metrics.levoNettoMs, mapped.levoNetto.value, 1e-6)
        assertEquals(metrics.autoMcMs, mapped.autoMacCready, 1e-6)
        assertEquals(metrics.autoMcValid, mapped.autoMacCreadyValid)
        assertEquals(metrics.speedToFlyIasMs, mapped.speedToFlyIas.value, 1e-6)
        assertEquals(metrics.speedToFlyDeltaMs, mapped.speedToFlyDelta.value, 1e-6)
        assertEquals(metrics.speedToFlyValid, mapped.speedToFlyValid)
        assertEquals(metrics.speedToFlyMcSourceAuto, mapped.speedToFlyMcSourceAuto)
        assertEquals(metrics.speedToFlyHasPolar, mapped.speedToFlyHasPolar)
        assertEquals(metrics.nettoAverage30sValid, mapped.nettoAverage30sValid)
        assertEquals(1.75, mapped.audioVario.value, 1e-6)
        assertEquals(2.25, mapped.realIgcVario!!.value, 1e-6)
        assertEquals(3.45, mapped.condorVario!!.value, 1e-6)
        assertEquals(1.1, mapped.varioOptimized.value, 1e-6)
        assertEquals(0.9, mapped.varioLegacy.value, 1e-6)
        assertEquals(1.4, mapped.varioRaw.value, 1e-6)
        assertEquals(0.8, mapped.varioGPS.value, 1e-6)
        assertEquals(1.0, mapped.varioComplementary.value, 1e-6)
        assertEquals(123.4, mapped.agl.value, 1e-6)
        assertEquals(9_876L, mapped.aglTimestampMonoMs)
        assertEquals(1.5, mapped.macCready, 1e-6)
        assertEquals(0.25, mapped.macCreadyRisk, 1e-6)
        assertEquals(metrics.calculatedLD, mapped.currentLD, 1e-6f)
        assertEquals(metrics.currentLDValid, mapped.currentLDValid)
        assertEquals(metrics.currentLDAir, mapped.currentLDAir, 1e-6f)
        assertEquals(metrics.currentLDAirValid, mapped.currentLDAirValid)
        assertEquals(metrics.polarLdCurrentSpeed, mapped.polarLdCurrentSpeed, 1e-6f)
        assertEquals(metrics.polarLdCurrentSpeedValid, mapped.polarLdCurrentSpeedValid)
        assertEquals(metrics.polarBestLd, mapped.polarBestLd, 1e-6f)
        assertEquals(metrics.polarBestLdValid, mapped.polarBestLdValid)
        assertEquals(12_345L, mapped.timestamp)
        assertEquals("TEST", mapped.dataQuality)
    }

    @Test
    fun map_preserves_null_replay_igc_vario() {
        val mapped = FlightDisplayMapper().map(
            FlightDisplaySnapshot(
                gps = gpsSample(2_000L),
                baro = null,
                compass = null,
                metrics = metricsResult(verticalSpeed = 0.4),
                aglMeters = 0.0,
                varioResults = emptyMap(),
                replayIgcVario = null,
                audioVario = 0.0,
                dataQuality = "TEST",
                timestamp = 2_000L,
                macCready = 0.0,
                macCreadyRisk = 0.0
            )
        )

        assertNull(mapped.realIgcVario)
    }

    private fun gpsSample(timestampMillis: Long): GPSData = GPSData(
        position = GeoPoint(47.0, 13.0),
        altitude = AltitudeM(1000.0),
        speed = SpeedMs(20.0),
        bearing = 90.0,
        accuracy = 5f,
        timestamp = timestampMillis,
        monotonicTimestampMillis = timestampMillis
    )

    private fun metricsResult(
        verticalSpeed: Double,
        displayVario: Double = verticalSpeed,
        displayNeedleVario: Double = verticalSpeed,
        displayNeedleVarioFast: Double = verticalSpeed,
        displayNetto: Double = verticalSpeed,
        displayBaselineVario: Double = 0.0,
        navAltitude: Double = 1100.0,
        nettoAverage30sValid: Boolean = false,
        levoNettoMs: Double = 0.0,
        autoMcMs: Double = 0.0,
        autoMcValid: Boolean = false,
        speedToFlyIasMs: Double = 0.0,
        speedToFlyDeltaMs: Double = 0.0,
        speedToFlyValid: Boolean = false,
        speedToFlyMcSourceAuto: Boolean = false,
        speedToFlyHasPolar: Boolean = false,
        calculatedLD: Float = 0f,
        currentLDValid: Boolean = false,
        currentLDAir: Float = 0f,
        currentLDAirValid: Boolean = false,
        polarLdCurrentSpeed: Float = 0f,
        polarLdCurrentSpeedValid: Boolean = false,
        polarBestLd: Float = 0f,
        polarBestLdValid: Boolean = false
    ): FlightMetricsResult = FlightMetricsResult(
        baroAltitude = 1000.0,
        qnh = 1013.25,
        isQnhCalibrated = false,
        pressureAltitude = 995.0,
        baroGpsDelta = null,
        baroConfidence = ConfidenceLevel.HIGH,
        qnhCalibrationAgeSeconds = 0L,
        bruttoVario = verticalSpeed,
        verticalSpeed = verticalSpeed,
        varioSource = "TEST",
        varioValid = true,
        teVario = null,
        navAltitude = navAltitude,
        bruttoAverage30s = verticalSpeed,
        bruttoAverage30sValid = true,
        nettoAverage30s = verticalSpeed,
        nettoAverage30sValid = nettoAverage30sValid,
        displayVario = displayVario,
        displayNeedleVario = displayNeedleVario,
        displayNeedleVarioFast = displayNeedleVarioFast,
        displayBaselineVario = displayBaselineVario,
        displayNetto = displayNetto,
        netto = verticalSpeed.toFloat(),
        nettoValid = true,
        indicatedAirspeedMs = 25.0,
        trueAirspeedMs = 27.0,
        airspeedSourceLabel = "TEST",
        tasValid = true,
        baselineVario = 0.0,
        baselineVarioValid = false,
        thermalAverageCircle = 0f,
        thermalAverage30s = 0f,
        thermalAverageTotal = 0f,
        thermalGain = 0.0,
        thermalGainValid = false,
        currentThermalLiftRate = 0.0,
        currentThermalValid = false,
        calculatedLD = calculatedLD,
        currentLDValid = currentLDValid,
        currentLDAir = currentLDAir,
        currentLDAirValid = currentLDAirValid,
        polarLdCurrentSpeed = polarLdCurrentSpeed,
        polarLdCurrentSpeedValid = polarLdCurrentSpeedValid,
        polarBestLd = polarBestLd,
        polarBestLdValid = polarBestLdValid,
        teAltitude = 0.0,
        isCircling = false,
        thermalAverage30sValid = false,
        levoNettoMs = levoNettoMs,
        levoNettoValid = false,
        levoNettoHasWind = false,
        levoNettoHasPolar = false,
        levoNettoConfidence = 0.0,
        autoMcMs = autoMcMs,
        autoMcValid = autoMcValid,
        speedToFlyIasMs = speedToFlyIasMs,
        speedToFlyDeltaMs = speedToFlyDeltaMs,
        speedToFlyValid = speedToFlyValid,
        speedToFlyMcSourceAuto = speedToFlyMcSourceAuto,
        speedToFlyHasPolar = speedToFlyHasPolar
    )
}
