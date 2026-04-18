package com.trust3.xcpro.map

import com.trust3.xcpro.weather.rain.WeatherRadarRenderOptions
import com.trust3.xcpro.weather.rain.WeatherRainFrameSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.mockito.Answers
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.withSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MapscreenPkgW1PerfEvidenceTest {

    @Test
    fun emitPkgW1PerfEvidence() {
        val mismatchCount = measureTileSizeMismatchRebuildMissCount()
        val payload = linkedMapOf<String, Any?>(
            "package_id" to "pkg-w1",
            "generated_at" to MapscreenPerfEvidenceSupport.nowIsoString(),
            "slo_metrics" to linkedMapOf(
                "MS-ENG-08" to linkedMapOf(
                    "tile_size_mismatch_rebuild_miss_count" to mismatchCount
                )
            )
        )
        MapscreenPerfEvidenceSupport.writePackageEvidence(
            packageId = "pkg-w1",
            payload = payload
        )

        assertEquals(0, mismatchCount)
    }

    private fun measureTileSizeMismatchRebuildMissCount(): Int {
        var mismatchCount = 0
        repeat(12) { runIndex ->
            val map: MapLibreMap = mock()
            val style: Style = mock()
            whenever(map.style).thenReturn(style)
            val createdTileSizes = mutableListOf<Int>()

            mockConstruction(RasterSource::class.java) { _, context ->
                createdTileSizes += context.arguments()[2] as Int
            }.use {
                mockConstruction(
                    RasterLayer::class.java,
                    withSettings().defaultAnswer(Answers.RETURNS_SELF)
                ).use { layers ->
                    whenever(style.getLayer(any())).thenAnswer {
                        layers.constructed().lastOrNull()
                    }

                    val overlay = WeatherRainOverlay(map)
                    val frameEpochSec = 9_000L + runIndex
                    overlay.render(
                        frameSelection = frameSelection(epochSec = frameEpochSec, tileSizePx = 256),
                        opacity = 0.60f,
                        transitionDurationMs = 0L
                    )
                    overlay.render(
                        frameSelection = frameSelection(epochSec = frameEpochSec, tileSizePx = 512),
                        opacity = 0.60f,
                        transitionDurationMs = 0L
                    )
                }
            }

            val expected = listOf(256, 512)
            if (createdTileSizes != expected) {
                mismatchCount += 1
            }
        }
        assertTrue(mismatchCount >= 0)
        return mismatchCount
    }

    private fun frameSelection(
        epochSec: Long,
        tileSizePx: Int
    ): WeatherRainFrameSelection {
        val framePath = "/v2/radar/$epochSec"
        return WeatherRainFrameSelection(
            hostUrl = "https://tilecache.rainviewer.com",
            framePath = framePath,
            frameTimeEpochSec = epochSec,
            renderOptions = WeatherRadarRenderOptions(tileSizePx = tileSizePx)
        )
    }
}
