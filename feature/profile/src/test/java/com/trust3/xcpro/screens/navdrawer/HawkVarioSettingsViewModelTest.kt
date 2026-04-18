package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.common.units.UnitsSettingsUseCase
import com.trust3.xcpro.common.units.VerticalSpeedUnit
import com.trust3.xcpro.hawk.HawkConfidence
import com.trust3.xcpro.hawk.HawkVarioUiState
import com.trust3.xcpro.testing.MainDispatcherRule
import com.trust3.xcpro.vario.LevoVarioConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HawkVarioSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_reflectsConfigPreviewAndUnitsUpdates() = runTest {
        val useCase = mock<HawkVarioSettingsUseCase>()
        val unitsUseCase = mock<UnitsSettingsUseCase>()
        val configFlow = MutableStateFlow(
            LevoVarioConfig(
                enableHawkUi = true,
                showHawkCard = true,
                hawkNeedleOmegaMinHz = 1.2,
                hawkNeedleOmegaMaxHz = 2.4,
                hawkNeedleTargetTauSec = 0.9,
                hawkNeedleDriftTauMinSec = 3.2,
                hawkNeedleDriftTauMaxSec = 7.8
            )
        )
        val hawkPreviewFlow = MutableStateFlow(
            HawkVarioUiState(
                varioSmoothedMps = 1.6f,
                confidence = HawkConfidence.LEVEL4
            )
        )
        val unitsFlow = MutableStateFlow(UnitsPreferences())
        whenever(useCase.configFlow).thenReturn(configFlow)
        whenever(useCase.hawkVarioUiState).thenReturn(hawkPreviewFlow)
        whenever(unitsUseCase.unitsFlow).thenReturn(unitsFlow)

        val viewModel = HawkVarioSettingsViewModel(useCase, unitsUseCase)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.setProfileId("pilot-a")
        unitsFlow.value = UnitsPreferences(verticalSpeed = VerticalSpeedUnit.KNOTS)
        hawkPreviewFlow.value = HawkVarioUiState(
            varioSmoothedMps = 2.2f,
            confidence = HawkConfidence.LEVEL6
        )
        runCurrent()

        verify(unitsUseCase).setActiveProfileId("pilot-a")
        assertEquals(true, viewModel.uiState.value.enableHawkUi)
        assertEquals(true, viewModel.uiState.value.showHawkCard)
        assertEquals(2.2f, viewModel.uiState.value.hawkVarioUiState.varioSmoothedMps)
        assertEquals(HawkConfidence.LEVEL6, viewModel.uiState.value.hawkVarioUiState.confidence)
        assertEquals(VerticalSpeedUnit.KNOTS, viewModel.uiState.value.unitsPreferences.verticalSpeed)
        collector.cancel()
    }

    @Test
    fun setters_clampAndDelegateToUseCase() = runTest {
        val useCase = mock<HawkVarioSettingsUseCase>()
        val unitsUseCase = mock<UnitsSettingsUseCase>()
        whenever(useCase.configFlow).thenReturn(
            MutableStateFlow(
                LevoVarioConfig(
                    hawkNeedleOmegaMinHz = 0.9,
                    hawkNeedleOmegaMaxHz = 2.0,
                    hawkNeedleDriftTauMinSec = 3.5,
                    hawkNeedleDriftTauMaxSec = 8.0
                )
            )
        )
        whenever(useCase.hawkVarioUiState).thenReturn(MutableStateFlow(HawkVarioUiState()))
        whenever(unitsUseCase.unitsFlow).thenReturn(MutableStateFlow(UnitsPreferences()))

        val viewModel = HawkVarioSettingsViewModel(useCase, unitsUseCase)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.setNeedleOmegaMinHz(9.0)
        viewModel.setNeedleOmegaMaxHz(0.1)
        viewModel.setNeedleTargetTauSec(9.0)
        viewModel.setNeedleDriftTauMinSec(99.0)
        viewModel.setNeedleDriftTauMaxSec(0.1)
        runCurrent()

        verify(useCase).setHawkNeedleOmegaMinHz(2.0)
        verify(useCase).setHawkNeedleOmegaMaxHz(1.0)
        verify(useCase).setHawkNeedleTargetTauSec(2.0)
        verify(useCase).setHawkNeedleDriftTauMinSec(8.0)
        verify(useCase).setHawkNeedleDriftTauMaxSec(3.5)
        collector.cancel()
    }
}
