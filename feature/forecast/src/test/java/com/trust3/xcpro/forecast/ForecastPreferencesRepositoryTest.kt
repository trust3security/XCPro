package com.trust3.xcpro.forecast

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
class ForecastPreferencesRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking(Dispatchers.IO) {
        val repository = ForecastPreferencesRepository(context)
        repository.setOverlayEnabled(false)
        repository.setOpacity(FORECAST_OPACITY_DEFAULT)
        repository.setWindOverlayScale(FORECAST_WIND_OVERLAY_SCALE_DEFAULT)
        repository.setWindOverlayEnabled(FORECAST_WIND_OVERLAY_ENABLED_DEFAULT)
        repository.setWindDisplayMode(FORECAST_WIND_DISPLAY_MODE_DEFAULT)
        repository.setSelectedPrimaryParameterId(DEFAULT_FORECAST_PARAMETER_ID)
        repository.setSelectedWindParameterId(DEFAULT_FORECAST_WIND_PARAMETER_ID)
        repository.setSelectedTimeUtcMs(null)
        repository.setSelectedRegion(DEFAULT_FORECAST_REGION_CODE)
        repository.setFollowTimeOffsetMinutes(FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT)
        repository.setAutoTimeEnabled(FORECAST_AUTO_TIME_DEFAULT)
    }

    @Test
    fun overlayEnabled_defaultsToFalse() = runTest {
        val repository = ForecastPreferencesRepository(context)

        val current = repository.overlayEnabledFlow.first()

        assertFalse(current)
    }

    @Test
    fun opacityFlow_defaultsToConfiguredDefault() = runTest {
        val repository = ForecastPreferencesRepository(context)

        val current = repository.opacityFlow.first()

        assertEquals(FORECAST_OPACITY_DEFAULT, current)
    }

    @Test
    fun setOpacity_clampsBelowMinimum() = runTest {
        val repository = ForecastPreferencesRepository(context)

        repository.setOpacity(-1f)
        val current = repository.opacityFlow.first()

        assertEquals(FORECAST_OPACITY_MIN, current)
    }

    @Test
    fun setOpacity_clampsAboveMaximum() = runTest {
        val repository = ForecastPreferencesRepository(context)

        repository.setOpacity(2f)
        val current = repository.opacityFlow.first()

        assertEquals(FORECAST_OPACITY_MAX, current)
    }

    @Test
    fun windOverlayScaleFlow_defaultsToConfiguredDefault() = runTest {
        val repository = ForecastPreferencesRepository(context)

        val current = repository.windOverlayScaleFlow.first()

        assertEquals(FORECAST_WIND_OVERLAY_SCALE_DEFAULT, current)
    }

    @Test
    fun setWindOverlayScale_clampsBelowMinimum() = runTest {
        val repository = ForecastPreferencesRepository(context)

        repository.setWindOverlayScale(-1f)
        val current = repository.windOverlayScaleFlow.first()

        assertEquals(FORECAST_WIND_OVERLAY_SCALE_MIN, current)
    }

    @Test
    fun setWindOverlayScale_clampsAboveMaximum() = runTest {
        val repository = ForecastPreferencesRepository(context)

        repository.setWindOverlayScale(9f)
        val current = repository.windOverlayScaleFlow.first()

        assertEquals(FORECAST_WIND_OVERLAY_SCALE_MAX, current)
    }

    @Test
    fun windDisplayMode_defaultsToArrow() = runTest {
        val repository = ForecastPreferencesRepository(context)

        val current = repository.windDisplayModeFlow.first()

        assertEquals(FORECAST_WIND_DISPLAY_MODE_DEFAULT, current)
    }

    @Test
    fun windOverlayEnabled_defaultsToFalse() = runTest {
        val repository = ForecastPreferencesRepository(context)

        val current = repository.windOverlayEnabledFlow.first()

        assertFalse(current)
    }

    @Test
    fun setValues_persistAndReadBack() = runTest {
        val repository = ForecastPreferencesRepository(context)

        repository.setOverlayEnabled(true)
        repository.setOpacity(0.4f)
        repository.setWindOverlayScale(1.5f)
        repository.setWindOverlayEnabled(true)
        repository.setWindDisplayMode(ForecastWindDisplayMode.BARB)
        repository.setSelectedWindParameterId(ForecastParameterId("bltopwind"))
        repository.setSelectedRegion("EUROPE")

        val enabled = repository.overlayEnabledFlow.first()
        val opacity = repository.opacityFlow.first()
        val windOverlayScale = repository.windOverlayScaleFlow.first()
        val windOverlayEnabled = repository.windOverlayEnabledFlow.first()
        val windDisplayMode = repository.windDisplayModeFlow.first()
        val selectedWindParameter = repository.selectedWindParameterIdFlow.first()
        val selectedRegion = repository.selectedRegionFlow.first()

        assertTrue(enabled)
        assertEquals(0.4f, opacity)
        assertEquals(1.5f, windOverlayScale)
        assertTrue(windOverlayEnabled)
        assertEquals(ForecastWindDisplayMode.BARB, windDisplayMode)
        assertEquals("bltopwind", selectedWindParameter.value)
        assertEquals("EUROPE", selectedRegion)
    }

    @Test
    fun selectedRegion_defaultsToConfiguredDefault() = runTest {
        val repository = ForecastPreferencesRepository(context)

        val selectedRegion = repository.selectedRegionFlow.first()

        assertEquals(DEFAULT_FORECAST_REGION_CODE, selectedRegion)
    }

    @Test
    fun setSelectedRegion_normalizesUnknownRegionToDefault() = runTest {
        val repository = ForecastPreferencesRepository(context)

        repository.setSelectedRegion("unknown_region")
        val selectedRegion = repository.selectedRegionFlow.first()

        assertEquals(DEFAULT_FORECAST_REGION_CODE, selectedRegion)
    }

    @Test
    fun setSelectedParameter_preservesProviderIdCasing() = runTest {
        val repository = ForecastPreferencesRepository(context)

        repository.setSelectedPrimaryParameterId(ForecastParameterId("dwcrit"))
        val selected = repository.selectedPrimaryParameterIdFlow.first()

        assertEquals("dwcrit", selected.value)
    }

    @Test
    fun autoTimeEnabled_defaultsToConfiguredDefault() = runTest {
        val repository = ForecastPreferencesRepository(context)

        val enabled = repository.autoTimeEnabledFlow.first()

        assertEquals(FORECAST_AUTO_TIME_DEFAULT, enabled)
    }

    @Test
    fun followTimeOffset_defaultsToNow() = runTest {
        val repository = ForecastPreferencesRepository(context)

        val offset = repository.followTimeOffsetMinutesFlow.first()

        assertEquals(FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT, offset)
    }

    @Test
    fun setFollowTimeOffset_normalizesToNearestAllowedStep() = runTest {
        val repository = ForecastPreferencesRepository(context)

        repository.setFollowTimeOffsetMinutes(44)
        val positiveOffset = repository.followTimeOffsetMinutesFlow.first()

        repository.setFollowTimeOffsetMinutes(-44)
        val negativeOffset = repository.followTimeOffsetMinutesFlow.first()

        assertEquals(30, positiveOffset)
        assertEquals(-30, negativeOffset)
    }

    @Test
    fun setAutoTimeEnabled_trueClearsManualTimeSelection() = runTest {
        val repository = ForecastPreferencesRepository(context)

        repository.setSelectedTimeUtcMs(1_700_000_000_000L)
        repository.setAutoTimeEnabled(true)

        val selectedTime = repository.selectedTimeUtcMsFlow.first()
        val autoTimeEnabled = repository.autoTimeEnabledFlow.first()

        assertEquals(null, selectedTime)
        assertTrue(autoTimeEnabled)
    }
}
