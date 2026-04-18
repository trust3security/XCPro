package com.trust3.xcpro.weather.rain

const val WEATHER_RAIN_OPACITY_MIN = 0f
const val WEATHER_RAIN_OPACITY_MAX = 1f
const val WEATHER_RAIN_OPACITY_DEFAULT = 0.45f
// Poll metadata frequently enough to pick up new published radar frames quickly.
// RainViewer past frames are 10-minute cadence, so 60s keeps UI close to live without aggressive polling.
const val WEATHER_RAIN_METADATA_REFRESH_INTERVAL_OK_MS = 60 * 1000L
const val WEATHER_RAIN_METADATA_REFRESH_INTERVAL_TRANSIENT_ERROR_MS = 120 * 1000L
const val WEATHER_RAIN_METADATA_REFRESH_INTERVAL_RATE_LIMIT_MS = 300 * 1000L
const val WEATHER_RAIN_METADATA_STALE_AFTER_MS = 15 * 60 * 1000L
const val WEATHER_RAIN_ANIMATION_WINDOW_10_MIN_SECONDS = 10 * 60L
const val WEATHER_RAIN_ANIMATION_WINDOW_20_MIN_SECONDS = 20 * 60L
const val WEATHER_RAIN_ANIMATION_WINDOW_30_MIN_SECONDS = 30 * 60L
const val WEATHER_RAIN_ANIMATION_WINDOW_DEFAULT_SECONDS = WEATHER_RAIN_ANIMATION_WINDOW_10_MIN_SECONDS
const val WEATHER_RAIN_ANIMATION_WINDOW_STEP_MINUTES = 10
const val WEATHER_RAIN_ANIMATION_WINDOW_MAX_MINUTES = 120
const val WEATHER_RAIN_FRAME_CADENCE_SECONDS = 10 * 60L
const val WEATHER_RAIN_ANIMATION_FRAME_INTERVAL_SLOW_MS = 1600L
const val WEATHER_RAIN_ANIMATION_FRAME_INTERVAL_NORMAL_MS = 1200L
const val WEATHER_RAIN_ANIMATION_FRAME_INTERVAL_FAST_MS = 700L
const val WEATHER_RAIN_TRANSITION_DURATION_CRISP_MS = 120L
const val WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS = 280L
const val WEATHER_RAIN_TRANSITION_DURATION_SMOOTH_MS = 420L
const val WEATHER_RAIN_TRANSITION_DURATION_MAX_FRAME_FRACTION = 0.45f
const val WEATHER_RAIN_TRANSITION_WINDOW_SCALE_20_MIN = 0.85f
const val WEATHER_RAIN_TRANSITION_WINDOW_SCALE_30_MIN = 0.70f
const val WEATHER_RAIN_STALE_DIMMED_OPACITY_MAX = 0.20f
const val WEATHER_RAIN_MIN_ZOOM = 0f
const val WEATHER_RAIN_MAX_ZOOM = 7f
const val WEATHER_RAIN_TILE_SIZE_256_PX = 256
const val WEATHER_RAIN_TILE_SIZE_512_PX = 512
const val WEATHER_RAIN_TILE_SIZE_DEFAULT_PX = WEATHER_RAIN_TILE_SIZE_512_PX
const val WEATHER_RAIN_COLOR_SCHEME_UNIVERSAL_BLUE = 2

fun clampWeatherRainOpacity(value: Float): Float =
    value.coerceIn(WEATHER_RAIN_OPACITY_MIN, WEATHER_RAIN_OPACITY_MAX)

fun WeatherRainAnimationWindow.maxSelectableFrameCount(): Int =
    ((windowSeconds / WEATHER_RAIN_FRAME_CADENCE_SECONDS) + 1L)
        .toInt()
        .coerceAtLeast(1)

fun resolveWeatherRainMetadataRefreshIntervalMs(
    status: WeatherRadarStatusCode
): Long =
    when (status) {
        WeatherRadarStatusCode.RATE_LIMIT -> WEATHER_RAIN_METADATA_REFRESH_INTERVAL_RATE_LIMIT_MS
        WeatherRadarStatusCode.NETWORK_ERROR,
        WeatherRadarStatusCode.PARSE_ERROR -> WEATHER_RAIN_METADATA_REFRESH_INTERVAL_TRANSIENT_ERROR_MS
        else -> WEATHER_RAIN_METADATA_REFRESH_INTERVAL_OK_MS
    }

fun isWeatherRainMetadataStale(
    status: WeatherRadarStatusCode,
    freshnessAgeMs: Long?
): Boolean {
    val resolvedAgeMs = freshnessAgeMs ?: return true
    val staleByAge = resolvedAgeMs > WEATHER_RAIN_METADATA_STALE_AFTER_MS
    return when (status) {
        WeatherRadarStatusCode.OK -> staleByAge
        WeatherRadarStatusCode.NETWORK_ERROR,
        WeatherRadarStatusCode.RATE_LIMIT -> staleByAge
        WeatherRadarStatusCode.NO_METADATA,
        WeatherRadarStatusCode.NO_FRAMES,
        WeatherRadarStatusCode.PARSE_ERROR -> true
    }
}

fun normalizeWeatherRainTileSize(tileSizePx: Int): Int =
    when (tileSizePx) {
        WEATHER_RAIN_TILE_SIZE_256_PX -> WEATHER_RAIN_TILE_SIZE_256_PX
        WEATHER_RAIN_TILE_SIZE_512_PX -> WEATHER_RAIN_TILE_SIZE_512_PX
        else -> WEATHER_RAIN_TILE_SIZE_DEFAULT_PX
    }

fun resolveWeatherRainTransitionDurationMs(
    preferredDurationMs: Long,
    frameIntervalMs: Long
): Long {
    val normalizedPreferred = preferredDurationMs.coerceAtLeast(0L)
    val normalizedInterval = frameIntervalMs.coerceAtLeast(0L)
    val maxAllowedDuration = (normalizedInterval * WEATHER_RAIN_TRANSITION_DURATION_MAX_FRAME_FRACTION)
        .toLong()
    return normalizedPreferred.coerceAtMost(maxAllowedDuration)
}

fun resolveWeatherRainEffectiveTransitionDurationMs(
    preferredDurationMs: Long,
    frameIntervalMs: Long,
    animatePastWindow: Boolean,
    animationWindow: WeatherRainAnimationWindow
): Long {
    val baseDurationMs = resolveWeatherRainTransitionDurationMs(
        preferredDurationMs = preferredDurationMs,
        frameIntervalMs = frameIntervalMs
    )
    if (!animatePastWindow) return baseDurationMs
    val windowScale = when (animationWindow) {
        WeatherRainAnimationWindow.TEN_MINUTES -> 1.0f
        WeatherRainAnimationWindow.TWENTY_MINUTES -> WEATHER_RAIN_TRANSITION_WINDOW_SCALE_20_MIN
        else -> WEATHER_RAIN_TRANSITION_WINDOW_SCALE_30_MIN
    }
    return (baseDurationMs * windowScale).toLong().coerceAtMost(baseDurationMs).coerceAtLeast(0L)
}

fun resolveWeatherRainPlaybackFrameIndex(
    animationTick: Long,
    frameCount: Int
): Int {
    if (frameCount <= 1) return 0
    val normalizedTick = (animationTick % frameCount.toLong()).toInt()
    return if (normalizedTick >= 0) normalizedTick else normalizedTick + frameCount
}
