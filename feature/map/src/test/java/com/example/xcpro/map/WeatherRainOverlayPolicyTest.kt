package com.example.xcpro.map

import com.example.xcpro.weather.rain.WeatherRadarRenderOptions
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
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

    private fun frameSelection(epochSec: Long): WeatherRainFrameSelection {
        val framePath = "/v2/radar/$epochSec"
        return WeatherRainFrameSelection(
            hostUrl = "https://tilecache.rainviewer.com",
            framePath = framePath,
            frameTimeEpochSec = epochSec,
            renderOptions = WeatherRadarRenderOptions()
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
