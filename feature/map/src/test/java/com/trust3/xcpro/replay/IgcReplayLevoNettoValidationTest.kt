package com.trust3.xcpro.replay

import com.trust3.xcpro.core.flight.filters.ModernVarioResult
import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.glider.SpeedBoundsMs
import com.trust3.xcpro.glider.StillAirSinkProvider
import com.trust3.xcpro.sensors.FlightCalculationHelpers
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.sensors.domain.CalculateFlightMetricsUseCase
import com.trust3.xcpro.sensors.domain.FlightMetricsRequest
import com.trust3.xcpro.sensors.domain.WindEstimator
import com.trust3.xcpro.weather.wind.model.WindSource
import com.trust3.xcpro.weather.wind.model.WindState
import com.trust3.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.math.abs
import kotlin.math.max

class IgcReplayLevoNettoValidationTest {

    @Test
    fun replayDemoIgcProducesBoundedLevoNetto() {
        val resource = javaClass.classLoader?.getResourceAsStream(REPLAY_RESOURCE)
            ?: error("Missing replay fixture: $REPLAY_RESOURCE")
        val parser = IgcParser(FakeClock(wallMs = 0L))
        val log = parser.parse(resource)
        assertTrue("Expected IGC points", log.points.isNotEmpty())

        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double = 0.0
            override fun iasBoundsMs(): SpeedBoundsMs = SpeedBoundsMs(minMs = 12.0, maxMs = 80.0)
        }

        val helpers = mock<FlightCalculationHelpers>()
        whenever(helpers.calculateNetto(any(), anyOrNull(), any(), any())).thenReturn(
            FlightCalculationHelpers.NettoComputation(0.0, true)
        )
        whenever(helpers.calculateTotalEnergy(any(), any(), any(), any())).thenAnswer { invocation ->
            invocation.getArgument<Double>(0)
        }
        whenever(helpers.calculateCurrentLD(any(), any(), any())).thenReturn(0f)
        whenever(helpers.updateThermalState(any(), any(), any(), any(), any())).thenAnswer { }
        whenever(helpers.updateAGL(any(), any(), any())).thenAnswer { }
        whenever(helpers.recordLocationSample(any(), any())).thenAnswer { }
        whenever(helpers.thermalAverageCurrent).thenReturn(0f)
        whenever(helpers.thermalAverageTotal).thenReturn(0f)
        whenever(helpers.thermalGainCurrent).thenReturn(0.0)
        whenever(helpers.thermalGainValid).thenReturn(false)

        val useCase = CalculateFlightMetricsUseCase(
            flightHelpers = helpers,
            sinkProvider = sinkProvider,
            windEstimator = WindEstimator()
        )

        val windState = WindState(
            vector = WindVector(0.0, 0.0),
            source = WindSource.MANUAL,
            quality = 1,
            stale = false,
            confidence = 1.0
        )

        val points = IgcReplayMath.densifyPoints(log.points)
        var prev: IgcPoint? = null
        var validCount = 0
        var maxAbs = 0.0

        points.forEach { point ->
            val movement = IgcReplayMath.groundVector(point, prev)
            val vario = IgcReplayMath.verticalSpeed(point, prev)
            val currentTime = point.timestampMillis
            val dtSeconds = if (prev == null) {
                1.0
            } else {
                ((currentTime - (prev?.timestampMillis ?: currentTime)) / 1000.0).coerceAtLeast(1.0)
            }

            val gps = GPSData(
                position = GeoPoint(point.latitude, point.longitude),
                altitude = AltitudeM(point.gpsAltitude),
                speed = SpeedMs(movement.speedMs),
                bearing = movement.bearingDeg.toDouble(),
                accuracy = 5f,
                timestamp = currentTime,
                monotonicTimestampMillis = currentTime
            )

            val varioResult = ModernVarioResult(
                altitude = point.pressureAltitude ?: point.gpsAltitude,
                verticalSpeed = vario,
                acceleration = 0.0,
                confidence = 0.9
            )

            val result = useCase.execute(
                FlightMetricsRequest(
                    gps = gps,
                    currentTimeMillis = currentTime,
                    wallTimeMillis = currentTime,
                    gpsTimestampMillis = currentTime,
                    deltaTimeSeconds = dtSeconds,
                    varioResult = varioResult,
                    varioGpsValue = vario,
                    baroResult = null,
                    windState = windState,
                    varioValidUntil = currentTime + 1_000,
                    isFlying = true,
                    macCreadySetting = 0.0,
                    autoMcEnabled = false,
                    flightMode = FlightMode.CRUISE
                )
            )

            if (result.levoNettoValid) {
                validCount += 1
                maxAbs = max(maxAbs, abs(result.levoNettoMs))
            }

            prev = point
        }

        assertTrue("Expected valid levo netto samples", validCount > 0)
        assertTrue(
            "Levo netto should stay bounded for the replay sample (maxAbs=$maxAbs)",
            maxAbs <= MAX_ABS_LEVO_MS
        )
    }

    companion object {
        private const val REPLAY_RESOURCE = "replay/vario-demo-0-10-0-120s.igc"
        private const val MAX_ABS_LEVO_MS = 3.0
    }
}
