package com.example.xcpro.sensors

import com.example.dfcards.filters.ModernVarioResult
import com.example.xcpro.audio.AudioMode
import com.example.xcpro.audio.VarioFrequencyMapper
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.UnitsConverter
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.common.units.VerticalSpeedUnit
import com.example.xcpro.flightdata.FlightDisplayMapper
import com.example.xcpro.flightdata.FlightDisplaySnapshot
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.convertToRealTimeFlightData
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.sensors.domain.CalculateFlightMetricsUseCase
import com.example.xcpro.sensors.domain.FlightMetricsRequest
import com.example.xcpro.sensors.domain.WindEstimator
import com.example.xcpro.common.flight.FlightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private fun gpsSample(timeMs: Long, altitude: Double) = GPSData(
    position = GeoPoint(0.0, 0.0),
    altitude = AltitudeM(altitude),
    speed = SpeedMs(20.0),
    bearing = 0.0,
    accuracy = 5f,
    timestamp = timeMs
)

private fun varioSample(vs: Double, alt: Double) = ModernVarioResult(
    altitude = alt,
    verticalSpeed = vs,
    acceleration = 0.0,
    confidence = 0.8
)

private fun newUseCase(): CalculateFlightMetricsUseCase {
    val sink = mock<StillAirSinkProvider> {
        on { sinkAtSpeed(any()) }.thenReturn(0.0)
        on { iasBoundsMs() }.thenReturn(null)
    }
    val helpers = mock<FlightCalculationHelpers>()
    whenever(helpers.calculateNetto(any(), anyOrNull(), any(), any())).thenReturn(
        FlightCalculationHelpers.NettoComputation(0.0, true)
    )
    whenever(helpers.calculateCurrentLD(any(), any(), any())).thenReturn(0f)
    whenever(helpers.updateThermalState(any(), any(), any(), any(), any())).thenAnswer { }
    whenever(helpers.updateAGL(any(), any(), any())).thenAnswer { }
    whenever(helpers.recordLocationSample(any(), any())).thenAnswer { }
    whenever(helpers.thermalAverageCurrent).thenReturn(0f)
    whenever(helpers.thermalAverageTotal).thenReturn(0f)
    whenever(helpers.thermalGainCurrent).thenReturn(0.0)
    whenever(helpers.thermalGainValid).thenReturn(false)
    whenever(helpers.currentThermalLiftRate).thenReturn(0.0)
    whenever(helpers.currentThermalValid).thenReturn(false)

    return CalculateFlightMetricsUseCase(
        flightHelpers = helpers,
        sinkProvider = sink,
        windEstimator = WindEstimator()
    )
}

class LevoVarioPipelineTest {

    @Test
    fun constant_one_knot_climb_reaches_ui_and_audio() {
        // 1 kt climb rate (vertical speed) expressed in m/s for the pipeline.
        val climbMs = UnitsConverter.knotsToMs(1.0)
        val useCase = newUseCase()
        var time = 0L
        var altitude = 1000.0
        var metricsResult = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(time, altitude),
                currentTimeMillis = time,
                wallTimeMillis = time,
                gpsTimestampMillis = time,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(climbMs, altitude),
                varioGpsValue = climbMs,
                baroResult = null,
                windState = null,
                varioValidUntil = time + 500,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )

        // Feed a full minute of steady climb to let smoothing settle.
        repeat(60) {
            metricsResult = useCase.execute(
                FlightMetricsRequest(
                    gps = gpsSample(time, altitude),
                    currentTimeMillis = time,
                    wallTimeMillis = time,
                    gpsTimestampMillis = time,
                    deltaTimeSeconds = 1.0,
                    varioResult = varioSample(climbMs, altitude),
                    varioGpsValue = climbMs,
                    baroResult = null,
                    windState = null,
                    varioValidUntil = time + 500,
                    isFlying = true,
                    macCreadySetting = 0.0,
                    autoMcEnabled = false,
                    flightMode = FlightMode.CRUISE
                )
            )
            time += 1_000
            altitude += climbMs
        }

        val displaySnapshot = FlightDisplaySnapshot(
            gps = gpsSample(time, altitude),
            baro = null,
            compass = null,
            metrics = metricsResult,
            aglMeters = 0.0,
            varioResults = emptyMap(),
            replayIgcVario = null,
            audioVario = 0.0,
            dataQuality = "TEST",
            timestamp = time,
            macCready = 0.0,
            macCreadyRisk = 0.0
        )
        val complete = FlightDisplayMapper().map(displaySnapshot)
        val realtime = convertToRealTimeFlightData(
            completeData = complete,
            windState = null,
            isFlying = true
        )

        assertTrue(realtime.varioValid)
        assertTrue(complete.displayNeedleVario.value <= complete.bruttoVario.value + 1e-6)
        assertTrue(complete.displayNeedleVario.value >= complete.bruttoVario.value * 0.95)

        val formatted = UnitsFormatter.verticalSpeed(
            VerticalSpeedMs(realtime.displayVario),
            UnitsPreferences(verticalSpeed = VerticalSpeedUnit.KNOTS)
        )
        assertEquals("+1.0 kt", formatted.text)

        val audioParams = VarioFrequencyMapper().mapVerticalSpeed(climbMs)
        assertEquals(AudioMode.BEEPING, audioParams.mode)
        assertTrue(audioParams.frequencyHz > 0.0)
    }
}
