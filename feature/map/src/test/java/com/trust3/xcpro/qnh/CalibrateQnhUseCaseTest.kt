package com.trust3.xcpro.qnh

import com.trust3.xcpro.core.flight.calculations.TerrainElevationReadPort
import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.PressureHpa
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.flightdata.FlightDataRepository
import com.trust3.xcpro.sensors.AccelData
import com.trust3.xcpro.sensors.AttitudeData
import com.trust3.xcpro.sensors.BaroData
import com.trust3.xcpro.sensors.CompassData
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.sensors.RawAccelData
import com.trust3.xcpro.sensors.SensorDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrateQnhUseCaseTest {

    @Test
    fun execute_usesSharedTerrainPortForTerrainCalibration() = runTest {
        val terrainPort = FakeTerrainElevationReadPort(terrainElevation = 998.0)
        val repository = FakeQnhRepository()
        val useCase = newUseCase(
            terrainPort = terrainPort,
            qnhRepository = repository,
            flightDataRepository = FlightDataRepository(),
            config = QnhCalibrationConfig(samplesRequired = 1)
        )

        val resultDeferred = async { useCase.execute() }
        advanceUntilIdle()
        val result = resultDeferred.await()

        val success = result as QnhCalibrationResult.Success
        val expectedQnh = QnhMath.computeQnhFromPressure(
            pressureHpa = 900.0,
            altitudeMeters = 1_000.0
        )
        assertEquals(expectedQnh, success.value.hpa, 1e-6)
        assertEquals(QnhSource.AUTO_TERRAIN, success.value.source)
        assertEquals(QnhConfidence.HIGH, success.value.confidence)
        assertEquals(1, terrainPort.callCount)
        assertEquals(success.value, repository.appliedAutoQnh)
        assertTrue(repository.calibrationState.value is QnhCalibrationState.Succeeded)
    }

    @Test
    fun execute_fallsBackToGpsAltitudeWhenTerrainUnavailable() = runTest {
        val terrainPort = FakeTerrainElevationReadPort(terrainElevation = null)
        val repository = FakeQnhRepository()
        val useCase = newUseCase(
            terrainPort = terrainPort,
            qnhRepository = repository,
            flightDataRepository = FlightDataRepository(),
            config = QnhCalibrationConfig(samplesRequired = 1)
        )

        val resultDeferred = async { useCase.execute() }
        advanceUntilIdle()
        val result = resultDeferred.await()

        val success = result as QnhCalibrationResult.Success
        val expectedQnh = QnhMath.computeQnhFromPressure(
            pressureHpa = 900.0,
            altitudeMeters = 1_050.0
        )
        assertEquals(expectedQnh, success.value.hpa, 1e-6)
        assertEquals(QnhSource.AUTO_GPS, success.value.source)
        assertEquals(QnhConfidence.MEDIUM, success.value.confidence)
        assertEquals(1, terrainPort.callCount)
    }

    @Test
    fun execute_inReplayMode_failsBeforeTerrainLookup() = runTest {
        val terrainPort = FakeTerrainElevationReadPort(terrainElevation = 998.0)
        val repository = FakeQnhRepository()
        val flightDataRepository = FlightDataRepository().apply {
            setActiveSource(FlightDataRepository.Source.REPLAY)
        }
        val useCase = newUseCase(
            terrainPort = terrainPort,
            qnhRepository = repository,
            flightDataRepository = flightDataRepository,
            config = QnhCalibrationConfig(samplesRequired = 1)
        )

        val resultDeferred = async { useCase.execute() }
        advanceUntilIdle()
        val result = resultDeferred.await()

        val failure = result as QnhCalibrationResult.Failure
        assertEquals(QnhCalibrationFailureReason.REPLAY_MODE, failure.reason)
        assertEquals(0, terrainPort.callCount)
        assertEquals(
            QnhCalibrationState.Failed(QnhCalibrationFailureReason.REPLAY_MODE),
            repository.calibrationState.value
        )
    }

    private fun TestScope.newUseCase(
        terrainPort: TerrainElevationReadPort,
        qnhRepository: FakeQnhRepository,
        flightDataRepository: FlightDataRepository,
        config: QnhCalibrationConfig
    ): CalibrateQnhUseCase {
        return CalibrateQnhUseCase(
            sensorDataSource = FakeSensorDataSource(
                baro = baroSample(),
                gps = gpsSample()
            ),
            terrainElevationReadPort = terrainPort,
            qnhRepository = qnhRepository,
            flightDataRepository = flightDataRepository,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            clock = FakeClock(monoMs = 5_000L, wallMs = 9_000L),
            config = config
        )
    }

    private fun baroSample(): BaroData = BaroData(
        pressureHPa = PressureHpa(900.0),
        timestamp = 1_000L,
        monotonicTimestampMillis = 1_000L
    )

    private fun gpsSample(): GPSData = GPSData(
        position = GeoPoint(-35.0, 149.0),
        altitude = AltitudeM(1_050.0),
        speed = SpeedMs(0.0),
        bearing = 0.0,
        accuracy = 4f,
        timestamp = 1_000L,
        monotonicTimestampMillis = 1_000L
    )

    private class FakeTerrainElevationReadPort(
        private val terrainElevation: Double?
    ) : TerrainElevationReadPort {
        var callCount: Int = 0
            private set

        override suspend fun getElevationMeters(lat: Double, lon: Double): Double? {
            callCount += 1
            return terrainElevation
        }
    }

    private class FakeSensorDataSource(
        baro: BaroData?,
        gps: GPSData?
    ) : SensorDataSource {
        override val gpsFlow: StateFlow<GPSData?> = MutableStateFlow(gps)
        override val baroFlow: StateFlow<BaroData?> = MutableStateFlow(baro)
        override val compassFlow: StateFlow<CompassData?> = MutableStateFlow(null)
        override val rawAccelFlow: StateFlow<RawAccelData?> = MutableStateFlow(null)
        override val accelFlow: StateFlow<AccelData?> = MutableStateFlow(null)
        override val attitudeFlow: StateFlow<AttitudeData?> = MutableStateFlow(null)
    }

    private class FakeQnhRepository : QnhRepository {
        private val initialValue = QnhValue(
            hpa = 1013.25,
            source = QnhSource.STANDARD,
            calibratedAtMillis = 0L,
            confidence = QnhConfidence.LOW
        )
        private val qnhFlow = MutableStateFlow(initialValue)
        override val qnhState: StateFlow<QnhValue> = qnhFlow
        override val calibrationState: MutableStateFlow<QnhCalibrationState> =
            MutableStateFlow(QnhCalibrationState.Idle)

        var appliedAutoQnh: QnhValue? = null
            private set

        override suspend fun setActiveProfileId(profileId: String) = Unit

        override suspend fun setManualQnh(hpa: Double) = Unit

        override suspend fun resetToStandard() = Unit

        override suspend fun applyAutoQnh(value: QnhValue) {
            appliedAutoQnh = value
            qnhFlow.value = value
        }

        override fun updateCalibrationState(state: QnhCalibrationState) {
            calibrationState.value = state
        }
    }
}
