package com.example.xcpro.weather.rain

import java.net.URI
import java.util.Locale

private const val URL_SCHEME_HTTPS = "https"
private const val RAINVIEWER_HOST_ROOT = "rainviewer.com"
private const val RAINVIEWER_HOST_SUFFIX = ".rainviewer.com"
private const val RADAR_PATH_PREFIX = "/v2/radar/"

object WeatherRainTileUrlBuilder {
    fun buildUrlTemplate(selection: WeatherRainFrameSelection): String {
        val normalizedHost = normalizeHostUrl(selection.hostUrl)
        val normalizedPath = normalizeFramePath(selection.framePath)
        val renderOptions = selection.renderOptions
        val tileSizePx = normalizeWeatherRainTileSize(renderOptions.normalizedTileSizePx)
        val colorScheme = renderOptions.colorScheme
        require(colorScheme >= 0) { "Color scheme must be >= 0" }
        val optionsToken = renderOptions.optionsToken

        val candidate =
            "$normalizedHost$normalizedPath/$tileSizePx/{z}/{x}/{y}/$colorScheme/$optionsToken.png"
        require(isSecureRainViewerTileUrl(candidate)) { "Invalid rain tile URL" }
        return candidate
    }

    internal fun isSecureRainViewerTileUrl(url: String): Boolean {
        val normalized = url
            .replace("{z}", "0")
            .replace("{x}", "0")
            .replace("{y}", "0")
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return false
        val host = uri.host ?: return false
        val path = uri.path ?: return false
        return uri.scheme.equals(URL_SCHEME_HTTPS, ignoreCase = true) &&
            isTrustedRainViewerHost(host) &&
            path.startsWith(RADAR_PATH_PREFIX) &&
            path.endsWith(".png")
    }

    internal fun normalizeHostUrl(rawHostUrl: String): String {
        val normalized = rawHostUrl.trim().trimEnd('/')
        require(normalized.isNotEmpty()) { "Host URL is required" }
        val uri = runCatching { URI(normalized) }.getOrNull() ?: throw IllegalArgumentException(
            "Invalid host URL"
        )
        require(uri.scheme.equals(URL_SCHEME_HTTPS, ignoreCase = true)) {
            "Host URL must use https"
        }
        val host = uri.host?.trim() ?: throw IllegalArgumentException("Missing host")
        require(isTrustedRainViewerHost(host)) {
            "Untrusted host: $host"
        }
        val normalizedPort = if (uri.port > 0) ":${uri.port}" else ""
        return "${URL_SCHEME_HTTPS}://${host.lowercase(Locale.US)}$normalizedPort"
    }

    internal fun normalizeFramePath(rawFramePath: String): String {
        val normalized = rawFramePath.trim().removeSuffix("/")
        require(normalized.isNotEmpty()) { "Frame path is required" }
        val prefixed = if (normalized.startsWith("/")) normalized else "/$normalized"
        require(prefixed.startsWith(RADAR_PATH_PREFIX)) {
            "Unexpected frame path"
        }
        return prefixed
    }

    internal fun isTrustedRainViewerHost(host: String): Boolean {
        val normalized = host.trim().lowercase(Locale.US)
        if (normalized.isEmpty()) return false
        return normalized == RAINVIEWER_HOST_ROOT || normalized.endsWith(RAINVIEWER_HOST_SUFFIX)
    }
}
