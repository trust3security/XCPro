package com.example.xcpro.forecast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun setUp() = runBlocking {
        val preferencesRepository = ForecastPreferencesRepository(context)
        preferencesRepository.setOverlayEnabled(false)
        preferencesRepository.setOpacity(FORECAST_OPACITY_DEFAULT)
        preferencesRepository.setSelectedParameterId(DEFAULT_FORECAST_PARAMETER_ID)
        preferencesRepository.setSelectedTimeUtcMs(null)
    }

    @After
    fun tearDown() {
        context.filesDir.resolve("datastore")?.takeIf { it.exists() }?.deleteRecursively()
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

        val state = repository.overlayState.first { it.enabled }

        assertEquals(1, tilesPort.calls)
        assertEquals(1, legendPort.calls)
        assertNotNull(state.tileSpec)
        assertNotNull(state.legend)
    }

    private class CountingTilesPort : ForecastTilesPort {
        var calls: Int = 0

        override suspend fun getTileSpec(
            parameterId: ForecastParameterId,
            timeSlot: ForecastTimeSlot
        ): ForecastTileSpec {
            calls += 1
            return ForecastTileSpec(
                urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                minZoom = 0,
                maxZoom = 18
            )
        }
    }

    private class CountingLegendPort : ForecastLegendPort {
        var calls: Int = 0

        override suspend fun getLegend(parameterId: ForecastParameterId): ForecastLegendSpec {
            calls += 1
            return ForecastLegendSpec(
                unitLabel = "m/s",
                stops = listOf(
                    ForecastLegendStop(value = 0.0, argb = 0xFF000000.toInt()),
                    ForecastLegendStop(value = 1.0, argb = 0xFFFFFFFF.toInt())
                )
            )
        }
    }
}

