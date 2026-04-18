package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.audio.VarioAudioSettings
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LevoVarioSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_exposes_canonical_thresholds() = runTest {
        val useCase = mock<LevoVarioSettingsUseCase>()
        whenever(useCase.configFlow).thenReturn(
            MutableStateFlow(
                LevoVarioConfig(
                    audioSettings = VarioAudioSettings(
                        liftStartThreshold = 0.5,
                        sinkStartThreshold = -1.5
                    )
                )
            )
        )

        val viewModel = LevoVarioSettingsViewModel(useCase)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(0.5f, viewModel.uiState.value.liftStartThreshold, 0.0f)
        assertEquals(-1.5f, viewModel.uiState.value.sinkStartThreshold, 0.0f)
        collector.cancel()
    }

    @Test
    fun canonical_threshold_setters_update_canonical_fields() = runTest {
        val useCase = mock<LevoVarioSettingsUseCase>()
        whenever(useCase.configFlow).thenReturn(MutableStateFlow(LevoVarioConfig()))

        val viewModel = LevoVarioSettingsViewModel(useCase)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.setLiftStartThreshold(0.7f)
        viewModel.setSinkStartThreshold(-1.8f)
        runCurrent()

        val transformCaptor = argumentCaptor<(VarioAudioSettings) -> VarioAudioSettings>()
        verify(useCase, times(2)).updateAudioSettings(transformCaptor.capture())

        val liftUpdated = transformCaptor.allValues[0](VarioAudioSettings())
        assertEquals(0.7, liftUpdated.liftStartThreshold, 1e-6)

        val sinkUpdated = transformCaptor.allValues[1](VarioAudioSettings())
        assertEquals(-1.8, sinkUpdated.sinkStartThreshold, 1e-6)
        collector.cancel()
    }
}
