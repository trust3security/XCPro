package com.example.xcpro.map

import com.example.xcpro.map.AdsbTrafficUiModel
import com.example.xcpro.map.Icao24
import com.example.xcpro.forecast.ForecastTileFormat
import com.example.xcpro.forecast.ForecastTileSpec
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.map.OgnThermalHotspot
import com.example.xcpro.map.OgnTrafficTarget
import com.example.xcpro.weather.rain.WeatherRadarRenderOptions
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
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
        try {
            val ognTrafficFacade: OgnTrafficFacade = mock()
            val adsbTrafficFacade: AdsbTrafficFacade = mock()
            whenever(ognTrafficFacade.setOverlayEnabled(true)).thenReturn(Unit)
            whenever(ognTrafficFacade.setShowThermalsEnabled(true)).thenReturn(Unit)

            val mapLocation = MutableStateFlow<MapLocationUiModel?>(null)
            val coordinator = MapScreenTrafficCoordinator(
                scope = this,
                streamingGate = createTrafficStreamingGatePort(
                    allowSensorStart = MutableStateFlow(true),
                    isMapVisible = MutableStateFlow(true)
                ),
                ognOverlayEnabled = MutableStateFlow(false),
                adsbOverlayEnabled = MutableStateFlow(false),
                viewportPort = createTrafficViewportPort(MapStateStore(initialStyleName = "Terrain")),
                ownshipPort = createTrafficOwnshipPort(
                    scope = this,
                    mapLocation = mapLocation,
                    isFlying = MutableStateFlow(false),
                    ownshipAltitudeMeters = MutableStateFlow<Double?>(null),
                    ownshipIsCircling = MutableStateFlow(false),
                    circlingFeatureEnabled = MutableStateFlow(false)
                ),
                adsbFilterPort = createAdsbTrafficFilterPort(
                    AdsbFilterStateFlows(
                        maxDistanceKm = MutableStateFlow(10),
                        verticalAboveMeters = MutableStateFlow(500.0),
                        verticalBelowMeters = MutableStateFlow(500.0)
                    )
                ),
                rawOgnTargets = MutableStateFlow<List<OgnTrafficTarget>>(emptyList()),
                selectionPort = createTrafficSelectionPort(
                    selectedOgnId = MutableStateFlow<String?>(null),
                    selectedThermalId = MutableStateFlow<String?>(null),
                    selectedAdsbId = MutableStateFlow<Icao24?>(null)
                ),
                ognTargetEnabled = MutableStateFlow(false),
                ognTargetAircraftKey = MutableStateFlow<String?>(null),
                ognSuppressedTargetIds = MutableStateFlow(emptySet()),
                showSciaEnabled = MutableStateFlow(false),
                showThermalsEnabled = MutableStateFlow(false),
                thermalHotspots = MutableStateFlow<List<OgnThermalHotspot>>(emptyList()),
                rawAdsbTargets = MutableStateFlow<List<AdsbTrafficUiModel>>(emptyList()),
                ognTrafficFacade = ognTrafficFacade,
                adsbTrafficFacade = adsbTrafficFacade,
                userMessagePort = object : TrafficUserMessagePort {
                    override suspend fun showToast(message: String) = Unit
                }
            )

            coordinator.onToggleOgnThermals()
            advanceUntilIdle()

            verify(ognTrafficFacade).setOverlayEnabled(true)
            verify(ognTrafficFacade).setShowThermalsEnabled(true)
        } finally {
            coroutineContext.cancelChildren()
        }
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
