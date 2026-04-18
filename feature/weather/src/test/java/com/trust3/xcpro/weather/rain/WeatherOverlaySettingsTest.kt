package com.trust3.xcpro.weather.rain

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherOverlaySettingsTest {

    @Test
    fun clampWeatherRainOpacity_clampsToSupportedRange() {
        assertEquals(WEATHER_RAIN_OPACITY_MIN, clampWeatherRainOpacity(-1f))
        assertEquals(0.5f, clampWeatherRainOpacity(0.5f))
        assertEquals(WEATHER_RAIN_OPACITY_MAX, clampWeatherRainOpacity(2f))
    }

    @Test
    fun resolveWeatherRainMetadataRefreshIntervalMs_appliesStatusBackoff() {
        assertEquals(
            WEATHER_RAIN_METADATA_REFRESH_INTERVAL_OK_MS,
            resolveWeatherRainMetadataRefreshIntervalMs(WeatherRadarStatusCode.OK)
        )
        assertEquals(
            WEATHER_RAIN_METADATA_REFRESH_INTERVAL_TRANSIENT_ERROR_MS,
            resolveWeatherRainMetadataRefreshIntervalMs(WeatherRadarStatusCode.NETWORK_ERROR)
        )
        assertEquals(
            WEATHER_RAIN_METADATA_REFRESH_INTERVAL_TRANSIENT_ERROR_MS,
            resolveWeatherRainMetadataRefreshIntervalMs(WeatherRadarStatusCode.PARSE_ERROR)
        )
        assertEquals(
            WEATHER_RAIN_METADATA_REFRESH_INTERVAL_RATE_LIMIT_MS,
            resolveWeatherRainMetadataRefreshIntervalMs(WeatherRadarStatusCode.RATE_LIMIT)
        )
    }

    @Test
    fun isWeatherRainMetadataStale_respectsStatusAndAge() {
        assertEquals(
            false,
            isWeatherRainMetadataStale(
                status = WeatherRadarStatusCode.OK,
                freshnessAgeMs = WEATHER_RAIN_METADATA_STALE_AFTER_MS
            )
        )
        assertEquals(
            true,
            isWeatherRainMetadataStale(
                status = WeatherRadarStatusCode.OK,
                freshnessAgeMs = WEATHER_RAIN_METADATA_STALE_AFTER_MS + 1L
            )
        )
        assertEquals(
            true,
            isWeatherRainMetadataStale(
                status = WeatherRadarStatusCode.OK,
                freshnessAgeMs = null
            )
        )
        assertEquals(
            false,
            isWeatherRainMetadataStale(
                status = WeatherRadarStatusCode.NETWORK_ERROR,
                freshnessAgeMs = 1_000L
            )
        )
        assertEquals(
            true,
            isWeatherRainMetadataStale(
                status = WeatherRadarStatusCode.NETWORK_ERROR,
                freshnessAgeMs = WEATHER_RAIN_METADATA_STALE_AFTER_MS + 1L
            )
        )
        assertEquals(
            true,
            isWeatherRainMetadataStale(
                status = WeatherRadarStatusCode.PARSE_ERROR,
                freshnessAgeMs = 1_000L
            )
        )
    }

    @Test
    fun normalizeWeatherRainTileSize_acceptsOnly256Or512() {
        assertEquals(WEATHER_RAIN_TILE_SIZE_256_PX, normalizeWeatherRainTileSize(256))
        assertEquals(WEATHER_RAIN_TILE_SIZE_512_PX, normalizeWeatherRainTileSize(512))
        assertEquals(WEATHER_RAIN_TILE_SIZE_DEFAULT_PX, normalizeWeatherRainTileSize(0))
        assertEquals(WEATHER_RAIN_TILE_SIZE_DEFAULT_PX, normalizeWeatherRainTileSize(1024))
    }

    @Test
    fun animationSpeed_fromStorageFallsBackToNormal() {
        assertEquals(
            WeatherRainAnimationSpeed.SLOW,
            WeatherRainAnimationSpeed.fromStorage("slow")
        )
        assertEquals(
            WeatherRainAnimationSpeed.NORMAL,
            WeatherRainAnimationSpeed.fromStorage("unknown")
        )
        assertEquals(
            WeatherRainAnimationSpeed.NORMAL,
            WeatherRainAnimationSpeed.fromStorage(null)
        )
    }

    @Test
    fun animationWindow_fromStorageFallsBackToTenMinutes() {
        assertEquals(
            WeatherRainAnimationWindow.TEN_MINUTES,
            WeatherRainAnimationWindow.fromStorage("10m")
        )
        assertEquals(
            WeatherRainAnimationWindow.TWENTY_MINUTES,
            WeatherRainAnimationWindow.fromStorage("20m")
        )
        assertEquals(
            WeatherRainAnimationWindow.THIRTY_MINUTES,
            WeatherRainAnimationWindow.fromStorage("30m")
        )
        assertEquals(
            WeatherRainAnimationWindow.ONE_HUNDRED_TWENTY_MINUTES,
            WeatherRainAnimationWindow.fromStorage("120m")
        )
        assertEquals(
            WeatherRainAnimationWindow.TEN_MINUTES,
            WeatherRainAnimationWindow.fromStorage("unknown")
        )
        assertEquals(
            WeatherRainAnimationWindow.TEN_MINUTES,
            WeatherRainAnimationWindow.fromStorage(null)
        )
    }

    @Test
    fun maxSelectableFrameCount_matchesWindowContract() {
        assertEquals(
            2,
            WeatherRainAnimationWindow.TEN_MINUTES.maxSelectableFrameCount()
        )
        assertEquals(
            3,
            WeatherRainAnimationWindow.TWENTY_MINUTES.maxSelectableFrameCount()
        )
        assertEquals(
            4,
            WeatherRainAnimationWindow.THIRTY_MINUTES.maxSelectableFrameCount()
        )
        assertEquals(
            13,
            WeatherRainAnimationWindow.ONE_HUNDRED_TWENTY_MINUTES.maxSelectableFrameCount()
        )
    }

    @Test
    fun transitionQuality_fromStorageFallsBackToBalanced() {
        assertEquals(
            WeatherRainTransitionQuality.CRISP,
            WeatherRainTransitionQuality.fromStorage("crisp")
        )
        assertEquals(
            WeatherRainTransitionQuality.BALANCED,
            WeatherRainTransitionQuality.fromStorage("unknown")
        )
        assertEquals(
            WeatherRainTransitionQuality.BALANCED,
            WeatherRainTransitionQuality.fromStorage(null)
        )
    }

    @Test
    fun resolveWeatherRainTransitionDurationMs_capsByFrameIntervalFraction() {
        assertEquals(
            420L,
            resolveWeatherRainTransitionDurationMs(
                preferredDurationMs = WEATHER_RAIN_TRANSITION_DURATION_SMOOTH_MS,
                frameIntervalMs = WEATHER_RAIN_ANIMATION_FRAME_INTERVAL_NORMAL_MS
            )
        )
        assertEquals(
            315L,
            resolveWeatherRainTransitionDurationMs(
                preferredDurationMs = 999L,
                frameIntervalMs = WEATHER_RAIN_ANIMATION_FRAME_INTERVAL_FAST_MS
            )
        )
        assertEquals(
            0L,
            resolveWeatherRainTransitionDurationMs(
                preferredDurationMs = WEATHER_RAIN_TRANSITION_DURATION_CRISP_MS,
                frameIntervalMs = 0L
            )
        )
    }

    @Test
    fun resolveWeatherRainEffectiveTransitionDurationMs_scalesByAnimationWindow() {
        assertEquals(
            280L,
            resolveWeatherRainEffectiveTransitionDurationMs(
                preferredDurationMs = WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS,
                frameIntervalMs = WEATHER_RAIN_ANIMATION_FRAME_INTERVAL_NORMAL_MS,
                animatePastWindow = true,
                animationWindow = WeatherRainAnimationWindow.TEN_MINUTES
            )
        )
        assertEquals(
            238L,
            resolveWeatherRainEffectiveTransitionDurationMs(
                preferredDurationMs = WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS,
                frameIntervalMs = WEATHER_RAIN_ANIMATION_FRAME_INTERVAL_NORMAL_MS,
                animatePastWindow = true,
                animationWindow = WeatherRainAnimationWindow.TWENTY_MINUTES
            )
        )
        assertEquals(
            196L,
            resolveWeatherRainEffectiveTransitionDurationMs(
                preferredDurationMs = WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS,
                frameIntervalMs = WEATHER_RAIN_ANIMATION_FRAME_INTERVAL_NORMAL_MS,
                animatePastWindow = true,
                animationWindow = WeatherRainAnimationWindow.THIRTY_MINUTES
            )
        )
        assertEquals(
            196L,
            resolveWeatherRainEffectiveTransitionDurationMs(
                preferredDurationMs = WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS,
                frameIntervalMs = WEATHER_RAIN_ANIMATION_FRAME_INTERVAL_NORMAL_MS,
                animatePastWindow = true,
                animationWindow = WeatherRainAnimationWindow.ONE_HUNDRED_TWENTY_MINUTES
            )
        )
    }

    @Test
    fun resolveWeatherRainEffectiveTransitionDurationMs_keepsBaseWhenAnimationDisabled() {
        assertEquals(
            280L,
            resolveWeatherRainEffectiveTransitionDurationMs(
                preferredDurationMs = WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS,
                frameIntervalMs = WEATHER_RAIN_ANIMATION_FRAME_INTERVAL_NORMAL_MS,
                animatePastWindow = false,
                animationWindow = WeatherRainAnimationWindow.THIRTY_MINUTES
            )
        )
    }

    @Test
    fun resolveWeatherRainPlaybackFrameIndex_usesForwardLoopSequence() {
        val sequence = (0L..7L).map { tick ->
            resolveWeatherRainPlaybackFrameIndex(
                animationTick = tick,
                frameCount = 4
            )
        }
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3), sequence)
    }

    @Test
    fun resolveWeatherRainPlaybackFrameIndex_singleFrameAlwaysZero() {
        assertEquals(0, resolveWeatherRainPlaybackFrameIndex(0L, 1))
        assertEquals(0, resolveWeatherRainPlaybackFrameIndex(5L, 1))
    }
}
