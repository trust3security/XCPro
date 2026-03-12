package com.example.xcpro.weather.rain

import java.util.Locale

data class WeatherOverlayPreferences(
    val enabled: Boolean = false,
    val opacity: Float = WEATHER_RAIN_OPACITY_DEFAULT,
    val animatePastWindow: Boolean = false,
    val animationWindow: WeatherRainAnimationWindow = WeatherRainAnimationWindow.TEN_MINUTES,
    val animationSpeed: WeatherRainAnimationSpeed = WeatherRainAnimationSpeed.NORMAL,
    val transitionQuality: WeatherRainTransitionQuality = WeatherRainTransitionQuality.BALANCED,
    val frameMode: WeatherRadarFrameMode = WeatherRadarFrameMode.LATEST,
    val manualFrameIndex: Int = 0,
    val renderOptions: WeatherRadarRenderOptions = WeatherRadarRenderOptions()
)

data class WeatherOverlayRuntimeState(
    val enabled: Boolean = false,
    val opacity: Float = WEATHER_RAIN_OPACITY_DEFAULT,
    val animatePastWindow: Boolean = false,
    val animationWindow: WeatherRainAnimationWindow = WeatherRainAnimationWindow.TEN_MINUTES,
    val animationSpeed: WeatherRainAnimationSpeed = WeatherRainAnimationSpeed.NORMAL,
    val transitionQuality: WeatherRainTransitionQuality = WeatherRainTransitionQuality.BALANCED,
    val transitionDurationMs: Long = WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS,
    val frameMode: WeatherRadarFrameMode = WeatherRadarFrameMode.LATEST,
    val manualFrameIndex: Int = 0,
    val selectedFrame: WeatherRainFrameSelection? = null,
    val availableFrameCount: Int = 0,
    val metadataGeneratedEpochSec: Long? = null,
    val metadataStatus: WeatherRadarStatusCode = WeatherRadarStatusCode.NO_METADATA,
    val metadataDetail: String? = null,
    val lastSuccessfulMetadataFetchWallMs: Long? = null,
    val lastMetadataContentChangeWallMs: Long? = null,
    val metadataFreshnessAgeMs: Long? = null,
    val metadataContentAgeMs: Long? = null,
    val selectedFrameAgeMs: Long? = null,
    val metadataStale: Boolean = true
)

enum class WeatherRainAnimationSpeed(
    val storageKey: String,
    val frameIntervalMs: Long
) {
    SLOW(
        storageKey = "slow",
        frameIntervalMs = WEATHER_RAIN_ANIMATION_FRAME_INTERVAL_SLOW_MS
    ),
    NORMAL(
        storageKey = "normal",
        frameIntervalMs = WEATHER_RAIN_ANIMATION_FRAME_INTERVAL_NORMAL_MS
    ),
    FAST(
        storageKey = "fast",
        frameIntervalMs = WEATHER_RAIN_ANIMATION_FRAME_INTERVAL_FAST_MS
    );

    companion object {
        fun fromStorage(value: String?): WeatherRainAnimationSpeed =
            entries.firstOrNull { speed ->
                speed.storageKey == value?.trim()?.lowercase(Locale.US)
            } ?: NORMAL
    }
}

enum class WeatherRainAnimationWindow(
    val storageKey: String,
    val windowMinutes: Int
) {
    TEN_MINUTES(
        storageKey = "10m",
        windowMinutes = 10
    ),
    TWENTY_MINUTES(
        storageKey = "20m",
        windowMinutes = 20
    ),
    THIRTY_MINUTES(
        storageKey = "30m",
        windowMinutes = 30
    ),
    FORTY_MINUTES(
        storageKey = "40m",
        windowMinutes = 40
    ),
    FIFTY_MINUTES(
        storageKey = "50m",
        windowMinutes = 50
    ),
    SIXTY_MINUTES(
        storageKey = "60m",
        windowMinutes = 60
    ),
    SEVENTY_MINUTES(
        storageKey = "70m",
        windowMinutes = 70
    ),
    EIGHTY_MINUTES(
        storageKey = "80m",
        windowMinutes = 80
    ),
    NINETY_MINUTES(
        storageKey = "90m",
        windowMinutes = 90
    ),
    ONE_HUNDRED_MINUTES(
        storageKey = "100m",
        windowMinutes = 100
    ),
    ONE_HUNDRED_TEN_MINUTES(
        storageKey = "110m",
        windowMinutes = 110
    ),
    ONE_HUNDRED_TWENTY_MINUTES(
        storageKey = "120m",
        windowMinutes = 120
    );

    val windowSeconds: Long
        get() = windowMinutes * 60L

    companion object {
        fun fromStorage(value: String?): WeatherRainAnimationWindow =
            entries.firstOrNull { window ->
                window.storageKey == value?.trim()?.lowercase(Locale.US)
            } ?: TEN_MINUTES
    }
}

enum class WeatherRainTransitionQuality(
    val storageKey: String,
    val preferredDurationMs: Long
) {
    CRISP(
        storageKey = "crisp",
        preferredDurationMs = WEATHER_RAIN_TRANSITION_DURATION_CRISP_MS
    ),
    BALANCED(
        storageKey = "balanced",
        preferredDurationMs = WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS
    ),
    SMOOTH(
        storageKey = "smooth",
        preferredDurationMs = WEATHER_RAIN_TRANSITION_DURATION_SMOOTH_MS
    );

    companion object {
        fun fromStorage(value: String?): WeatherRainTransitionQuality =
            entries.firstOrNull { quality ->
                quality.storageKey == value?.trim()?.lowercase(Locale.US)
            } ?: BALANCED
    }
}

enum class WeatherRadarFrameMode {
    LATEST,
    MANUAL;

    val storageKey: String
        get() = name.lowercase(Locale.US)

    companion object {
        fun fromStorage(value: String?): WeatherRadarFrameMode =
            entries.firstOrNull { mode ->
                mode.storageKey == value?.trim()?.lowercase(Locale.US)
            } ?: LATEST
    }
}

data class WeatherRadarRenderOptions(
    val smooth: Boolean = true,
    val snow: Boolean = true,
    val colorScheme: Int = WEATHER_RAIN_COLOR_SCHEME_UNIVERSAL_BLUE,
    val tileSizePx: Int = WEATHER_RAIN_TILE_SIZE_DEFAULT_PX
) {
    val optionsToken: String = "${flagToToken(smooth)}_${flagToToken(snow)}"

    val normalizedTileSizePx: Int = normalizeWeatherRainTileSize(tileSizePx)

    private fun flagToToken(value: Boolean): Int = if (value) 1 else 0
}

data class WeatherRainFrameSelection(
    val hostUrl: String,
    val framePath: String,
    val frameTimeEpochSec: Long,
    val renderOptions: WeatherRadarRenderOptions
)

enum class WeatherRadarStatusCode {
    NO_METADATA,
    NO_FRAMES,
    RATE_LIMIT,
    NETWORK_ERROR,
    PARSE_ERROR,
    OK
}
