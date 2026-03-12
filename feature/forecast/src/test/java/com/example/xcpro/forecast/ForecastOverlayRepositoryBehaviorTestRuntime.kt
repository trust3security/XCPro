package com.example.xcpro.forecast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class ForecastOverlayRepositoryBehaviorTest {

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
    fun selectedParameter_matchesCaseInsensitively() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = LowercaseCatalogPort()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = CountingTilesPort(),
            legendPort = CountingLegendPort(),
            valuePort = catalogPort,
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setSelectedPrimaryParameterId(ForecastParameterId("DWCRIT"))
        preferencesRepository.setOverlayEnabled(true)
        advanceUntilIdle()

        val state = repository.overlayState.first()

        assertEquals("dwcrit", state.selectedPrimaryParameterId.value)
    }

    @Test
    fun windDisplayMode_propagatesToUiState() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = FakeForecastProviderAdapter()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = CountingTilesPort(),
            legendPort = CountingLegendPort(),
            valuePort = catalogPort,
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setWindDisplayMode(ForecastWindDisplayMode.BARB)
        advanceUntilIdle()

        val state = repository.overlayState.first()

        assertEquals(ForecastWindDisplayMode.BARB, state.windDisplayMode)
    }

    @Test
    fun autoTime_withPositiveFollowOffset_selectsFutureSlot() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = OffsetAwareCatalogPort()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = CountingTilesPort(),
            legendPort = CountingLegendPort(),
            valuePort = catalogPort,
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setFollowTimeOffsetMinutes(60)
        preferencesRepository.setAutoTimeEnabled(true)
        preferencesRepository.setOverlayEnabled(true)
        advanceUntilIdle()

        val state = repository.overlayState.first()
        val nowUtcMs = clock.nowWallMs()

        assertEquals(nowUtcMs + 60 * 60_000L, state.selectedTimeUtcMs)
        assertEquals(60, state.followTimeOffsetMinutes)
    }

    @Test
    fun autoTime_withNegativeFollowOffset_selectsPastSlot() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = OffsetAwareCatalogPort()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = CountingTilesPort(),
            legendPort = CountingLegendPort(),
            valuePort = catalogPort,
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setFollowTimeOffsetMinutes(-60)
        preferencesRepository.setAutoTimeEnabled(true)
        preferencesRepository.setOverlayEnabled(true)
        advanceUntilIdle()

        val state = repository.overlayState.first()
        val nowUtcMs = clock.nowWallMs()

        assertEquals(nowUtcMs - 60 * 60_000L, state.selectedTimeUtcMs)
        assertEquals(-60, state.followTimeOffsetMinutes)
    }

    @Test
    fun manualMode_withoutSelectedTime_ignoresFollowOffset() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = OffsetAwareCatalogPort()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = CountingTilesPort(),
            legendPort = CountingLegendPort(),
            valuePort = catalogPort,
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setAutoTimeEnabled(false)
        preferencesRepository.setSelectedTimeUtcMs(null)
        preferencesRepository.setFollowTimeOffsetMinutes(60)
        preferencesRepository.setOverlayEnabled(true)
        advanceUntilIdle()

        val state = repository.overlayState.first()
        val nowUtcMs = clock.nowWallMs()

        assertEquals(nowUtcMs, state.selectedTimeUtcMs)
    }

    @Test
    fun queryPointValue_returnsUnavailable_whenParameterDoesNotSupportPointValue() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = ConvergenceCatalogPort()
        val valuePort = CountingValuePort()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = CountingTilesPort(),
            legendPort = CountingLegendPort(),
            valuePort = valuePort,
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setOverlayEnabled(true)
        preferencesRepository.setSelectedPrimaryParameterId(ForecastParameterId("wblmaxmin"))
        advanceUntilIdle()

        val result = repository.queryPointValue(
            latitude = -33.8688,
            longitude = 151.2093
        )

        assertTrue(result is ForecastPointQueryResult.Unavailable)
        assertEquals(0, valuePort.calls)
    }

    @Test
    fun overlayState_propagatesCancellation_whenTileFetchIsCancelled() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = FakeForecastProviderAdapter()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = CancellingTilesPort(),
            legendPort = CountingLegendPort(),
            valuePort = catalogPort,
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setOverlayEnabled(true)
        advanceUntilIdle()

        var cancelled = false
        try {
            repository.overlayState.first { state ->
                state.primaryTileSpec != null
            }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test
    fun queryPointValue_propagatesCancellation_fromValuePort() = runTest(testDispatcher) {
        val preferencesRepository = ForecastPreferencesRepository(context)
        val catalogPort = FakeForecastProviderAdapter()
        val repository = ForecastOverlayRepository(
            preferencesRepository = preferencesRepository,
            catalogPort = catalogPort,
            tilesPort = CountingTilesPort(),
            legendPort = CountingLegendPort(),
            valuePort = CancellingValuePort(),
            clock = clock,
            dispatcher = testDispatcher
        )

        preferencesRepository.setOverlayEnabled(true)
        advanceUntilIdle()

        var cancelled = false
        try {
            repository.queryPointValue(
                latitude = 37.0,
                longitude = -120.0
            )
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }
}
