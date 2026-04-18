package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.hawk.HawkConfidence
import com.trust3.xcpro.hawk.HawkVarioPreviewReadPort
import com.trust3.xcpro.hawk.HawkVarioUiState
import com.trust3.xcpro.vario.LevoVarioConfig
import com.trust3.xcpro.vario.LevoVarioPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
class HawkVarioSettingsUseCaseTest {

    @Test
    fun exposesPreferenceAndPreviewFlowsFromCanonicalOwners() = runTest {
        val preferencesRepository = mock<LevoVarioPreferencesRepository>()
        val previewReadPort = mock<HawkVarioPreviewReadPort>()
        val configFlow = MutableStateFlow(
            LevoVarioConfig(
                enableHawkUi = true,
                showHawkCard = true,
                hawkNeedleOmegaMinHz = 1.1,
                hawkNeedleOmegaMaxHz = 2.6
            )
        )
        val previewFlow = MutableStateFlow(
            HawkVarioUiState(
                varioSmoothedMps = 1.4f,
                confidence = HawkConfidence.LEVEL5
            )
        )
        whenever(preferencesRepository.config).thenReturn(configFlow)
        whenever(previewReadPort.hawkVarioUiState).thenReturn(previewFlow)

        val useCase = HawkVarioSettingsUseCase(preferencesRepository, previewReadPort)

        assertEquals(true, useCase.configFlow.first().enableHawkUi)
        assertEquals(true, useCase.configFlow.first().showHawkCard)
        assertEquals(1.4f, useCase.hawkVarioUiState.first().varioSmoothedMps)
        assertEquals(HawkConfidence.LEVEL5, useCase.hawkVarioUiState.first().confidence)
    }

    @Test
    fun setters_delegateToPreferencesRepository() = runTest {
        val preferencesRepository = mock<LevoVarioPreferencesRepository>()
        whenever(preferencesRepository.config).thenReturn(MutableStateFlow(LevoVarioConfig()))
        val previewReadPort = mock<HawkVarioPreviewReadPort>()
        whenever(previewReadPort.hawkVarioUiState).thenReturn(MutableStateFlow(HawkVarioUiState()))
        val useCase = HawkVarioSettingsUseCase(preferencesRepository, previewReadPort)

        useCase.setEnableHawkUi(true)
        useCase.setShowHawkCard(true)
        useCase.setHawkNeedleOmegaMinHz(0.9)
        useCase.setHawkNeedleOmegaMaxHz(2.1)
        useCase.setHawkNeedleTargetTauSec(0.7)
        useCase.setHawkNeedleDriftTauMinSec(3.0)
        useCase.setHawkNeedleDriftTauMaxSec(7.5)

        verify(preferencesRepository).setEnableHawkUi(true)
        verify(preferencesRepository).setShowHawkCard(true)
        verify(preferencesRepository).setHawkNeedleOmegaMinHz(0.9)
        verify(preferencesRepository).setHawkNeedleOmegaMaxHz(2.1)
        verify(preferencesRepository).setHawkNeedleTargetTauSec(0.7)
        verify(preferencesRepository).setHawkNeedleDriftTauMinSec(3.0)
        verify(preferencesRepository).setHawkNeedleDriftTauMaxSec(7.5)
    }
}
