package com.example.xcpro.map

import com.example.xcpro.map.WEATHER_RAIN_INTERACTION_MIN_APPLY_INTERVAL_MS
import com.example.xcpro.weather.rain.WeatherRadarRenderOptions
import com.example.xcpro.weather.rain.WeatherRadarStatusCode
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MapOverlayManagerRuntimeForecastWeatherDelegateWeatherRainTest {

    @Test
    fun setWeatherRainOverlay_deferredOlderFrame_notReplayedAfterNewerFrameApplied() {
        val fixture = createFixture()
        val frame1 = frameSelection(1_000L)
        val frame2 = frameSelection(1_600L)
        val frame3 = frameSelection(2_200L)

        fixture.delegate.setMapInteractionActive(true)
        fixture.delegate.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame1,
            opacity = 0.60f,
            transitionDurationMs = 240L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )
        fixture.nowMonoMs += 100L
        fixture.delegate.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame2,
            opacity = 0.60f,
            transitionDurationMs = 240L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )
        fixture.nowMonoMs += WEATHER_RAIN_INTERACTION_MIN_APPLY_INTERVAL_MS + 100L
        fixture.delegate.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame3,
            opacity = 0.60f,
            transitionDurationMs = 240L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )

        fixture.delegate.setMapInteractionActive(false)

        val frameCaptor = argumentCaptor<WeatherRainFrameSelection>()
        verify(fixture.weatherOverlay, times(2)).render(
            frameSelection = frameCaptor.capture(),
            opacity = any(),
            transitionDurationMs = any()
        )
        assertEquals(
            listOf(frame1.frameTimeEpochSec, frame3.frameTimeEpochSec),
            frameCaptor.allValues.map { captured -> captured.frameTimeEpochSec }
        )
    }

    @Test
    fun setWeatherRainOverlay_disableDuringInteraction_remainsDisabledAfterInteractionEnds() {
        val fixture = createFixture()
        val frame1 = frameSelection(3_000L)
        val frame2 = frameSelection(3_600L)

        fixture.delegate.setMapInteractionActive(true)
        fixture.delegate.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame1,
            opacity = 0.55f,
            transitionDurationMs = 200L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )
        fixture.nowMonoMs += 100L
        fixture.delegate.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame2,
            opacity = 0.55f,
            transitionDurationMs = 200L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )
        fixture.nowMonoMs += 100L
        fixture.delegate.setWeatherRainOverlay(
            enabled = false,
            frameSelection = null,
            opacity = 0.55f,
            transitionDurationMs = 200L,
            statusCode = WeatherRadarStatusCode.NO_METADATA,
            stale = true
        )

        fixture.delegate.setMapInteractionActive(false)

        verify(fixture.weatherOverlay, times(1)).render(
            frameSelection = any(),
            opacity = any(),
            transitionDurationMs = any()
        )
        verify(fixture.weatherOverlay, times(1)).clear()
    }

    @Test
    fun setMapInteractionActive_flushWithNullMap_consumesDeferredConfigWithoutLaterReplay() {
        val fixture = createFixture()
        val frame1 = frameSelection(5_000L)
        val frame2 = frameSelection(5_600L)

        fixture.delegate.setMapInteractionActive(true)
        fixture.delegate.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame1,
            opacity = 0.50f,
            transitionDurationMs = 180L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )
        fixture.nowMonoMs += 100L
        fixture.delegate.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame2,
            opacity = 0.50f,
            transitionDurationMs = 180L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )

        fixture.runtimeState.mapLibreMap = null
        fixture.delegate.setMapInteractionActive(false)
        fixture.runtimeState.mapLibreMap = fixture.map
        fixture.delegate.setMapInteractionActive(true)
        fixture.delegate.setMapInteractionActive(false)

        verify(fixture.weatherOverlay, times(1)).render(
            frameSelection = any(),
            opacity = any(),
            transitionDurationMs = any()
        )
    }

    @Test
    fun onMapStyleChanged_reappliesLatestWeatherRainConfigToReplacementOverlay() {
        val fixture = createFixture()
        val frame = frameSelection(6_000L)

        fixture.delegate.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frame,
            opacity = 0.57f,
            transitionDurationMs = 210L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )

        val forecastOverlayConstruction = mockConstruction(ForecastRasterOverlay::class.java)
        val skySightOverlayConstruction = mockConstruction(SkySightSatelliteOverlay::class.java)
        val weatherOverlayConstruction = mockConstruction(WeatherRainOverlay::class.java)
        try {
            fixture.delegate.onMapStyleChanged(fixture.map)

            val replacementOverlay = weatherOverlayConstruction.constructed().single()
            verify(fixture.weatherOverlay, times(1)).cleanup()
            verify(replacementOverlay, times(1)).render(
                frameSelection = eq(frame),
                opacity = eq(0.57f),
                transitionDurationMs = eq(210L)
            )
        } finally {
            weatherOverlayConstruction.close()
            skySightOverlayConstruction.close()
            forecastOverlayConstruction.close()
        }
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
        val runtimeState = FakeForecastWeatherOverlayRuntimeState()
        val map: MapLibreMap = mock()
        val weatherOverlay: WeatherRainOverlay = mock()
        val fixture = Fixture(
            runtimeState = runtimeState,
            map = map,
            weatherOverlay = weatherOverlay
        )
        runtimeState.mapLibreMap = map
        runtimeState.weatherRainOverlay = weatherOverlay
        return fixture
    }

    private data class Fixture(
        val runtimeState: FakeForecastWeatherOverlayRuntimeState,
        val map: MapLibreMap,
        val weatherOverlay: WeatherRainOverlay
    ) {
        var nowMonoMs: Long = 10_000L
        val delegate = MapOverlayManagerRuntimeForecastWeatherDelegate(
            runtimeState = runtimeState,
            bringTrafficOverlaysToFront = {},
            onSatelliteContrastIconsChanged = {},
            nowMonoMs = { nowMonoMs }
        )
    }

    private class FakeForecastWeatherOverlayRuntimeState : ForecastWeatherOverlayRuntimeState {
        override var mapLibreMap: MapLibreMap? = null
        override var forecastOverlay: ForecastRasterOverlay? = null
        override var forecastWindOverlay: ForecastRasterOverlay? = null
        override var skySightSatelliteOverlay: SkySightSatelliteOverlay? = null
        override var weatherRainOverlay: WeatherRainOverlay? = null
    }
}
