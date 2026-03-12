package com.example.xcpro.weather.rain

import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherRainTileUrlBuilderTest {

    @Test(expected = IllegalArgumentException::class)
    fun buildUrlTemplate_requiresTrustedHttpsHost() {
        val selection = WeatherRainFrameSelection(
            hostUrl = "https://example.com",
            framePath = "/v2/radar/123",
            frameTimeEpochSec = 1L,
            renderOptions = WeatherRadarRenderOptions()
        )
        WeatherRainTileUrlBuilder.buildUrlTemplate(selection)
    }

    @Test
    fun buildUrlTemplate_buildsRainViewerFrameUrl() {
        val selection = WeatherRainFrameSelection(
            hostUrl = "https://tilecache.rainviewer.com",
            framePath = "/v2/radar/1234567890",
            frameTimeEpochSec = 1_234_567_890L,
            renderOptions = WeatherRadarRenderOptions(
                smooth = true,
                snow = true,
                colorScheme = 2,
                tileSizePx = 512
            )
        )
        val url = WeatherRainTileUrlBuilder.buildUrlTemplate(selection)

        assertTrue(url.startsWith("https://tilecache.rainviewer.com/v2/radar/1234567890/"))
        assertTrue(url.contains("/{z}/{x}/{y}/2/1_1.png"))
        assertTrue(WeatherRainTileUrlBuilder.isSecureRainViewerTileUrl(url))
    }

    @Test
    fun buildUrlTemplate_normalizesHostAndPathSlashes() {
        val selection = WeatherRainFrameSelection(
            hostUrl = "https://tilecache.rainviewer.com/",
            framePath = "v2/radar/42/",
            frameTimeEpochSec = 42L,
            renderOptions = WeatherRadarRenderOptions(
                smooth = false,
                snow = true,
                colorScheme = 2,
                tileSizePx = 512
            )
        )
        val url = WeatherRainTileUrlBuilder.buildUrlTemplate(selection)

        assertFalse(url.contains("//v2/radar"))
        assertTrue(url.contains("/v2/radar/42/512/{z}/{x}/{y}/2/0_1.png"))
        assertTrue(WeatherRainTileUrlBuilder.isSecureRainViewerTileUrl(url))
    }

    @Test
    fun buildUrlTemplate_normalizesUnsupportedTileSizeToDefault() {
        val selection = WeatherRainFrameSelection(
            hostUrl = "https://tilecache.rainviewer.com",
            framePath = "/v2/radar/1234567890",
            frameTimeEpochSec = 1_234_567_890L,
            renderOptions = WeatherRadarRenderOptions(tileSizePx = 999)
        )
        val url = WeatherRainTileUrlBuilder.buildUrlTemplate(selection)

        assertTrue(url.contains("/${WEATHER_RAIN_TILE_SIZE_DEFAULT_PX}/{z}/{x}/{y}/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildUrlTemplate_rejectsUnexpectedFramePath() {
        val selection = WeatherRainFrameSelection(
            hostUrl = "https://tilecache.rainviewer.com",
            framePath = "/v1/other/1234567890",
            frameTimeEpochSec = 1_234_567_890L,
            renderOptions = WeatherRadarRenderOptions()
        )
        WeatherRainTileUrlBuilder.buildUrlTemplate(selection)
    }

    @Test
    fun isSecureRainViewerTileUrl_rejectsHttpAndWrongHost() {
        assertFalse(
            WeatherRainTileUrlBuilder.isSecureRainViewerTileUrl(
                "http://tilecache.rainviewer.com/v2/radar/1/512/{z}/{x}/{y}/2/1_1.png"
            )
        )
        assertFalse(
            WeatherRainTileUrlBuilder.isSecureRainViewerTileUrl(
                "https://example.com/v2/radar/1/512/{z}/{x}/{y}/2/1_1.png"
            )
        )
    }

    @Test
    fun normalizeHostUrl_isLocaleSafeUnderTurkishDefault() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val normalized = WeatherRainTileUrlBuilder.normalizeHostUrl(
                "https://TILECACHE.RAINVIEWER.COM"
            )
            assertEquals("https://tilecache.rainviewer.com", normalized)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
