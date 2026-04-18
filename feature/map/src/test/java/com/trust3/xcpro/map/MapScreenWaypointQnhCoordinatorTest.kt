package com.trust3.xcpro.map

import com.trust3.xcpro.common.waypoint.WaypointData
import com.trust3.xcpro.common.waypoint.WaypointLoader
import com.trust3.xcpro.qnh.CalibrateQnhUseCase
import com.trust3.xcpro.qnh.QnhCalibrationFailureReason
import com.trust3.xcpro.qnh.QnhCalibrationResult
import com.trust3.xcpro.qnh.QnhCalibrationState
import com.trust3.xcpro.qnh.QnhConfidence
import com.trust3.xcpro.qnh.QnhRepository
import com.trust3.xcpro.qnh.QnhSource
import com.trust3.xcpro.qnh.QnhValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenWaypointQnhCoordinatorTest {

    @Test
    fun loadWaypoints_updatesStateOnSuccess() = runTest {
        val uiState = MutableStateFlow(MapUiState())
        val uiEffects = MutableSharedFlow<MapUiEffect>(replay = 4, extraBufferCapacity = 4)
        val waypoint = WaypointData(
            name = "Canberra",
            code = "YSCB",
            country = "AU",
            latitude = -35.3075,
            longitude = 149.1244,
            elevation = "1886",
            style = 2,
            runwayDirection = null,
            runwayLength = null,
            frequency = null,
            description = null
        )
        val coordinator = MapScreenWaypointQnhCoordinator(
            scope = backgroundScope,
            uiState = uiState,
            uiEffects = uiEffects,
            waypointLoader = WaypointLoader { listOf(waypoint) },
            qnhRepository = RecordingQnhRepository(),
            calibrateQnhUseCase = Mockito.mock(CalibrateQnhUseCase::class.java)
        )

        coordinator.loadWaypoints()
        runCurrent()

        assertEquals(listOf(waypoint), uiState.value.waypoints)
        assertFalse(uiState.value.isLoadingWaypoints)
        assertNull(uiState.value.waypointError)
    }

    @Test
    fun onAutoCalibrateQnh_successEmitsSameToast() = runTest {
        val uiState = MutableStateFlow(MapUiState())
        val uiEffects = MutableSharedFlow<MapUiEffect>(replay = 4, extraBufferCapacity = 4)
        val calibrateQnhUseCase = Mockito.mock(CalibrateQnhUseCase::class.java)
        Mockito.`when`(calibrateQnhUseCase.execute()).thenReturn(
            QnhCalibrationResult.Success(
                QnhValue(
                    hpa = 1018.5,
                    source = QnhSource.AUTO_GPS,
                    calibratedAtMillis = 123L,
                    confidence = QnhConfidence.HIGH
                )
            )
        )
        val coordinator = MapScreenWaypointQnhCoordinator(
            scope = backgroundScope,
            uiState = uiState,
            uiEffects = uiEffects,
            waypointLoader = WaypointLoader { emptyList() },
            qnhRepository = RecordingQnhRepository(),
            calibrateQnhUseCase = calibrateQnhUseCase
        )

        coordinator.onAutoCalibrateQnh()
        runCurrent()

        assertEquals(
            MapUiEffect.ShowToast("QNH updated to 1018.5 hPa"),
            uiEffects.replayCache.last()
        )
    }

    @Test
    fun onAutoCalibrateQnh_failureEmitsSameToast() = runTest {
        val uiState = MutableStateFlow(MapUiState())
        val uiEffects = MutableSharedFlow<MapUiEffect>(replay = 4, extraBufferCapacity = 4)
        val calibrateQnhUseCase = Mockito.mock(CalibrateQnhUseCase::class.java)
        Mockito.`when`(calibrateQnhUseCase.execute()).thenReturn(
            QnhCalibrationResult.Failure(QnhCalibrationFailureReason.MISSING_SENSORS)
        )
        val coordinator = MapScreenWaypointQnhCoordinator(
            scope = backgroundScope,
            uiState = uiState,
            uiEffects = uiEffects,
            waypointLoader = WaypointLoader { emptyList() },
            qnhRepository = RecordingQnhRepository(),
            calibrateQnhUseCase = calibrateQnhUseCase
        )

        coordinator.onAutoCalibrateQnh()
        runCurrent()

        assertEquals(
            MapUiEffect.ShowToast("Auto calibration needs GPS and baro"),
            uiEffects.replayCache.last()
        )
    }

    @Test
    fun onSetManualQnh_delegatesToRepositoryAndEmitsToast() = runTest {
        val uiState = MutableStateFlow(MapUiState())
        val uiEffects = MutableSharedFlow<MapUiEffect>(replay = 4, extraBufferCapacity = 4)
        val qnhRepository = RecordingQnhRepository()
        val coordinator = MapScreenWaypointQnhCoordinator(
            scope = backgroundScope,
            uiState = uiState,
            uiEffects = uiEffects,
            waypointLoader = WaypointLoader { emptyList() },
            qnhRepository = qnhRepository,
            calibrateQnhUseCase = Mockito.mock(CalibrateQnhUseCase::class.java)
        )

        coordinator.onSetManualQnh(1009.2)
        runCurrent()

        assertEquals(listOf(1009.2), qnhRepository.manualQnhValues)
        assertEquals(
            MapUiEffect.ShowToast("QNH set to 1009.2 hPa"),
            uiEffects.replayCache.last()
        )
    }

    private class RecordingQnhRepository : QnhRepository {
        override val qnhState: StateFlow<QnhValue> = MutableStateFlow(
            QnhValue(
                hpa = 1013.25,
                source = QnhSource.STANDARD,
                calibratedAtMillis = 0L,
                confidence = QnhConfidence.HIGH
            )
        )
        override val calibrationState: StateFlow<QnhCalibrationState> =
            MutableStateFlow(QnhCalibrationState.Idle)
        val manualQnhValues = mutableListOf<Double>()

        override suspend fun setActiveProfileId(profileId: String) = Unit

        override suspend fun setManualQnh(hpa: Double) {
            manualQnhValues += hpa
        }

        override suspend fun resetToStandard() = Unit

        override suspend fun applyAutoQnh(value: QnhValue) = Unit

        override fun updateCalibrationState(state: QnhCalibrationState) = Unit
    }
}
