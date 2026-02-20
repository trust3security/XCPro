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
    fun setUp() = runBlocking {
        val preferencesRepository = ForecastPreferencesRepository(context)
        preferencesRepository.setOverlayEnabled(false)
        preferencesRepository.setOpacity(FORECAST_OPACITY_DEFAULT)
        preferencesRepository.setWindOverlayScale(FORECAST_WIND_OVERLAY_SCALE_DEFAULT)
        preferencesRepository.setSecondaryPrimaryOverlayEnabled(
            FORECAST_SECONDARY_PRIMARY_OVERLAY_ENABLED_DEFAULT
        )
        preferencesRepository.setWindOverlayEnabled(FORECAST_WIND_OVERLAY_ENABLED_DEFAULT)
        preferencesRepository.setWindDisplayMode(FORECAST_WIND_DISPLAY_MODE_DEFAULT)
        preferencesRepository.setSelectedPrimaryParameterId(DEFAULT_FORECAST_PARAMETER_ID)
        preferencesRepository.setSelectedSecondaryPrimaryParameterId(
            DEFAULT_FORECAST_SECONDARY_PRIMARY_PARAMETER_ID
        )
        preferencesRepository.setSelectedWindParameterId(DEFAULT_FORECAST_WIND_PARAMETER_ID)
        preferencesRepository.setSelectedTimeUtcMs(null)
        preferencesRepository.setFollowTimeOffsetMinutes(FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT)
        preferencesRepository.setAutoTimeEnabled(FORECAST_AUTO_TIME_DEFAULT)
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

        val state = repository.overlayState.first { state ->
            state.enabled && state.primaryTileSpec != null && state.primaryLegend != null
        }

        assertEquals(1, tilesPort.calls)
        assertEquals(1, legendPort.calls)
        assertNotNull(state.primaryTileSpec)
        assertNotNull(state.primaryLegend)
    }

    @Test
    fun enabledOverlay_withSecondaryPrimary_fetchesBothPrimaryLayers() = runTest(testDispatcher) {
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
        preferencesRepository.setSecondaryPrimaryOverlayEnabled(true)
        preferencesRepository.setSelectedPrimaryParameterId(ForecastParameterId("wstar_bsratio"))
        preferencesRepository.setSelectedSecondaryPrimaryParameterId(ForecastParameterId("accrain"))
        advanceUntilIdle()

        val state = repository.overlayState.first { state ->
            state.enabled &&
                state.primaryTileSpec != null &&
                state.secondaryPrimaryTileSpec != null &&
                state.primaryLegend != null &&
                state.secondaryPrimaryLegend != null
        }

        assertTrue(state.secondaryPrimaryOverlayEnabled)
        assertNotNull(state.secondaryPrimaryTileSpec)
        assertNotNull(state.secondaryPrimaryLegend)
        assertEquals(2, tilesPort.calls)
        assertEquals(2, legendPort.calls)
    }

    @Test
    fun enabledOverlay_rainPlusConvergence_fetchesBothAsMultiPrimary() = runTest(testDispatcher) {
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
        preferencesRepository.setSelectedSecondaryPrimaryParameterId(ForecastParameterId("wblmaxmin"))
        preferencesRepository.setOverlayEnabled(true)
        preferencesRepository.setSecondaryPrimaryOverlayEnabled(true)
        advanceUntilIdle()

        val state = repository.overlayState.first { state ->
            state.enabled &&
                state.primaryTileSpec != null &&
                state.primaryLegend != null &&
                state.secondaryPrimaryTileSpec != null &&
                state.secondaryPrimaryLegend != null
        }

        assertEquals("accrain", state.selectedPrimaryParameterId.value)
        assertEquals("wblmaxmin", state.selectedSecondaryPrimaryParameterId.value)
        assertEquals(
            setOf("ACCRAIN", "WBLMAXMIN"),
            tilesPort.requestedParameterIds.map { id -> id.value.uppercase() }.toSet()
        )
        assertEquals(
            setOf("ACCRAIN", "WBLMAXMIN"),
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

    private class CountingTilesPort : ForecastTilesPort {
        var calls: Int = 0

        override suspend fun getTileSpec(
            parameterId: ForecastParameterId,
            timeSlot: ForecastTimeSlot,
            regionCode: String
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

        override suspend fun getLegend(
            parameterId: ForecastParameterId,
            timeSlot: ForecastTimeSlot,
            regionCode: String
        ): ForecastLegendSpec {
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

    private class RecordingTilesPort : ForecastTilesPort {
        val requestedParameterIds = mutableListOf<ForecastParameterId>()

        override suspend fun getTileSpec(
            parameterId: ForecastParameterId,
            timeSlot: ForecastTimeSlot,
            regionCode: String
        ): ForecastTileSpec {
            requestedParameterIds.add(parameterId)
            return ForecastTileSpec(
                urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                minZoom = 0,
                maxZoom = 18
            )
        }
    }

    private class RecordingLegendPort : ForecastLegendPort {
        val requestedParameterIds = mutableListOf<ForecastParameterId>()

        override suspend fun getLegend(
            parameterId: ForecastParameterId,
            timeSlot: ForecastTimeSlot,
            regionCode: String
        ): ForecastLegendSpec {
            requestedParameterIds.add(parameterId)
            return ForecastLegendSpec(
                unitLabel = "m/s",
                stops = listOf(
                    ForecastLegendStop(value = 0.0, argb = 0xFF000000.toInt()),
                    ForecastLegendStop(value = 1.0, argb = 0xFFFFFFFF.toInt())
                )
            )
        }
    }

    private class LowercaseCatalogPort : ForecastCatalogPort, ForecastValuePort {
        override suspend fun getParameters(): List<ForecastParameterMeta> = listOf(
            ForecastParameterMeta(
                id = ForecastParameterId("wstar_bsratio"),
                name = "Thermal",
                category = "Thermal",
                unitLabel = "m/s"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("dwcrit"),
                name = "Thermal Height",
                category = "Thermal",
                unitLabel = "m"
            )
        )

        override fun getTimeSlots(
            nowUtcMs: Long,
            regionCode: String
        ): List<ForecastTimeSlot> = listOf(
            ForecastTimeSlot(validTimeUtcMs = nowUtcMs)
        )

        override suspend fun getValue(
            latitude: Double,
            longitude: Double,
            parameterId: ForecastParameterId,
            timeSlot: ForecastTimeSlot,
            regionCode: String
        ): ForecastPointValue = ForecastPointValue(
            value = 0.0,
            unitLabel = "m",
            validTimeUtcMs = timeSlot.validTimeUtcMs
        )
    }

    private class OffsetAwareCatalogPort : ForecastCatalogPort, ForecastValuePort {
        override suspend fun getParameters(): List<ForecastParameterMeta> = listOf(
            ForecastParameterMeta(
                id = DEFAULT_FORECAST_PARAMETER_ID,
                name = "Thermal",
                category = "Thermal",
                unitLabel = "m/s"
            )
        )

        override fun getTimeSlots(
            nowUtcMs: Long,
            regionCode: String
        ): List<ForecastTimeSlot> = listOf(
            ForecastTimeSlot(validTimeUtcMs = nowUtcMs - 60 * 60_000L),
            ForecastTimeSlot(validTimeUtcMs = nowUtcMs - 30 * 60_000L),
            ForecastTimeSlot(validTimeUtcMs = nowUtcMs),
            ForecastTimeSlot(validTimeUtcMs = nowUtcMs + 30 * 60_000L),
            ForecastTimeSlot(validTimeUtcMs = nowUtcMs + 60 * 60_000L)
        )

        override suspend fun getValue(
            latitude: Double,
            longitude: Double,
            parameterId: ForecastParameterId,
            timeSlot: ForecastTimeSlot,
            regionCode: String
        ): ForecastPointValue = ForecastPointValue(
            value = 0.0,
            unitLabel = "m/s",
            validTimeUtcMs = timeSlot.validTimeUtcMs
        )
    }

    private class RainConvergenceCatalogPort : ForecastCatalogPort {
        override suspend fun getParameters(): List<ForecastParameterMeta> = listOf(
            ForecastParameterMeta(
                id = ForecastParameterId("accrain"),
                name = "Rain",
                category = "Precip",
                unitLabel = "mm/h"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("wblmaxmin"),
                name = "Convergence",
                category = "Lift",
                unitLabel = "m/s",
                supportsPointValue = false
            )
        )

        override fun getTimeSlots(
            nowUtcMs: Long,
            regionCode: String
        ): List<ForecastTimeSlot> = listOf(
            ForecastTimeSlot(validTimeUtcMs = nowUtcMs)
        )
    }

    private class ConvergenceCatalogPort : ForecastCatalogPort {
        override suspend fun getParameters(): List<ForecastParameterMeta> = listOf(
            ForecastParameterMeta(
                id = ForecastParameterId("wblmaxmin"),
                name = "Convergence",
                category = "Lift",
                unitLabel = "m/s",
                supportsPointValue = false
            )
        )

        override fun getTimeSlots(
            nowUtcMs: Long,
            regionCode: String
        ): List<ForecastTimeSlot> = listOf(
            ForecastTimeSlot(validTimeUtcMs = nowUtcMs)
        )
    }

    private class CountingValuePort : ForecastValuePort {
        var calls: Int = 0

        override suspend fun getValue(
            latitude: Double,
            longitude: Double,
            parameterId: ForecastParameterId,
            timeSlot: ForecastTimeSlot,
            regionCode: String
        ): ForecastPointValue {
            calls += 1
            return ForecastPointValue(
                value = 0.0,
                unitLabel = "m/s",
                validTimeUtcMs = timeSlot.validTimeUtcMs
            )
        }
    }

}
