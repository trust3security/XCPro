package com.example.xcpro.screens.navdrawer

import com.example.xcpro.weather.rain.WeatherOverlayPreferences
import com.example.xcpro.weather.rain.WeatherOverlayPreferencesRepository
import com.example.xcpro.weather.rain.WeatherRadarFrameMode
import com.example.xcpro.weather.rain.WeatherRadarRenderOptions
import com.example.xcpro.weather.rain.WeatherRainAnimationSpeed
import com.example.xcpro.weather.rain.WeatherRainAnimationWindow
import com.example.xcpro.weather.rain.WeatherRainTransitionQuality
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherSettingsUseCaseTest {

    private val repository: WeatherOverlayPreferencesRepository = mock()

    @Test
    fun flowProjection_matchesPreferencesRepositoryState() = runTest {
        val preferences = WeatherOverlayPreferences(
            enabled = true,
            opacity = 0.62f,
            animatePastWindow = true,
            animationWindow = WeatherRainAnimationWindow.SIXTY_MINUTES,
            animationSpeed = WeatherRainAnimationSpeed.FAST,
            transitionQuality = WeatherRainTransitionQuality.SMOOTH,
            frameMode = WeatherRadarFrameMode.MANUAL,
            manualFrameIndex = 3,
            renderOptions = WeatherRadarRenderOptions(
                smooth = false,
                snow = true
            )
        )
        whenever(repository.enabledFlow).thenReturn(flowOf(preferences.enabled))
        whenever(repository.opacityFlow).thenReturn(flowOf(preferences.opacity))
        whenever(repository.preferencesFlow).thenReturn(flowOf(preferences))
        val useCase = WeatherSettingsUseCase(repository)

        assertTrue(useCase.rainOverlayEnabledFlow.first())
        assertEquals(0.62f, useCase.rainOpacityFlow.first(), 0f)
        assertTrue(useCase.rainAnimatePastWindowFlow.first())
        assertEquals(
            WeatherRainAnimationWindow.SIXTY_MINUTES,
            useCase.rainAnimationWindowFlow.first()
        )
        assertEquals(WeatherRainAnimationSpeed.FAST, useCase.rainAnimationSpeedFlow.first())
        assertEquals(
            WeatherRainTransitionQuality.SMOOTH,
            useCase.rainTransitionQualityFlow.first()
        )
        assertEquals(WeatherRadarFrameMode.MANUAL, useCase.rainFrameModeFlow.first())
        assertEquals(3, useCase.rainManualFrameIndexFlow.first())
        assertFalse(useCase.rainSmoothEnabledFlow.first())
        assertTrue(useCase.rainSnowEnabledFlow.first())
    }

    @Test
    fun setters_delegateToPreferencesRepository() = runTest {
        whenever(repository.enabledFlow).thenReturn(flowOf(false))
        whenever(repository.opacityFlow).thenReturn(flowOf(0.45f))
        whenever(repository.preferencesFlow).thenReturn(flowOf(WeatherOverlayPreferences()))
        val useCase = WeatherSettingsUseCase(repository)

        useCase.setOverlayEnabled(true)
        useCase.setOpacity(0.5f)
        useCase.setAnimatePastWindow(true)
        useCase.setAnimationWindow(WeatherRainAnimationWindow.SIXTY_MINUTES)
        useCase.setAnimationSpeed(WeatherRainAnimationSpeed.SLOW)
        useCase.setTransitionQuality(WeatherRainTransitionQuality.CRISP)
        useCase.setFrameMode(WeatherRadarFrameMode.MANUAL)
        useCase.setManualFrameIndex(4)
        useCase.setSmoothEnabled(false)
        useCase.setSnowEnabled(false)

        verify(repository).setEnabled(true)
        verify(repository).setOpacity(0.5f)
        verify(repository).setAnimatePastWindow(true)
        verify(repository).setAnimationWindow(WeatherRainAnimationWindow.SIXTY_MINUTES)
        verify(repository).setAnimationSpeed(WeatherRainAnimationSpeed.SLOW)
        verify(repository).setTransitionQuality(WeatherRainTransitionQuality.CRISP)
        verify(repository).setFrameMode(WeatherRadarFrameMode.MANUAL)
        verify(repository).setManualFrameIndex(4)
        verify(repository).setSmoothEnabled(false)
        verify(repository).setSnowEnabled(false)
    }
}
