package com.example.xcpro.map

import com.example.xcpro.MapOrientationSettingsRepository
import com.example.xcpro.common.glider.GliderConfigRepository
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import com.example.xcpro.map.trail.TrailLength
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.TrailType
import com.example.xcpro.qnh.QnhCalibrationState
import com.example.xcpro.qnh.QnhConfidence
import com.example.xcpro.qnh.QnhRepository
import com.example.xcpro.qnh.QnhSource
import com.example.xcpro.qnh.QnhValue
import com.example.xcpro.variometer.layout.VariometerLayoutUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenProfileSessionCoordinatorTest {

    @Test
    fun setActiveProfileId_updatesAllOwnersAndTrailSettings() = runTest {
        val mapStyleRepository = Mockito.mock(MapStyleRepository::class.java)
        Mockito.`when`(mapStyleRepository.readProfileStyle("profile-a")).thenReturn(MapStyleCatalog.TERRAIN)
        val unitsRepository = Mockito.mock(UnitsRepository::class.java)
        val orientationSettingsRepository = Mockito.mock(MapOrientationSettingsRepository::class.java)
        val gliderConfigRepository = Mockito.mock(GliderConfigRepository::class.java)
        val variometerLayoutUseCase = Mockito.mock(VariometerLayoutUseCase::class.java)
        val trailSettingsUseCase = Mockito.mock(MapTrailSettingsUseCase::class.java)
        val expectedTrailSettings = TrailSettings(
            length = TrailLength.SHORT,
            type = TrailType.ALTITUDE,
            windDriftEnabled = false,
            scalingEnabled = true
        )
        Mockito.`when`(trailSettingsUseCase.getSettings()).thenReturn(expectedTrailSettings)
        val qnhRepository = RecordingQnhRepository()
        val commands = mutableListOf<MapCommand>()
        val mapStateStore = MapStateStore(MapStyleCatalog.TOPO)
        val coordinator = MapScreenProfileSessionCoordinator(
            scope = backgroundScope,
            mapStyleRepository = mapStyleRepository,
            unitsRepository = unitsRepository,
            orientationSettingsRepository = orientationSettingsRepository,
            gliderConfigRepository = gliderConfigRepository,
            variometerLayoutUseCase = variometerLayoutUseCase,
            trailSettingsUseCase = trailSettingsUseCase,
            qnhRepository = qnhRepository,
            mapStateStore = mapStateStore,
            emitMapCommand = commands::add
        )

        coordinator.setActiveProfileId("profile-a")
        runCurrent()

        Mockito.verify(mapStyleRepository).setActiveProfileId("profile-a")
        Mockito.verify(mapStyleRepository).readProfileStyle("profile-a")
        Mockito.verify(unitsRepository).setActiveProfileId("profile-a")
        Mockito.verify(orientationSettingsRepository).setActiveProfileId("profile-a")
        Mockito.verify(gliderConfigRepository).setActiveProfileId("profile-a")
        Mockito.verify(variometerLayoutUseCase).setActiveProfileId("profile-a")
        Mockito.verify(trailSettingsUseCase).setActiveProfileId("profile-a")
        assertEquals(expectedTrailSettings, mapStateStore.trailSettings.value)
        assertEquals(listOf("profile-a"), qnhRepository.activeProfileIds)
        assertEquals(listOf(MapCommand.SetStyle(MapStyleCatalog.TERRAIN)), commands)
    }

    @Test
    fun setActiveProfileId_blankProfileResolvesDefaultProfile() = runTest {
        val mapStyleRepository = Mockito.mock(MapStyleRepository::class.java)
        Mockito.`when`(mapStyleRepository.readProfileStyle("profile-a")).thenReturn(MapStyleCatalog.TERRAIN)
        Mockito.`when`(mapStyleRepository.readProfileStyle("default-profile")).thenReturn(MapStyleCatalog.TOPO)
        val unitsRepository = Mockito.mock(UnitsRepository::class.java)
        val orientationSettingsRepository = Mockito.mock(MapOrientationSettingsRepository::class.java)
        val gliderConfigRepository = Mockito.mock(GliderConfigRepository::class.java)
        val variometerLayoutUseCase = Mockito.mock(VariometerLayoutUseCase::class.java)
        val trailSettingsUseCase = Mockito.mock(MapTrailSettingsUseCase::class.java)
        Mockito.`when`(trailSettingsUseCase.getSettings()).thenReturn(TrailSettings())
        val qnhRepository = RecordingQnhRepository()
        val coordinator = MapScreenProfileSessionCoordinator(
            scope = backgroundScope,
            mapStyleRepository = mapStyleRepository,
            unitsRepository = unitsRepository,
            orientationSettingsRepository = orientationSettingsRepository,
            gliderConfigRepository = gliderConfigRepository,
            variometerLayoutUseCase = variometerLayoutUseCase,
            trailSettingsUseCase = trailSettingsUseCase,
            qnhRepository = qnhRepository,
            mapStateStore = MapStateStore(MapStyleCatalog.TOPO),
            emitMapCommand = {}
        )

        coordinator.setActiveProfileId("profile-a")
        coordinator.setActiveProfileId("   ")
        runCurrent()

        Mockito.verify(mapStyleRepository).setActiveProfileId("default-profile")
        Mockito.verify(unitsRepository).setActiveProfileId("default-profile")
        Mockito.verify(orientationSettingsRepository).setActiveProfileId("default-profile")
        Mockito.verify(gliderConfigRepository).setActiveProfileId("default-profile")
        Mockito.verify(variometerLayoutUseCase).setActiveProfileId("default-profile")
        Mockito.verify(trailSettingsUseCase).setActiveProfileId("default-profile")
        assertEquals(listOf("default-profile"), qnhRepository.activeProfileIds)
    }

    @Test
    fun setActiveProfileId_onlyEmitsStyleCommandWhenEffectiveStyleChanges() = runTest {
        val mapStyleRepository = Mockito.mock(MapStyleRepository::class.java)
        Mockito.`when`(mapStyleRepository.readProfileStyle("profile-a")).thenReturn(MapStyleCatalog.TOPO)
        val trailSettingsUseCase = Mockito.mock(MapTrailSettingsUseCase::class.java)
        Mockito.`when`(trailSettingsUseCase.getSettings()).thenReturn(TrailSettings())
        val commands = mutableListOf<MapCommand>()
        val coordinator = MapScreenProfileSessionCoordinator(
            scope = backgroundScope,
            mapStyleRepository = mapStyleRepository,
            unitsRepository = Mockito.mock(UnitsRepository::class.java),
            orientationSettingsRepository = Mockito.mock(MapOrientationSettingsRepository::class.java),
            gliderConfigRepository = Mockito.mock(GliderConfigRepository::class.java),
            variometerLayoutUseCase = Mockito.mock(VariometerLayoutUseCase::class.java),
            trailSettingsUseCase = trailSettingsUseCase,
            qnhRepository = RecordingQnhRepository(),
            mapStateStore = MapStateStore(MapStyleCatalog.TOPO),
            emitMapCommand = commands::add
        )

        coordinator.setActiveProfileId("profile-a")
        runCurrent()

        assertTrue(commands.isEmpty())
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
        val activeProfileIds = mutableListOf<String>()

        override suspend fun setActiveProfileId(profileId: String) {
            activeProfileIds += profileId
        }

        override suspend fun setManualQnh(hpa: Double) = Unit

        override suspend fun resetToStandard() = Unit

        override suspend fun applyAutoQnh(value: QnhValue) = Unit

        override fun updateCalibrationState(state: QnhCalibrationState) = Unit
    }
}
