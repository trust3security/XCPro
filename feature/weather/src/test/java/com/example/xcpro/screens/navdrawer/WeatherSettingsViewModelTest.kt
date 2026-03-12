package com.example.xcpro.screens.navdrawer

import com.example.xcpro.testing.MainDispatcherRule
import com.example.xcpro.weather.rain.WeatherOverlayPreferences
import com.example.xcpro.weather.rain.WeatherOverlayPreferencesRepository
import com.example.xcpro.weather.rain.WeatherRadarFrameMode
import com.example.xcpro.weather.rain.WeatherRadarRenderOptions
import com.example.xcpro.weather.rain.WeatherRainAnimationSpeed
import com.example.xcpro.weather.rain.WeatherRainAnimationWindow
import com.example.xcpro.weather.rain.WeatherRainTransitionQuality
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: WeatherOverlayPreferencesRepository = mock()

    @Test
    fun stateFlows_reflectRepositoryValuesAndUpdates() = runTest {
        val enabledFlow = MutableStateFlow(true)
        val opacityFlow = MutableStateFlow(0.61f)
        val preferencesFlow = MutableStateFlow(
            WeatherOverlayPreferences(
                enabled = true,
                opacity = 0.61f,
                animatePastWindow = true,
                animationWindow = WeatherRainAnimationWindow.SIXTY_MINUTES,
                animationSpeed = WeatherRainAnimationSpeed.FAST,
                transitionQuality = WeatherRainTransitionQuality.SMOOTH,
                frameMode = WeatherRadarFrameMode.MANUAL,
                manualFrameIndex = 2,
                renderOptions = WeatherRadarRenderOptions(
                    smooth = false,
                    snow = false
                )
            )
        )
        whenever(repository.enabledFlow).thenReturn(enabledFlow)
        whenever(repository.opacityFlow).thenReturn(opacityFlow)
        whenever(repository.preferencesFlow).thenReturn(preferencesFlow)

        val viewModel = WeatherSettingsViewModel(WeatherSettingsUseCase(repository))
        val collectors = listOf(
            backgroundScope.launch { viewModel.overlayEnabled.collect {} },
            backgroundScope.launch { viewModel.opacity.collect {} },
            backgroundScope.launch { viewModel.animatePastWindow.collect {} },
            backgroundScope.launch { viewModel.animationWindow.collect {} },
            backgroundScope.launch { viewModel.animationSpeed.collect {} },
            backgroundScope.launch { viewModel.transitionQuality.collect {} },
            backgroundScope.launch { viewModel.frameMode.collect {} },
            backgroundScope.launch { viewModel.manualFrameIndex.collect {} },
            backgroundScope.launch { viewModel.smoothEnabled.collect {} },
            backgroundScope.launch { viewModel.snowEnabled.collect {} }
        )
        runCurrent()

        assertTrue(viewModel.overlayEnabled.value)
        assertEquals(0.61f, viewModel.opacity.value, 0f)
        assertTrue(viewModel.animatePastWindow.value)
        assertEquals(WeatherRainAnimationWindow.SIXTY_MINUTES, viewModel.animationWindow.value)
        assertEquals(WeatherRainAnimationSpeed.FAST, viewModel.animationSpeed.value)
        assertEquals(WeatherRainTransitionQuality.SMOOTH, viewModel.transitionQuality.value)
        assertEquals(WeatherRadarFrameMode.MANUAL, viewModel.frameMode.value)
        assertEquals(2, viewModel.manualFrameIndex.value)
        assertFalse(viewModel.smoothEnabled.value)
        assertFalse(viewModel.snowEnabled.value)

        enabledFlow.value = false
        opacityFlow.value = 0.44f
        preferencesFlow.value = preferencesFlow.value.copy(
            animatePastWindow = false,
            animationWindow = WeatherRainAnimationWindow.ONE_HUNDRED_TWENTY_MINUTES,
            animationSpeed = WeatherRainAnimationSpeed.SLOW,
            transitionQuality = WeatherRainTransitionQuality.CRISP,
            frameMode = WeatherRadarFrameMode.LATEST,
            manualFrameIndex = 0,
            renderOptions = WeatherRadarRenderOptions(
                smooth = true,
                snow = true
            )
        )
        runCurrent()

        assertFalse(viewModel.overlayEnabled.value)
        assertEquals(0.44f, viewModel.opacity.value, 0f)
        assertFalse(viewModel.animatePastWindow.value)
        assertEquals(
            WeatherRainAnimationWindow.ONE_HUNDRED_TWENTY_MINUTES,
            viewModel.animationWindow.value
        )
        assertEquals(WeatherRainAnimationSpeed.SLOW, viewModel.animationSpeed.value)
        assertEquals(WeatherRainTransitionQuality.CRISP, viewModel.transitionQuality.value)
        assertEquals(WeatherRadarFrameMode.LATEST, viewModel.frameMode.value)
        assertEquals(0, viewModel.manualFrameIndex.value)
        assertTrue(viewModel.smoothEnabled.value)
        assertTrue(viewModel.snowEnabled.value)
        collectors.forEach { it.cancel() }
    }

    @Test
    fun setters_delegateToUseCaseRepositoryPath() = runTest {
        whenever(repository.enabledFlow).thenReturn(MutableStateFlow(false))
        whenever(repository.opacityFlow).thenReturn(MutableStateFlow(0.45f))
        whenever(repository.preferencesFlow).thenReturn(MutableStateFlow(WeatherOverlayPreferences()))
        val viewModel = WeatherSettingsViewModel(WeatherSettingsUseCase(repository))

        viewModel.setOverlayEnabled(true)
        viewModel.setOpacity(0.7f)
        viewModel.setAnimatePastWindow(true)
        viewModel.setAnimationWindow(WeatherRainAnimationWindow.SIXTY_MINUTES)
        viewModel.setAnimationSpeed(WeatherRainAnimationSpeed.FAST)
        viewModel.setTransitionQuality(WeatherRainTransitionQuality.SMOOTH)
        viewModel.setFrameMode(WeatherRadarFrameMode.MANUAL)
        viewModel.setManualFrameIndex(5)
        viewModel.setSmoothEnabled(false)
        viewModel.setSnowEnabled(false)
        runCurrent()

        verify(repository).setEnabled(true)
        verify(repository).setOpacity(0.7f)
        verify(repository).setAnimatePastWindow(true)
        verify(repository).setAnimationWindow(WeatherRainAnimationWindow.SIXTY_MINUTES)
        verify(repository).setAnimationSpeed(WeatherRainAnimationSpeed.FAST)
        verify(repository).setTransitionQuality(WeatherRainTransitionQuality.SMOOTH)
        verify(repository).setFrameMode(WeatherRadarFrameMode.MANUAL)
        verify(repository).setManualFrameIndex(5)
        verify(repository).setSmoothEnabled(false)
        verify(repository).setSnowEnabled(false)
    }
}
