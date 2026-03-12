package com.example.xcpro.flightdata

import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.sensors.domain.FlightMetricsRequest
import com.example.xcpro.sensors.domain.gpsSample
import com.example.xcpro.sensors.domain.newUseCase
import com.example.xcpro.sensors.domain.varioSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightDisplayMapperTest {

    @Test
    fun map_wires_metrics_and_snapshot_fields_without_transforming_them() {
        val metrics = newUseCase().execute(
            FlightMetricsRequest(
                gps = gpsSample(1_000L),
                currentTimeMillis = 1_000L,
                wallTimeMillis = 1_000L,
                gpsTimestampMillis = 1_000L,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(1.2, 1_000.0),
                varioGpsValue = 1.2,
                baroResult = null,
                windState = null,
                varioValidUntil = 2_000L,
                isFlying = true,
                macCreadySetting = 1.5,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
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
        assertEquals(metrics.levoNettoMs, mapped.levoNetto.value, 1e-6)
        assertEquals(metrics.autoMcMs, mapped.autoMacCready, 1e-6)
        assertEquals(metrics.autoMcValid, mapped.autoMacCreadyValid)
        assertEquals(metrics.speedToFlyIasMs, mapped.speedToFlyIas.value, 1e-6)
        assertEquals(metrics.speedToFlyDeltaMs, mapped.speedToFlyDelta.value, 1e-6)
        assertEquals(metrics.speedToFlyValid, mapped.speedToFlyValid)
        assertEquals(metrics.speedToFlyMcSourceAuto, mapped.speedToFlyMcSourceAuto)
        assertEquals(metrics.speedToFlyHasPolar, mapped.speedToFlyHasPolar)
        assertEquals(1.75, mapped.audioVario.value, 1e-6)
        assertEquals(2.25, mapped.realIgcVario!!.value, 1e-6)
        assertEquals(1.1, mapped.varioOptimized.value, 1e-6)
        assertEquals(0.9, mapped.varioLegacy.value, 1e-6)
        assertEquals(1.4, mapped.varioRaw.value, 1e-6)
        assertEquals(0.8, mapped.varioGPS.value, 1e-6)
        assertEquals(1.0, mapped.varioComplementary.value, 1e-6)
        assertEquals(123.4, mapped.agl.value, 1e-6)
        assertEquals(9_876L, mapped.aglTimestampMonoMs)
        assertEquals(1.5, mapped.macCready, 1e-6)
        assertEquals(0.25, mapped.macCreadyRisk, 1e-6)
        assertEquals(metrics.polarLdCurrentSpeed, mapped.polarLdCurrentSpeed, 1e-6f)
        assertEquals(metrics.polarBestLd, mapped.polarBestLd, 1e-6f)
        assertEquals(12_345L, mapped.timestamp)
        assertEquals("TEST", mapped.dataQuality)
    }

    @Test
    fun map_preserves_null_replay_igc_vario() {
        val metrics = newUseCase().execute(
            FlightMetricsRequest(
                gps = gpsSample(2_000L),
                currentTimeMillis = 2_000L,
                wallTimeMillis = 2_000L,
                gpsTimestampMillis = 2_000L,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(0.4, 1_100.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = null,
                varioValidUntil = 3_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )

        val mapped = FlightDisplayMapper().map(
            FlightDisplaySnapshot(
                gps = gpsSample(2_000L),
                baro = null,
                compass = null,
                metrics = metrics,
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
}
