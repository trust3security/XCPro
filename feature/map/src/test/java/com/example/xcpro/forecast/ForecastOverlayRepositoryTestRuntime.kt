package com.example.xcpro.forecast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class ForecastOverlayRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock(
        monoMs = 0L,
        wallMs = 1_700_000_123_456L
    )
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = runBlocking(Dispatchers.IO) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        preferencesRepository.setOverlayEnabled(false)
        preferencesRepository.setOpacity(FORECAST_OPACITY_DEFAULT)
        preferencesRepository.setWindOverlayScale(FORECAST_WIND_OVERLAY_SCALE_DEFAULT)
        preferencesRepository.setWindOverlayEnabled(FORECAST_WIND_OVERLAY_ENABLED_DEFAULT)
        preferencesRepository.setWindDisplayMode(FORECAST_WIND_DISPLAY_MODE_DEFAULT)
        preferencesRepository.setSelectedPrimaryParameterId(DEFAULT_FORECAST_PARAMETER_ID)
        preferencesRepository.setSelectedWindParameterId(DEFAULT_FORECAST_WIND_PARAMETER_ID)
        preferencesRepository.setSelectedTimeUtcMs(null)
        preferencesRepository.setFollowTimeOffsetMinutes(FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT)
        preferencesRepository.setAutoTimeEnabled(FORECAST_AUTO_TIME_DEFAULT)
    }

    @Test
    fun disabledOverlay_doesNotFetchLegendOrTiles() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = FakeForecastProviderAdapter()
        val tilesPort = CountingTilesPort()
        val legendPort = CountingLegendPort()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = tilesPort,
            legendPort = legendPort,
            valuePort = catalogPort,
            clock = clock,
            dispatcher = testDispatcher
        )

        val state = repository.overlayState.first()

        assertFalse(state.enabled)
        assertEquals(0, tilesPort.calls)
        assertEquals(0, legendPort.calls)
    }

    @Test
    fun satelliteOnly_doesNotResolveForecastSelection() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = CountingCatalogOnlyPort()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = CountingTilesPort(),
            legendPort = CountingLegendPort(),
            valuePort = catalogPort,
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setOverlayEnabled(false)
        preferencesRepository.setWindOverlayEnabled(false)
        preferencesRepository.setSkySightSatelliteOverlayEnabled(true)
        advanceUntilIdle()

        val state = repository.overlayState.first { state ->
            state.skySightSatelliteOverlayEnabled
        }

        assertTrue(state.skySightSatelliteOverlayEnabled)
        assertEquals(0, catalogPort.parametersCalls)
        assertEquals(0, catalogPort.timeSlotsCalls)
        assertEquals(0, state.primaryParameters.size)
        val stepMs = FORECAST_SKYSIGHT_SATELLITE_FRAME_STEP_MINUTES * 60_000L
        assertEquals((clock.nowWallMs() / stepMs) * stepMs, state.selectedTimeUtcMs)
    }

    @Test
    fun enabledOverlay_fetchesLegendAndTiles() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = FakeForecastProviderAdapter()
        val tilesPort = CountingTilesPort()
        val legendPort = CountingLegendPort()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = tilesPort,
            legendPort = legendPort,
            valuePort = catalogPort,
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setOverlayEnabled(true)
        advanceUntilIdle()

        val state = repository.overlayState.first { state ->
            state.enabled && state.primaryTileSpec != null && state.primaryLegend != null
        }

        assertEquals(1, tilesPort.calls)
        assertEquals(1, legendPort.calls)
        assertNotNull(state.primaryTileSpec)
        assertNotNull(state.primaryLegend)
    }

    @Test
    fun enabledOverlay_withLegacyPrimaryPreference_fetchesSinglePrimaryLayer() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = FakeForecastProviderAdapter()
        val tilesPort = CountingTilesPort()
        val legendPort = CountingLegendPort()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = tilesPort,
            legendPort = legendPort,
            valuePort = catalogPort,
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setOverlayEnabled(true)
        preferencesRepository.setSelectedPrimaryParameterId(ForecastParameterId("wstar_bsratio"))
        advanceUntilIdle()

        val state = repository.overlayState.first { state ->
            state.enabled &&
                state.primaryTileSpec != null &&
                state.primaryLegend != null
        }

        assertEquals(1, tilesPort.calls)
        assertEquals(1, legendPort.calls)
    }

    @Test
    fun enabledOverlay_rainPlusConvergence_usesOnlySelectedPrimary() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = RainConvergenceCatalogPort()
        val tilesPort = RecordingTilesPort()
        val legendPort = RecordingLegendPort()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = tilesPort,
            legendPort = legendPort,
            valuePort = CountingValuePort(),
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setSelectedPrimaryParameterId(ForecastParameterId("accrain"))
        preferencesRepository.setOverlayEnabled(true)
        advanceUntilIdle()

        val state = repository.overlayState.first { state ->
            state.enabled &&
                state.primaryTileSpec != null &&
                state.primaryLegend != null
        }

        assertEquals("accrain", state.selectedPrimaryParameterId.value)
        assertEquals(
            setOf("ACCRAIN"),
            tilesPort.requestedParameterIds.map { id -> id.value.uppercase() }.toSet()
        )
        assertEquals(
            setOf("ACCRAIN"),
            legendPort.requestedParameterIds.map { id -> id.value.uppercase() }.toSet()
        )
    }

    @Test
    fun enabledOverlay_withWindOverlay_fetchesPrimaryAndWindTiles() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = FakeForecastProviderAdapter()
        val tilesPort = CountingTilesPort()
        val legendPort = CountingLegendPort()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = tilesPort,
            legendPort = legendPort,
            valuePort = catalogPort,
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setOverlayEnabled(true)
        preferencesRepository.setWindOverlayEnabled(true)
        advanceUntilIdle()

        val state = repository.overlayState.first { state ->
            state.enabled &&
                state.primaryTileSpec != null &&
                state.primaryLegend != null &&
                state.windTileSpec != null &&
                state.windLegend != null
        }

        assertEquals(2, tilesPort.calls)
        assertEquals(2, legendPort.calls)
        assertTrue(state.windOverlayEnabled)
    }

    @Test
    fun primaryDisabled_withWindOverlay_fetchesWindTilesOnly() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = FakeForecastProviderAdapter()
        val tilesPort = CountingTilesPort()
        val legendPort = CountingLegendPort()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = tilesPort,
            legendPort = legendPort,
            valuePort = catalogPort,
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setOverlayEnabled(false)
        preferencesRepository.setWindOverlayEnabled(true)
        advanceUntilIdle()

        val state = repository.overlayState.first { state ->
            !state.enabled &&
                state.primaryTileSpec == null &&
                state.windOverlayEnabled &&
                state.windTileSpec != null &&
                state.windLegend != null
        }

        assertEquals(1, tilesPort.calls)
        assertEquals(1, legendPort.calls)
        assertTrue(state.windOverlayEnabled)
    }

    @Test
    fun primaryDisabled_withWindOverlayTileFailure_setsFatalErrorMessage() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = FakeForecastProviderAdapter()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = FailingTilesPort(message = "wind tile failed"),
            legendPort = CountingLegendPort(),
            valuePort = catalogPort,
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setOverlayEnabled(false)
        preferencesRepository.setWindOverlayEnabled(true)
        advanceUntilIdle()

        val state = repository.overlayState.first { state ->
            !state.isLoading &&
                state.windOverlayEnabled &&
                state.errorMessage != null
        }

        assertEquals("wind tile failed", state.errorMessage)
        assertEquals(null, state.warningMessage)
        assertEquals(null, state.windTileSpec)
    }

    @Test
    fun primaryDisabled_withWindTileAndLegendFailure_dedupesWarningAgainstError() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = FakeForecastProviderAdapter()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = FailingTilesPort(message = "wind failed"),
            legendPort = FailingLegendPort(message = "wind failed"),
            valuePort = catalogPort,
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setOverlayEnabled(false)
        preferencesRepository.setWindOverlayEnabled(true)
        advanceUntilIdle()

        val state = repository.overlayState.first { state ->
            !state.isLoading && state.windOverlayEnabled && state.errorMessage != null
        }

        assertEquals("wind failed", state.errorMessage)
        assertEquals(null, state.warningMessage)
    }

}
