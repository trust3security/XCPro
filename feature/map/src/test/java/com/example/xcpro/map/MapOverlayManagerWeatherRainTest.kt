package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.weather.rain.WEATHER_RAIN_STALE_DIMMED_OPACITY_MAX
import com.example.xcpro.weather.rain.WeatherRadarRenderOptions
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import com.example.xcpro.weather.rain.WeatherRadarStatusCode
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.mockito.Mockito.clearInvocations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MapOverlayManagerWeatherRainTest {

    @Test
    fun setWeatherRainOverlay_statusOnlyChange_doesNotRenderAgain() {
        val fixture = createFixture()
        val map: MapLibreMap = mock()
        val weatherOverlay: WeatherRainOverlay = mock()
        val blueOverlay: BlueLocationOverlay = mock()
        val frame = frameSelection(1_000L)

        fixture.mapState.mapLibreMap = map
        fixture.mapState.weatherRainOverlay = weatherOverlay
        fixture.mapState.blueLocationOverlay = blueOverlay

        fixture.manager.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame,
            opacity = 0.60f,
            transitionDurationMs = 180L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )
        fixture.manager.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame,
            opacity = 0.60f,
            transitionDurationMs = 180L,
            statusCode = WeatherRadarStatusCode.NETWORK_ERROR,
            stale = false
        )

        verify(weatherOverlay, times(1)).render(
            frameSelection = eq(frame),
            opacity = eq(0.60f),
            transitionDurationMs = eq(180L)
        )
        verify(blueOverlay, times(1)).bringToFront()
        assertTrue(fixture.manager.getOverlayStatus().contains("Weather Rain Status: NETWORK_ERROR"))
    }

    @Test
    fun setWeatherRainOverlay_staleChange_reappliesWithDimmedOpacity() {
        val fixture = createFixture()
        val map: MapLibreMap = mock()
        val weatherOverlay: WeatherRainOverlay = mock()
        val frame = frameSelection(2_000L)

        fixture.mapState.mapLibreMap = map
        fixture.mapState.weatherRainOverlay = weatherOverlay

        fixture.manager.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame,
            opacity = 0.85f,
            transitionDurationMs = 220L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )
        fixture.manager.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame,
            opacity = 0.85f,
            transitionDurationMs = 220L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = true
        )

        val opacityCaptor = argumentCaptor<Float>()
        verify(weatherOverlay, times(2)).render(
            frameSelection = eq(frame),
            opacity = opacityCaptor.capture(),
            transitionDurationMs = eq(220L)
        )
        assertEquals(0.85f, opacityCaptor.firstValue, 0.0001f)
        assertEquals(WEATHER_RAIN_STALE_DIMMED_OPACITY_MAX, opacityCaptor.secondValue, 0.0001f)
    }

    @Test
    fun clearWeatherRainOverlay_resetsDedupeAndAllowsReapply() {
        val fixture = createFixture()
        val map: MapLibreMap = mock()
        val weatherOverlay: WeatherRainOverlay = mock()
        val frame = frameSelection(3_000L)

        fixture.mapState.mapLibreMap = map
        fixture.mapState.weatherRainOverlay = weatherOverlay

        fixture.manager.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame,
            opacity = 0.55f,
            transitionDurationMs = 140L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )
        fixture.manager.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame,
            opacity = 0.55f,
            transitionDurationMs = 140L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )
        verify(weatherOverlay, times(1)).render(
            frameSelection = eq(frame),
            opacity = eq(0.55f),
            transitionDurationMs = eq(140L)
        )

        fixture.manager.clearWeatherRainOverlay()
        verify(weatherOverlay, times(1)).clear()

        fixture.manager.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame,
            opacity = 0.55f,
            transitionDurationMs = 140L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )
        verify(weatherOverlay, times(2)).render(
            frameSelection = eq(frame),
            opacity = eq(0.55f),
            transitionDurationMs = eq(140L)
        )
    }

    @Test
    fun reapplyWeatherRainOverlay_afterOverlayReplacement_reusesLatestRuntimeConfig() {
        val fixture = createFixture()
        val map: MapLibreMap = mock()
        val oldWeatherOverlay: WeatherRainOverlay = mock()
        val newWeatherOverlay: WeatherRainOverlay = mock()
        val frame = frameSelection(4_000L)

        fixture.mapState.mapLibreMap = map
        fixture.mapState.weatherRainOverlay = oldWeatherOverlay

        fixture.manager.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame,
            opacity = 0.62f,
            transitionDurationMs = 160L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )
        clearInvocations(oldWeatherOverlay)
        fixture.mapState.weatherRainOverlay = newWeatherOverlay
        fixture.manager.reapplyWeatherRainOverlay()

        verify(newWeatherOverlay, times(1)).render(
            frameSelection = eq(frame),
            opacity = eq(0.62f),
            transitionDurationMs = eq(160L)
        )
    }

    @Test
    fun setWeatherRainOverlay_renderFailure_allowsSameConfigRetry() {
        val fixture = createFixture()
        val map: MapLibreMap = mock()
        val weatherOverlay: WeatherRainOverlay = mock()
        val frame = frameSelection(5_000L)

        fixture.mapState.mapLibreMap = map
        fixture.mapState.weatherRainOverlay = weatherOverlay

        var calls = 0
        doAnswer {
            calls += 1
            if (calls == 1) {
                throw IllegalStateException("rain render failed")
            }
            Unit
        }.whenever(weatherOverlay).render(any(), any(), any())

        fixture.manager.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame,
            opacity = 0.58f,
            transitionDurationMs = 190L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )
        fixture.manager.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame,
            opacity = 0.58f,
            transitionDurationMs = 190L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )

        verify(weatherOverlay, times(2)).render(
            frameSelection = eq(frame),
            opacity = eq(0.58f),
            transitionDurationMs = eq(190L)
        )
    }

    private fun frameSelection(epochSec: Long): WeatherRainFrameSelection {
        return WeatherRainFrameSelection(
            hostUrl = "https://tilecache.rainviewer.com",
            framePath = "/v2/radar/$epochSec",
            frameTimeEpochSec = epochSec,
            renderOptions = WeatherRadarRenderOptions()
        )
    }

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
            waypointFilesUseCase = mock<WaypointFilesUseCase>(),
            monoTimeMs = { 1_000L }
        )
        return Fixture(
            manager = manager,
            mapState = mapState
        )
    }

    private data class Fixture(
        val manager: MapOverlayManager,
        val mapState: MapScreenState
    )
}
