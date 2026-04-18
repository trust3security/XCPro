package com.trust3.xcpro.map

import com.trust3.xcpro.weather.rain.WeatherRadarRenderOptions
import com.trust3.xcpro.weather.rain.WeatherRainFrameSelection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.mockito.Answers
import org.mockito.MockedConstruction
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.withSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WeatherRainOverlayPolicyTest {

    @Test
    fun render_zeroDurationFrameSwitch_updatesBothFrameLayerIds() {
        val map: MapLibreMap = mock()
        val style: Style = mock()
        whenever(map.style).thenReturn(style)

        withLayerAndSourceConstructionMocks { layerConstruction ->
            whenever(style.getLayer(any())).thenAnswer {
                layerConstruction.constructed().lastOrNull()
            }

            val overlay = WeatherRainOverlay(map)

            overlay.render(
                frameSelection = frameSelection(1_000L),
                opacity = 0.70f,
                transitionDurationMs = 0L
            )
            overlay.render(
                frameSelection = frameSelection(1_060L),
                opacity = 0.70f,
                transitionDurationMs = 0L
            )

            verify(style, atLeastOnce()).getLayer("weather-rain-layer-1000")
            verify(style, atLeastOnce()).getLayer("weather-rain-layer-1060")
        }
    }

    @Test
    fun render_moreThanCacheCap_prunesOldestFrameArtifacts() {
        val map: MapLibreMap = mock()
        val style: Style = mock()
        whenever(map.style).thenReturn(style)

        withLayerAndSourceConstructionMocks { layerConstruction ->
            whenever(style.getLayer(any())).thenAnswer {
                layerConstruction.constructed().lastOrNull()
            }

            val overlay = WeatherRainOverlay(map)

            (1L..30L).forEach { frameEpochSec ->
                overlay.render(
                    frameSelection = frameSelection(frameEpochSec),
                    opacity = 0.60f,
                    transitionDurationMs = 0L
                )
            }

            (1L..6L).forEach { prunedFrame ->
                verify(style, atLeastOnce()).removeLayer("weather-rain-layer-$prunedFrame")
                verify(style, atLeastOnce()).removeSource("weather-rain-source-$prunedFrame")
            }
        }
    }

    @Test
    fun clear_removesCachedArtifactsAndLegacyArtifacts() {
        val map: MapLibreMap = mock()
        val style: Style = mock()
        whenever(map.style).thenReturn(style)

        withLayerAndSourceConstructionMocks { layerConstruction ->
            whenever(style.getLayer(any())).thenAnswer {
                layerConstruction.constructed().lastOrNull()
            }

            val overlay = WeatherRainOverlay(map)
            val frameEpochSec = 5_000L
            overlay.render(
                frameSelection = frameSelection(frameEpochSec),
                opacity = 0.50f,
                transitionDurationMs = 0L
            )

            overlay.clear()

            verify(style, atLeastOnce()).removeLayer("weather-rain-layer-$frameEpochSec")
            verify(style, atLeastOnce()).removeSource("weather-rain-source-$frameEpochSec")
            verify(style, atLeastOnce()).removeLayer("weather-rain-layer")
            verify(style, atLeastOnce()).removeSource("weather-rain-source")
        }
    }

    @Test
    fun render_sameFrameTileSizeChange_rebuildsRasterSourceWithUpdatedTileSize() {
        val map: MapLibreMap = mock()
        val style: Style = mock()
        whenever(map.style).thenReturn(style)
        val createdTileSizes = mutableListOf<Int>()

        mockConstruction(
            RasterSource::class.java
        ) { _, context ->
            createdTileSizes += context.arguments()[2] as Int
        }.use {
            mockConstruction(
                RasterLayer::class.java,
                withSettings().defaultAnswer(Answers.RETURNS_SELF)
            ).use { layerConstruction ->
                whenever(style.getLayer(any())).thenAnswer {
                    layerConstruction.constructed().lastOrNull()
                }

                val overlay = WeatherRainOverlay(map)
                val frameEpochSec = 9_500L

                overlay.render(
                    frameSelection = frameSelection(
                        epochSec = frameEpochSec,
                        tileSizePx = 256
                    ),
                    opacity = 0.60f,
                    transitionDurationMs = 0L
                )
                overlay.render(
                    frameSelection = frameSelection(
                        epochSec = frameEpochSec,
                        tileSizePx = 512
                    ),
                    opacity = 0.60f,
                    transitionDurationMs = 0L
                )

                assertEquals(listOf(256, 512), createdTileSizes)
                verify(style, atLeastOnce()).removeSource("weather-rain-source-$frameEpochSec")
                verify(style, atLeastOnce()).removeLayer("weather-rain-layer-$frameEpochSec")
            }
        }
    }

    @Test
    fun render_whenOnlySkySightLayersExist_placesRainAboveSkySight() {
        val map: MapLibreMap = mock()
        val style: Style = mock()
        val skySightLayer: org.maplibre.android.style.layers.Layer = mock()
        whenever(map.style).thenReturn(style)
        whenever(skySightLayer.id).thenReturn("skysight-sat-layer-0")
        whenever(style.layers).thenReturn(mutableListOf(skySightLayer))

        withLayerAndSourceConstructionMocks { layerConstruction ->
            whenever(style.getLayer(any())).thenReturn(null)

            val overlay = WeatherRainOverlay(map)
            overlay.render(
                frameSelection = frameSelection(9_000L),
                opacity = 0.65f,
                transitionDurationMs = 0L
            )

            verify(style, atLeastOnce()).addLayerAbove(any(), org.mockito.kotlin.eq("skysight-sat-layer-0"))
        }
    }

    private fun frameSelection(
        epochSec: Long,
        tileSizePx: Int = 256
    ): WeatherRainFrameSelection {
        val framePath = "/v2/radar/$epochSec"
        return WeatherRainFrameSelection(
            hostUrl = "https://tilecache.rainviewer.com",
            framePath = framePath,
            frameTimeEpochSec = epochSec,
            renderOptions = WeatherRadarRenderOptions(tileSizePx = tileSizePx)
        )
    }

    private inline fun withLayerAndSourceConstructionMocks(
        block: (MockedConstruction<RasterLayer>) -> Unit
    ) {
        mockConstruction(RasterSource::class.java).use {
            mockConstruction(
                RasterLayer::class.java,
                withSettings().defaultAnswer(Answers.RETURNS_SELF)
            ).use { layerConstruction ->
                block(layerConstruction)
            }
        }
    }
}
