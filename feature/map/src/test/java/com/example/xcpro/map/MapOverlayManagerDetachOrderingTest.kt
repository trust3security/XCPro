package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.weather.rain.WeatherRadarRenderOptions
import com.example.xcpro.weather.rain.WeatherRadarStatusCode
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MapOverlayManagerDetachOrderingTest {

    @Test
    fun onMapDetached_clearsDeferredWeatherRainWithoutInteractionReleaseReplay() {
        val fixture = createFixture()
        val frame1 = frameSelection(7_000L)
        val frame2 = frameSelection(7_600L)

        fixture.manager.setMapInteractionActive(true)
        fixture.manager.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame1,
            opacity = 0.61f,
            transitionDurationMs = 220L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )

        fixture.nowMonoMs += 100L
        fixture.manager.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame2,
            opacity = 0.61f,
            transitionDurationMs = 220L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )

        fixture.manager.onMapDetached()

        verify(fixture.weatherOverlay, times(1)).render(
            frameSelection = any(),
            opacity = any(),
            transitionDurationMs = any()
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
        val weatherOverlay: WeatherRainOverlay = mock()
        val map: MapLibreMap = mock()
        val fixture = Fixture(
            weatherOverlay = weatherOverlay,
            mapState = mapState
        )
        mapState.mapLibreMap = map
        mapState.weatherRainOverlay = weatherOverlay
        fixture.manager = MapOverlayManager(
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
            renderSurfaceDiagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { fixture.nowMonoMs }),
            monoTimeMs = { fixture.nowMonoMs }
        )
        return fixture
    }

    private class Fixture(
        val weatherOverlay: WeatherRainOverlay,
        val mapState: MapScreenState
    ) {
        lateinit var manager: MapOverlayManager
        var nowMonoMs: Long = 10_000L
    }
}
