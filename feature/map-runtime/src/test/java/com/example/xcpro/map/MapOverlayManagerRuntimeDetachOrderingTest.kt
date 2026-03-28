package com.example.xcpro.map

import android.content.Context
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

class MapOverlayManagerRuntimeDetachOrderingTest {

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
        val trafficRuntimeState = FakeTrafficOverlayRuntimeState()
        val forecastWeatherRuntimeState = FakeForecastWeatherOverlayRuntimeState()
        val map: MapLibreMap = mock()
        val weatherOverlay: WeatherRainOverlay = mock()
        val fixture = Fixture(
            trafficRuntimeState = trafficRuntimeState,
            forecastWeatherRuntimeState = forecastWeatherRuntimeState,
            weatherOverlay = weatherOverlay
        )
        trafficRuntimeState.mapLibreMap = map
        forecastWeatherRuntimeState.mapLibreMap = map
        forecastWeatherRuntimeState.weatherRainOverlay = weatherOverlay
        fixture.manager = TestMapOverlayManagerRuntime(
            context = mock<Context>(),
            taskRenderSyncCoordinator = mock<TaskRenderSyncCoordinator>(),
            coroutineScope = TestScope(),
            trafficRuntimeState = trafficRuntimeState,
            forecastWeatherRuntimeState = forecastWeatherRuntimeState,
            nowMonoMs = { fixture.nowMonoMs }
        )
        return fixture
    }

    private class Fixture(
        val trafficRuntimeState: FakeTrafficOverlayRuntimeState,
        val forecastWeatherRuntimeState: FakeForecastWeatherOverlayRuntimeState,
        val weatherOverlay: WeatherRainOverlay
    ) {
        lateinit var manager: TestMapOverlayManagerRuntime
        var nowMonoMs: Long = 10_000L
    }

    private class TestMapOverlayManagerRuntime(
        context: Context,
        taskRenderSyncCoordinator: TaskRenderSyncCoordinator,
        coroutineScope: TestScope,
        trafficRuntimeState: TrafficOverlayRuntimeState,
        forecastWeatherRuntimeState: ForecastWeatherOverlayRuntimeState,
        nowMonoMs: () -> Long
    ) : MapOverlayManagerRuntime(
        context = context,
        taskRenderSyncCoordinator = taskRenderSyncCoordinator,
        coroutineScope = coroutineScope,
        trafficRuntimeState = trafficRuntimeState,
        forecastWeatherRuntimeState = forecastWeatherRuntimeState,
        adsbTrafficOverlayFactory = { _, _, _ -> mock() },
        ognTrafficOverlayFactory = { _, _, _, _ -> mock() },
        ognTargetRingOverlayFactory = { _, _ -> mock() },
        ognTargetLineOverlayFactory = { _ -> mock() },
        ognThermalOverlayFactory = { _ -> mock() },
        ognGliderTrailOverlayFactory = { _ -> mock() },
        nowMonoMs = nowMonoMs
    ) {
        init {
            attachShellPorts(
                lifecyclePort = object : MapOverlayRuntimeLifecyclePort {
                    override fun toggleDistanceCircles() = Unit
                    override fun refreshAirspace(map: MapLibreMap?) = Unit
                    override fun refreshWaypoints(map: MapLibreMap?) = Unit
                    override fun plotSavedTask(map: MapLibreMap?) = Unit
                    override fun clearTaskOverlays(map: MapLibreMap?) = Unit
                    override fun onMapStyleChanged(map: MapLibreMap?) = Unit
                    override fun initializeOverlays(map: MapLibreMap?) = Unit
                    override fun initializeTrafficOverlays(map: MapLibreMap?) = Unit
                    override fun onZoomChanged(map: MapLibreMap?) = Unit
                    override fun onMapDetached() = Unit
                },
                statusReporter = object : MapOverlayRuntimeStatusReporter {
                    override fun getOverlayStatus(): String = ""
                }
            )
        }
    }

    private class FakeForecastWeatherOverlayRuntimeState : ForecastWeatherOverlayRuntimeState {
        override var mapLibreMap: MapLibreMap? = null
        override var forecastOverlay: ForecastRasterOverlay? = null
        override var forecastWindOverlay: ForecastRasterOverlay? = null
        override var skySightSatelliteOverlay: SkySightSatelliteOverlay? = null
        override var weatherRainOverlay: WeatherRainOverlay? = null
    }

    private class FakeTrafficOverlayRuntimeState : TrafficOverlayRuntimeState {
        override var mapLibreMap: MapLibreMap? = null
        override val blueLocationLayerId: String = "blue"
        override fun bringBlueLocationOverlayToFront() = Unit
        override var ognTrafficOverlay: OgnTrafficOverlayHandle? = null
        override var ognTargetRingOverlay: OgnTargetRingOverlayHandle? = null
        override var ognTargetLineOverlay: OgnTargetLineOverlayHandle? = null
        override var ognOwnshipTargetBadgeOverlay: OgnOwnshipTargetBadgeOverlayHandle? = null
        override var ognThermalOverlay: OgnThermalOverlayHandle? = null
        override var ognGliderTrailOverlay: OgnGliderTrailOverlayHandle? = null
        override var adsbTrafficOverlay: AdsbTrafficOverlayHandle? = null
    }
}
