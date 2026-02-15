package com.example.xcpro.forecast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
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
    fun setUp() = runBlocking {
        val repository = ForecastPreferencesRepository(context)
        repository.setOverlayEnabled(false)
        repository.setOpacity(FORECAST_OPACITY_DEFAULT)
        repository.setSelectedParameterId(DEFAULT_FORECAST_PARAMETER_ID)
        repository.setSelectedTimeUtcMs(null)
        repository.setSelectedRegion(DEFAULT_FORECAST_REGION_CODE)
        repository.setAutoTimeEnabled(FORECAST_AUTO_TIME_DEFAULT)
    }

    @After
    fun tearDown() {
        context.filesDir.resolve("datastore")?.takeIf { it.exists() }?.deleteRecursively()
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
    fun setValues_persistAndReadBack() = runTest {
        val repository = ForecastPreferencesRepository(context)

        repository.setOverlayEnabled(true)
        repository.setOpacity(0.4f)
        repository.setSelectedRegion("EUROPE")

        val enabled = repository.overlayEnabledFlow.first()
        val opacity = repository.opacityFlow.first()
        val selectedRegion = repository.selectedRegionFlow.first()

        assertTrue(enabled)
        assertEquals(0.4f, opacity)
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

        repository.setSelectedParameterId(ForecastParameterId("dwcrit"))
        val selected = repository.selectedParameterIdFlow.first()

        assertEquals("dwcrit", selected.value)
    }

    @Test
    fun autoTimeEnabled_defaultsToConfiguredDefault() = runTest {
        val repository = ForecastPreferencesRepository(context)

        val enabled = repository.autoTimeEnabledFlow.first()

        assertEquals(FORECAST_AUTO_TIME_DEFAULT, enabled)
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
