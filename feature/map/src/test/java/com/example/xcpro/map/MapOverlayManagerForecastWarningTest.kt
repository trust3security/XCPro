package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.forecast.ForecastTileSpec
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.map.trail.SnailTrailManager
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MapOverlayManagerForecastWarningTest {

    @Test
    fun setForecastOverlay_exposesRuntimeFallbackWarning() {
        val fixture = createFixture()
        val map: MapLibreMap = mock()
        val primaryOverlay: ForecastRasterOverlay = mock()
        val windOverlay: ForecastRasterOverlay = mock()
        val warning = "Forecast primary overlay source-layer fallback engaged ('1200')."

        fixture.mapState.mapLibreMap = map
        fixture.mapState.forecastOverlay = primaryOverlay
        fixture.mapState.forecastWindOverlay = windOverlay
        whenever(primaryOverlay.runtimeWarningMessage()).thenReturn(warning)
        whenever(windOverlay.runtimeWarningMessage()).thenReturn(null)

        fixture.manager.setForecastOverlay(
            enabled = true,
            primaryTileSpec = tileSpec(),
            primaryLegendSpec = null,
            windOverlayEnabled = false,
            windTileSpec = null,
            windLegendSpec = null,
            opacity = 0.7f,
            windOverlayScale = 1.0f,
            windDisplayMode = ForecastWindDisplayMode.ARROW
        )

        assertEquals(warning, fixture.manager.forecastRuntimeWarningMessage.value)
    }

    @Test
    fun setForecastOverlay_combinesPrimaryAndWindWarnings_withPipeDelimiter() {
        val fixture = createFixture()
        val map: MapLibreMap = mock()
        val primaryOverlay: ForecastRasterOverlay = mock()
        val windOverlay: ForecastRasterOverlay = mock()

        fixture.mapState.mapLibreMap = map
        fixture.mapState.forecastOverlay = primaryOverlay
        fixture.mapState.forecastWindOverlay = windOverlay
        whenever(primaryOverlay.runtimeWarningMessage()).thenReturn("primary fallback warning")
        whenever(windOverlay.runtimeWarningMessage()).thenReturn("wind fallback warning")

        fixture.manager.setForecastOverlay(
            enabled = true,
            primaryTileSpec = tileSpec(),
            primaryLegendSpec = null,
            windOverlayEnabled = true,
            windTileSpec = tileSpec(),
            windLegendSpec = null,
            opacity = 0.7f,
            windOverlayScale = 1.0f,
            windDisplayMode = ForecastWindDisplayMode.ARROW
        )

        assertEquals(
            "primary fallback warning | wind fallback warning",
            fixture.manager.forecastRuntimeWarningMessage.value
        )
    }

    @Test
    fun setForecastOverlay_renderFailure_surfacesRuntimeWarningWithoutThrowing() {
        val fixture = createFixture()
        val map: MapLibreMap = mock()
        val primaryOverlay: ForecastRasterOverlay = mock()
        val windOverlay: ForecastRasterOverlay = mock()
        val primaryTile = tileSpec()

        fixture.mapState.mapLibreMap = map
        fixture.mapState.forecastOverlay = primaryOverlay
        fixture.mapState.forecastWindOverlay = windOverlay
        doAnswer {
            throw IllegalStateException("primary render failed")
        }.whenever(primaryOverlay).render(
            tileSpec = eq(primaryTile),
            opacity = eq(0.7f),
            windOverlayScale = eq(1.0f),
            windDisplayMode = eq(ForecastWindDisplayMode.ARROW),
            legendSpec = eq(null)
        )

        fixture.manager.setForecastOverlay(
            enabled = true,
            primaryTileSpec = primaryTile,
            primaryLegendSpec = null,
            windOverlayEnabled = false,
            windTileSpec = null,
            windLegendSpec = null,
            opacity = 0.7f,
            windOverlayScale = 1.0f,
            windDisplayMode = ForecastWindDisplayMode.ARROW
        )

        assertEquals("primary render failed", fixture.manager.forecastRuntimeWarningMessage.value)
    }

    @Test
    fun clearForecastOverlay_clearsRuntimeFallbackWarning() {
        val fixture = createFixture()
        val map: MapLibreMap = mock()
        val primaryOverlay: ForecastRasterOverlay = mock()
        val windOverlay: ForecastRasterOverlay = mock()

        fixture.mapState.mapLibreMap = map
        fixture.mapState.forecastOverlay = primaryOverlay
        fixture.mapState.forecastWindOverlay = windOverlay
        whenever(primaryOverlay.runtimeWarningMessage()).thenReturn("fallback warning")

        fixture.manager.setForecastOverlay(
            enabled = true,
            primaryTileSpec = tileSpec(),
            primaryLegendSpec = null,
            windOverlayEnabled = false,
            windTileSpec = null,
            windLegendSpec = null,
            opacity = 0.7f,
            windOverlayScale = 1.0f,
            windDisplayMode = ForecastWindDisplayMode.ARROW
        )
        fixture.manager.clearForecastOverlay()

        assertNull(fixture.manager.forecastRuntimeWarningMessage.value)
    }

    @Test
    fun setForecastOverlay_whenMapUnavailable_clearsStaleRuntimeWarning() {
        val fixture = createFixture()
        val map: MapLibreMap = mock()
        val primaryOverlay: ForecastRasterOverlay = mock()
        val windOverlay: ForecastRasterOverlay = mock()

        fixture.mapState.mapLibreMap = map
        fixture.mapState.forecastOverlay = primaryOverlay
        fixture.mapState.forecastWindOverlay = windOverlay
        whenever(primaryOverlay.runtimeWarningMessage()).thenReturn("fallback warning")

        fixture.manager.setForecastOverlay(
            enabled = true,
            primaryTileSpec = tileSpec(),
            primaryLegendSpec = null,
            windOverlayEnabled = false,
            windTileSpec = null,
            windLegendSpec = null,
            opacity = 0.7f,
            windOverlayScale = 1.0f,
            windDisplayMode = ForecastWindDisplayMode.ARROW
        )
        assertEquals("fallback warning", fixture.manager.forecastRuntimeWarningMessage.value)

        fixture.mapState.mapLibreMap = null
        fixture.manager.setForecastOverlay(
            enabled = true,
            primaryTileSpec = tileSpec(),
            primaryLegendSpec = null,
            windOverlayEnabled = false,
            windTileSpec = null,
            windLegendSpec = null,
            opacity = 0.7f,
            windOverlayScale = 1.0f,
            windDisplayMode = ForecastWindDisplayMode.ARROW
        )

        assertNull(fixture.manager.forecastRuntimeWarningMessage.value)
    }

    @Test
    fun setForecastOverlay_disabled_clearsWarningEvenWhenMapUnavailable() {
        val fixture = createFixture()
        val map: MapLibreMap = mock()
        val primaryOverlay: ForecastRasterOverlay = mock()
        val windOverlay: ForecastRasterOverlay = mock()

        fixture.mapState.mapLibreMap = map
        fixture.mapState.forecastOverlay = primaryOverlay
        fixture.mapState.forecastWindOverlay = windOverlay
        whenever(primaryOverlay.runtimeWarningMessage()).thenReturn("fallback warning")

        fixture.manager.setForecastOverlay(
            enabled = true,
            primaryTileSpec = tileSpec(),
            primaryLegendSpec = null,
            windOverlayEnabled = false,
            windTileSpec = null,
            windLegendSpec = null,
            opacity = 0.7f,
            windOverlayScale = 1.0f,
            windDisplayMode = ForecastWindDisplayMode.ARROW
        )
        assertEquals("fallback warning", fixture.manager.forecastRuntimeWarningMessage.value)

        fixture.mapState.mapLibreMap = null
        fixture.manager.setForecastOverlay(
            enabled = false,
            primaryTileSpec = null,
            primaryLegendSpec = null,
            windOverlayEnabled = false,
            windTileSpec = null,
            windLegendSpec = null,
            opacity = 0.7f,
            windOverlayScale = 1.0f,
            windDisplayMode = ForecastWindDisplayMode.ARROW
        )

        assertNull(fixture.manager.forecastRuntimeWarningMessage.value)
    }

    private fun tileSpec(): ForecastTileSpec = ForecastTileSpec(
        urlTemplate = "https://tiles.example/{z}/{x}/{y}.pbf",
        minZoom = 3,
        maxZoom = 5
    )

    private fun createFixture(): Fixture {
        val mapState = MapScreenState()
        val mapStateStore = MapStateStore(initialStyleName = "Terrain")
        val manager = MapOverlayManager(
            context = mock<Context>(),
            mapState = mapState,
            mapStateReader = mapStateStore,
            taskRenderSyncCoordinator = mock<TaskRenderSyncCoordinator>(),
            taskWaypointCountProvider = { 0 },
            stateActions = MapStateActionsDelegate(mapStateStore),
            snailTrailManager = mock<SnailTrailManager>(),
            coroutineScope = TestScope(),
            airspaceUseCase = mock<AirspaceUseCase>(),
            waypointFilesUseCase = mock<WaypointFilesUseCase>()
        )
        return Fixture(manager = manager, mapState = mapState)
    }

    private data class Fixture(
        val manager: MapOverlayManager,
        val mapState: MapScreenState
    )
}
