package com.example.xcpro.map

import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.forecast.ForecastTileFormat
import com.example.xcpro.forecast.ForecastTileSpec
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.weather.rain.WeatherRadarRenderOptions
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.VectorSource
import org.mockito.Answers
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.withSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HotspotsOverlayPolicyTest {

    @Test
    fun thermalToggle_autoEnablesOgnOverlayWhenTurningOn() = runTest {
        val ognTrafficUseCase: OgnTrafficUseCase = mock()
        val adsbTrafficUseCase: AdsbTrafficUseCase = mock()
        whenever(ognTrafficUseCase.setOverlayEnabled(true)).thenReturn(Unit)
        whenever(ognTrafficUseCase.setShowThermalsEnabled(true)).thenReturn(Unit)

        val coordinator = MapScreenTrafficCoordinator(
            scope = this,
            allowSensorStart = MutableStateFlow(true),
            isMapVisible = MutableStateFlow(true),
            ognOverlayEnabled = MutableStateFlow(false),
            adsbOverlayEnabled = MutableStateFlow(false),
            mapState = MapStateStore(initialStyleName = "Terrain"),
            mapLocation = MutableStateFlow<MapLocationUiModel?>(null),
            isFlying = MutableStateFlow(false),
            ownshipAltitudeMeters = MutableStateFlow<Double?>(null),
            ownshipIsCircling = MutableStateFlow(false),
            circlingFeatureEnabled = MutableStateFlow(false),
            adsbMaxDistanceKm = MutableStateFlow(10),
            adsbVerticalAboveMeters = MutableStateFlow(500.0),
            adsbVerticalBelowMeters = MutableStateFlow(500.0),
            rawOgnTargets = MutableStateFlow<List<OgnTrafficTarget>>(emptyList()),
            selectedOgnId = MutableStateFlow<String?>(null),
            showSciaEnabled = MutableStateFlow(false),
            showThermalsEnabled = MutableStateFlow(false),
            thermalHotspots = MutableStateFlow<List<OgnThermalHotspot>>(emptyList()),
            selectedThermalId = MutableStateFlow<String?>(null),
            rawAdsbTargets = MutableStateFlow<List<AdsbTrafficUiModel>>(emptyList()),
            selectedAdsbId = MutableStateFlow<Icao24?>(null),
            ognTrafficUseCase = ognTrafficUseCase,
            adsbTrafficUseCase = adsbTrafficUseCase,
            emitUiEffect = {}
        )

        coordinator.onToggleOgnThermals()
        advanceUntilIdle()

        verify(ognTrafficUseCase).setOverlayEnabled(true)
        verify(ognTrafficUseCase).setShowThermalsEnabled(true)
    }

    @Test
    fun forecastAnchors_includeThermalLayers() {
        val map: MapLibreMap = mock()
        val style: Style = mock()
        whenever(map.style).thenReturn(style)

        mockConstruction(RasterSource::class.java).use {
            mockConstruction(
                RasterLayer::class.java,
                withSettings().defaultAnswer(Answers.RETURNS_SELF)
            ).use {
                whenever(style.getLayer(any())).thenAnswer { invocation ->
                    if (invocation.getArgument<String>(0) == "ogn-thermal-label-layer") {
                        mock<org.maplibre.android.style.layers.Layer>()
                    } else {
                        null
                    }
                }

                val overlay = ForecastRasterOverlay(map)
                overlay.render(
                    tileSpec = ForecastTileSpec(
                        urlTemplate = "https://tiles.example/{z}/{x}/{y}.png",
                        minZoom = 0,
                        maxZoom = 8,
                        format = ForecastTileFormat.RASTER
                    ),
                    opacity = 0.7f,
                    windOverlayScale = 1.0f,
                    windDisplayMode = ForecastWindDisplayMode.ARROW,
                    legendSpec = null
                )

                verify(style, atLeastOnce()).addLayerBelow(any(), eq("ogn-thermal-label-layer"))
            }
        }
    }

    @Test
    fun weatherAnchors_includeThermalLayers() {
        val map: MapLibreMap = mock()
        val style: Style = mock()
        whenever(map.style).thenReturn(style)

        mockConstruction(RasterSource::class.java).use {
            mockConstruction(
                RasterLayer::class.java,
                withSettings().defaultAnswer(Answers.RETURNS_SELF)
            ).use {
                whenever(style.getLayer(any())).thenAnswer { invocation ->
                    if (invocation.getArgument<String>(0) == "ogn-thermal-label-layer") {
                        mock<org.maplibre.android.style.layers.Layer>()
                    } else {
                        null
                    }
                }

                val overlay = WeatherRainOverlay(map)
                overlay.render(
                    frameSelection = WeatherRainFrameSelection(
                        hostUrl = "https://tilecache.rainviewer.com",
                        framePath = "/v2/radar/1000",
                        frameTimeEpochSec = 1_000L,
                        renderOptions = WeatherRadarRenderOptions()
                    ),
                    opacity = 0.6f,
                    transitionDurationMs = 0L
                )

                verify(style, atLeastOnce()).addLayerBelow(any(), eq("ogn-thermal-label-layer"))
                verify(style, never()).getLayer("forecast-secondary-raster-layer")
                verify(style, never()).getLayer("forecast-secondary-vector-fill-layer")
            }
        }
    }

    @Test
    fun skySightAnchors_includeThermalLayers() {
        val map: MapLibreMap = mock()
        val style: Style = mock()
        whenever(map.style).thenReturn(style)

        mockConstruction(RasterSource::class.java).use {
            mockConstruction(
                RasterLayer::class.java,
                withSettings().defaultAnswer(Answers.RETURNS_SELF)
            ).use {
                mockConstruction(VectorSource::class.java).use {
                    whenever(style.getLayer(any())).thenAnswer { invocation ->
                        if (invocation.getArgument<String>(0) == "ogn-thermal-label-layer") {
                            mock<org.maplibre.android.style.layers.Layer>()
                        } else {
                            null
                        }
                    }

                    val overlay = SkySightSatelliteOverlay(map)
                    overlay.render(
                        config = SkySightSatelliteRenderConfig(
                            enabled = true,
                            showSatelliteImagery = true,
                            showRadar = false,
                            showLightning = false,
                            animate = false,
                            historyFrameCount = 1,
                            referenceTimeUtcMs = 0L
                        )
                    )

                    verify(style, atLeastOnce()).addLayerBelow(any(), eq("ogn-thermal-label-layer"))
                    verify(style, never()).getLayer("forecast-secondary-raster-layer")
                    verify(style, never()).getLayer("forecast-secondary-vector-fill-layer")
                }
            }
        }
    }

    @Test
    fun skySightAnchors_placeSatelliteBelowDynamicRainFrames() {
        val map: MapLibreMap = mock()
        val style: Style = mock()
        whenever(map.style).thenReturn(style)

        mockConstruction(RasterSource::class.java).use {
            mockConstruction(
                RasterLayer::class.java,
                withSettings().defaultAnswer(Answers.RETURNS_SELF)
            ).use {
                mockConstruction(VectorSource::class.java).use {
                    val rainLayer: org.maplibre.android.style.layers.Layer = mock()
                    whenever(rainLayer.id).thenReturn("weather-rain-layer-1000")
                    whenever(style.layers).thenReturn(mutableListOf(rainLayer))
                    whenever(style.getLayer(any())).thenReturn(null)

                    val overlay = SkySightSatelliteOverlay(map)
                    overlay.render(
                        config = SkySightSatelliteRenderConfig(
                            enabled = true,
                            showSatelliteImagery = true,
                            showRadar = false,
                            showLightning = false,
                            animate = false,
                            historyFrameCount = 1,
                            referenceTimeUtcMs = 0L
                        )
                    )

                    verify(style, atLeastOnce()).addLayerBelow(any(), eq("weather-rain-layer-1000"))
                }
            }
        }
    }
}
