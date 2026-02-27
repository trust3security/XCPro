package com.example.xcpro.weather.rain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherOverlayPreferencesRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking(Dispatchers.IO) {
        val repository = WeatherOverlayPreferencesRepository(context)
        repository.setEnabled(false)
        repository.setOpacity(WEATHER_RAIN_OPACITY_DEFAULT)
        repository.setAnimatePastWindow(false)
        repository.setAnimationWindow(WeatherRainAnimationWindow.TEN_MINUTES)
        repository.setAnimationSpeed(WeatherRainAnimationSpeed.NORMAL)
        repository.setTransitionQuality(WeatherRainTransitionQuality.BALANCED)
    }

    @Test
    fun enabled_defaultsToFalse() = runTest {
        val repository = WeatherOverlayPreferencesRepository(context)

        val enabled = repository.enabledFlow.first()

        assertFalse(enabled)
    }

    @Test
    fun opacity_defaultsToConfiguredDefault() = runTest {
        val repository = WeatherOverlayPreferencesRepository(context)

        val opacity = repository.opacityFlow.first()

        assertEquals(WEATHER_RAIN_OPACITY_DEFAULT, opacity)
    }

    @Test
    fun setOpacity_clampsBelowMinimum() = runTest {
        val repository = WeatherOverlayPreferencesRepository(context)

        repository.setOpacity(-1f)
        val opacity = repository.opacityFlow.first()

        assertEquals(WEATHER_RAIN_OPACITY_MIN, opacity)
    }

    @Test
    fun setOpacity_clampsAboveMaximum() = runTest {
        val repository = WeatherOverlayPreferencesRepository(context)

        repository.setOpacity(5f)
        val opacity = repository.opacityFlow.first()

        assertEquals(WEATHER_RAIN_OPACITY_MAX, opacity)
    }

    @Test
    fun setValues_persistAndReadBack() = runTest {
        val repository = WeatherOverlayPreferencesRepository(context)

        repository.setEnabled(true)
        repository.setOpacity(0.7f)
        repository.setAnimatePastWindow(true)
        repository.setAnimationWindow(WeatherRainAnimationWindow.THIRTY_MINUTES)
        repository.setAnimationSpeed(WeatherRainAnimationSpeed.FAST)
        repository.setTransitionQuality(WeatherRainTransitionQuality.SMOOTH)
        repository.setFrameMode(WeatherRadarFrameMode.MANUAL)
        repository.setManualFrameIndex(3)
        repository.setSmoothEnabled(false)
        repository.setSnowEnabled(true)

        assertTrue(repository.enabledFlow.first())
        assertEquals(0.7f, repository.opacityFlow.first())
        val preferences = repository.preferencesFlow.first()
        assertTrue(preferences.animatePastWindow)
        assertEquals(WeatherRainAnimationWindow.THIRTY_MINUTES, preferences.animationWindow)
        assertEquals(WeatherRainAnimationSpeed.FAST, preferences.animationSpeed)
        assertEquals(WeatherRainTransitionQuality.SMOOTH, preferences.transitionQuality)
        assertEquals(WeatherRadarFrameMode.MANUAL, preferences.frameMode)
        assertEquals(3, preferences.manualFrameIndex)
        assertFalse(preferences.renderOptions.smooth)
        assertTrue(preferences.renderOptions.snow)
    }
}
