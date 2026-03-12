package com.example.xcpro.weather.rain

import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.time.Clock
import com.example.xcpro.di.WeatherMetadataHttpClient
import java.io.IOException
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Singleton
class WeatherRadarMetadataRepository @Inject constructor(
    private val clock: Clock,
    @WeatherMetadataHttpClient private val httpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val refreshMutex = Mutex()

    @Volatile
    private var lastState: WeatherRadarMetadataState = WeatherRadarMetadataState()

    @Volatile
    private var lastGeneratedEpochSec: Long? = null

    @Volatile
    private var lastRefreshAttemptMonoMs: Long? = null

    @Volatile
    private var lastObservedEtag: String? = null

    @Volatile
    private var lastObservedLastModified: String? = null

    fun currentState(): WeatherRadarMetadataState = lastState

    suspend fun refreshMetadata(): WeatherRadarMetadataState = withContext(ioDispatcher) {
        refreshMutex.withLock {
            val nowMonoMs = clock.nowMonoMs()
            val previousAttemptMonoMs = lastRefreshAttemptMonoMs
            if (previousAttemptMonoMs != null) {
                val elapsedSinceAttemptMs = (nowMonoMs - previousAttemptMonoMs).coerceAtLeast(0L)
                if (elapsedSinceAttemptMs < MIN_REFRESH_GAP_MS) {
                    return@withLock lastState
                }
            }
            lastRefreshAttemptMonoMs = nowMonoMs

            val previousState = lastState
            val previousMetadata = previousState.metadata

            val requestBuilder = Request.Builder()
                .url(RAINVIEWER_METADATA_URL)
                .get()
            lastObservedEtag?.takeIf { value -> value.isNotBlank() }?.let { value ->
                requestBuilder.header(HEADER_IF_NONE_MATCH, value)
            }
            lastObservedLastModified?.takeIf { value -> value.isNotBlank() }?.let { value ->
                requestBuilder.header(HEADER_IF_MODIFIED_SINCE, value)
            }
            val request = requestBuilder.build()

            val updatedState = runCatching {
                httpClient.newCall(request).execute().use { response ->
                    updateConditionalValidatorsFromResponse(response)
                    if (response.code == HTTP_NOT_MODIFIED) {
                        val previousMetadataState = previousState.metadata
                        if (previousMetadataState == null) {
                            return@use WeatherRadarMetadataState(
                                status = WeatherRadarStatusCode.NO_METADATA,
                                metadata = null,
                                lastSuccessfulFetchWallMs = previousState.lastSuccessfulFetchWallMs,
                                lastContentChangeWallMs = previousState.lastContentChangeWallMs,
                                detail = "HTTP 304 without cached metadata"
                            )
                        }
                        return@use WeatherRadarMetadataState(
                            status = WeatherRadarStatusCode.OK,
                            metadata = previousMetadataState,
                            lastSuccessfulFetchWallMs = clock.nowWallMs(),
                            lastContentChangeWallMs = previousState.lastContentChangeWallMs,
                            detail = "HTTP 304 not modified"
                        )
                    }

                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val failureStatus = if (response.code == HTTP_TOO_MANY_REQUESTS) {
                            WeatherRadarStatusCode.RATE_LIMIT
                        } else {
                            WeatherRadarStatusCode.NETWORK_ERROR
                        }
                        return@use WeatherRadarMetadataState(
                            status = failureStatus,
                            metadata = previousMetadata,
                            lastSuccessfulFetchWallMs = previousState.lastSuccessfulFetchWallMs,
                            lastContentChangeWallMs = previousState.lastContentChangeWallMs,
                            detail = "HTTP ${response.code}"
                        )
                    }

                    val parseResult = parseMetadataPayloadWithWarnings(responseBody)
                    val parsed = parseResult.metadata
                    val successFetchWallMs = clock.nowWallMs()
                    if (parsed.pastFrames.isEmpty()) {
                        return@use WeatherRadarMetadataState(
                            status = WeatherRadarStatusCode.NO_FRAMES,
                            metadata = previousMetadata ?: parsed,
                            lastSuccessfulFetchWallMs = successFetchWallMs,
                            lastContentChangeWallMs = previousState.lastContentChangeWallMs,
                            detail = "radar.past is empty"
                        )
                    }

                    val isNewGeneration = parsed.generatedEpochSec != lastGeneratedEpochSec
                    val isContentChanged = parsed != previousMetadata
                    if (isNewGeneration || lastGeneratedEpochSec == null) {
                        lastGeneratedEpochSec = parsed.generatedEpochSec
                    }
                    val nextContentChangeWallMs = if (isContentChanged) {
                        successFetchWallMs
                    } else {
                        previousState.lastContentChangeWallMs
                    }

                    WeatherRadarMetadataState(
                        status = WeatherRadarStatusCode.OK,
                        metadata = parsed,
                        lastSuccessfulFetchWallMs = successFetchWallMs,
                        lastContentChangeWallMs = nextContentChangeWallMs,
                        detail = parseResult.warningDetail
                    )
                }
            }.getOrElse { throwable ->
                val status = when (throwable) {
                    is IOException -> WeatherRadarStatusCode.NETWORK_ERROR
                    is IllegalArgumentException -> WeatherRadarStatusCode.PARSE_ERROR
                    else -> WeatherRadarStatusCode.PARSE_ERROR
                }
                WeatherRadarMetadataState(
                    status = status,
                    metadata = previousMetadata,
                    lastSuccessfulFetchWallMs = previousState.lastSuccessfulFetchWallMs,
                    lastContentChangeWallMs = previousState.lastContentChangeWallMs,
                    detail = throwable.message
                )
            }

            lastState = updatedState
            updatedState
        }
    }

    internal fun parseMetadataPayload(payload: String): WeatherRadarMetadata {
        return parseMetadataPayloadWithWarnings(payload).metadata
    }

    internal fun parseMetadataPayloadWithWarnings(payload: String): ParsedMetadataPayload {
        val root = runCatching { JSONObject(payload) }.getOrElse { cause ->
            throw IllegalArgumentException("Invalid weather metadata payload", cause)
        }

        val version = root.optString("version").trim()
        val versionWarningDetail = if (
            version.isNotEmpty() && !version.startsWith(SUPPORTED_METADATA_MAJOR_PREFIX)
        ) {
            "Unrecognized metadata version: $version"
        } else {
            null
        }

        val hostUrl = normalizeHostUrl(root.optString("host"))
        val generatedEpochSec = root.optLong("generated", INVALID_EPOCH_SEC)
        require(generatedEpochSec > 0L) { "Missing generated epoch seconds" }

        val radar = root.optJSONObject("radar")
        val past = radar?.optJSONArray("past")
        val frames = buildList {
            if (past != null) {
                for (index in 0 until past.length()) {
                    val frameObject = past.optJSONObject(index) ?: continue
                    val frameTime = frameObject.optLong("time", INVALID_EPOCH_SEC)
                    val rawPath = frameObject.optString("path").trim()
                    if (frameTime <= 0L || rawPath.isEmpty()) continue
                    val normalizedPath = runCatching { normalizeFramePath(rawPath) }.getOrNull()
                        ?: continue
                    add(
                        WeatherRadarFrame(
                            timeEpochSec = frameTime,
                            path = normalizedPath
                        )
                    )
                }
            }
        }
            .sortedBy { frame -> frame.timeEpochSec }
            .distinctBy { frame -> frame.timeEpochSec to frame.path }

        return ParsedMetadataPayload(
            metadata = WeatherRadarMetadata(
                hostUrl = hostUrl,
                generatedEpochSec = generatedEpochSec,
                pastFrames = frames
            ),
            warningDetail = versionWarningDetail
        )
    }

    internal fun normalizeHostUrl(rawHost: String): String {
        val normalized = rawHost.trim().trimEnd('/')
        require(normalized.isNotEmpty()) { "Missing host URL" }
        val uri = runCatching { URI(normalized) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid host URL")
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

    internal fun normalizeFramePath(rawPath: String): String {
        val normalized = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
        require(normalized.startsWith(RADAR_PATH_PREFIX)) {
            "Unexpected radar path"
        }
        return normalized.trimEnd('/')
    }

    internal fun isTrustedRainViewerHost(host: String): Boolean {
        val normalized = host.trim().lowercase(Locale.US)
        if (normalized.isEmpty()) return false
        return normalized == RAINVIEWER_HOST_ROOT || normalized.endsWith(RAINVIEWER_HOST_SUFFIX)
    }

    private fun updateConditionalValidatorsFromResponse(response: okhttp3.Response) {
        response.header(HEADER_ETAG)?.trim()?.takeIf { value -> value.isNotEmpty() }?.let { value ->
            lastObservedEtag = value
        }
        response.header(HEADER_LAST_MODIFIED)?.trim()?.takeIf { value -> value.isNotEmpty() }
            ?.let { value ->
                lastObservedLastModified = value
            }
    }

    private companion object {
        private const val RAINVIEWER_METADATA_URL = "https://api.rainviewer.com/public/weather-maps.json"
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_NOT_MODIFIED = 304
        private const val HEADER_ETAG = "ETag"
        private const val HEADER_LAST_MODIFIED = "Last-Modified"
        private const val HEADER_IF_NONE_MATCH = "If-None-Match"
        private const val HEADER_IF_MODIFIED_SINCE = "If-Modified-Since"
        private const val MIN_REFRESH_GAP_MS = 1_000L
        private const val INVALID_EPOCH_SEC = -1L
        private const val URL_SCHEME_HTTPS = "https"
        private const val RAINVIEWER_HOST_ROOT = "rainviewer.com"
        private const val RAINVIEWER_HOST_SUFFIX = ".rainviewer.com"
        private const val RADAR_PATH_PREFIX = "/v2/radar/"
        private const val SUPPORTED_METADATA_MAJOR_PREFIX = "2."
    }

    internal data class ParsedMetadataPayload(
        val metadata: WeatherRadarMetadata,
        val warningDetail: String?
    )
}
