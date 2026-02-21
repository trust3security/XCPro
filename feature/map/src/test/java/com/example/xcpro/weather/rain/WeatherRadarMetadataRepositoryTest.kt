package com.example.xcpro.weather.rain

import com.example.xcpro.core.time.FakeClock
import java.util.concurrent.atomic.AtomicInteger
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeatherRadarMetadataRepositoryTest {

    private val parserRepository = WeatherRadarMetadataRepository(
        clock = FakeClock(),
        httpClient = OkHttpClient(),
        ioDispatcher = Dispatchers.Unconfined
    )

    @Test
    fun parseMetadataPayload_parsesAndSortsPastFrames() {
        val payload = """
            {
              "generated": 1771554638,
              "host": "https://tilecache.rainviewer.com",
              "radar": {
                "past": [
                  {"time": 1771554600, "path": "/v2/radar/1771554600"},
                  {"time": 1771554000, "path": "/v2/radar/1771554000"}
                ]
              }
            }
        """.trimIndent()

        val parsed = parserRepository.parseMetadataPayload(payload)

        assertEquals("https://tilecache.rainviewer.com", parsed.hostUrl)
        assertEquals(1771554638L, parsed.generatedEpochSec)
        assertEquals(2, parsed.pastFrames.size)
        assertEquals(1771554000L, parsed.pastFrames.first().timeEpochSec)
        assertEquals("/v2/radar/1771554600", parsed.pastFrames.last().path)
    }

    @Test
    fun parseMetadataPayload_toleratesMissingOptionalBlocks() {
        val payload = """
            {
              "generated": 1771554638,
              "host": "https://tilecache.rainviewer.com",
              "radar": {}
            }
        """.trimIndent()

        val parsed = parserRepository.parseMetadataPayload(payload)

        assertTrue(parsed.pastFrames.isEmpty())
    }

    @Test
    fun parseMetadataPayloadWithWarnings_allowsUnknownVersionWhenPayloadIsValid() {
        val payload = """
            {
              "version": "3.0",
              "generated": 1771554638,
              "host": "https://tilecache.rainviewer.com",
              "radar": {
                "past": [
                  {"time": 1771554600, "path": "/v2/radar/1771554600"}
                ]
              }
            }
        """.trimIndent()

        val parsed = parserRepository.parseMetadataPayloadWithWarnings(payload)

        assertEquals(1771554638L, parsed.metadata.generatedEpochSec)
        assertEquals(1, parsed.metadata.pastFrames.size)
        assertTrue(parsed.warningDetail?.contains("Unrecognized metadata version") == true)
    }

    @Test
    fun parseMetadataPayload_deduplicatesDuplicateFramesByTimeAndPath() {
        val payload = """
            {
              "generated": 1771554638,
              "host": "https://tilecache.rainviewer.com",
              "radar": {
                "past": [
                  {"time": 1771554000, "path": "/v2/radar/a"},
                  {"time": 1771554000, "path": "/v2/radar/a"},
                  {"time": 1771554000, "path": "/v2/radar/b"}
                ]
              }
            }
        """.trimIndent()

        val parsed = parserRepository.parseMetadataPayload(payload)

        assertEquals(2, parsed.pastFrames.size)
        assertEquals("/v2/radar/a", parsed.pastFrames[0].path)
        assertEquals("/v2/radar/b", parsed.pastFrames[1].path)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseMetadataPayload_rejectsUntrustedHost() {
        val payload = """
            {
              "generated": 1771554638,
              "host": "https://example.com",
              "radar": {"past": []}
            }
        """.trimIndent()

        parserRepository.parseMetadataPayload(payload)
    }

    @Test
    fun normalizeHostUrl_lowercasesTrustedHost() {
        val normalized = parserRepository.normalizeHostUrl("https://TILECACHE.RAINVIEWER.COM")

        assertEquals("https://tilecache.rainviewer.com", normalized)
    }

    @Test
    fun normalizeHostUrl_isLocaleSafeUnderTurkishDefault() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val normalized = parserRepository.normalizeHostUrl("https://TILECACHE.RAINVIEWER.COM")
            assertEquals("https://tilecache.rainviewer.com", normalized)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun isTrustedRainViewerHost_acceptsRainViewerDomainsOnly() {
        assertTrue(parserRepository.isTrustedRainViewerHost("tilecache.rainviewer.com"))
        assertTrue(parserRepository.isTrustedRainViewerHost("rainviewer.com"))
        assertFalse(parserRepository.isTrustedRainViewerHost("example.com"))
    }

    @Test
    fun refreshMetadata_coalescesRapidParallelRequests() = runBlocking {
        val requestCount = AtomicInteger(0)
        val testClock = FakeClock(monoMs = 1_000L, wallMs = 1_000L)
        val countingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestCount.incrementAndGet()
                metadataResponse(
                    request = chain.request(),
                    generatedEpochSec = 1_771_554_638L
                )
            }
            .build()
        val refreshRepository = WeatherRadarMetadataRepository(
            clock = testClock,
            httpClient = countingClient,
            ioDispatcher = Dispatchers.Default
        )

        val first = async { refreshRepository.refreshMetadata() }
        val second = async { refreshRepository.refreshMetadata() }
        awaitAll(first, second)

        assertEquals(1, requestCount.get())
    }

    @Test
    fun refreshMetadata_allowsSubsequentRequestAfterMinGap() = runBlocking {
        val requestCount = AtomicInteger(0)
        val testClock = FakeClock(monoMs = 1_000L, wallMs = 1_000L)
        val countingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestCount.incrementAndGet()
                metadataResponse(
                    request = chain.request(),
                    generatedEpochSec = 1_771_554_638L
                )
            }
            .build()
        val refreshRepository = WeatherRadarMetadataRepository(
            clock = testClock,
            httpClient = countingClient,
            ioDispatcher = Dispatchers.Default
        )

        refreshRepository.refreshMetadata()
        testClock.advanceMonoMs(2_000L)
        testClock.advanceWallMs(2_000L)
        refreshRepository.refreshMetadata()

        assertEquals(2, requestCount.get())
    }

    @Test
    fun refreshMetadata_updatesLastSuccessTimestampOnEachSuccessfulFetch() = runBlocking {
        val testClock = FakeClock(monoMs = 1_000L, wallMs = 1_000L)
        val stableClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                metadataResponse(
                    request = chain.request(),
                    generatedEpochSec = 1_771_554_638L
                )
            }
            .build()
        val refreshRepository = WeatherRadarMetadataRepository(
            clock = testClock,
            httpClient = stableClient,
            ioDispatcher = Dispatchers.Default
        )

        val firstState = refreshRepository.refreshMetadata()
        testClock.advanceMonoMs(2_000L)
        testClock.advanceWallMs(2_000L)
        val secondState = refreshRepository.refreshMetadata()

        assertEquals(WeatherRadarStatusCode.OK, firstState.status)
        assertEquals(WeatherRadarStatusCode.OK, secondState.status)
        assertEquals(1_000L, firstState.lastSuccessfulFetchWallMs)
        assertEquals(3_000L, secondState.lastSuccessfulFetchWallMs)
    }

    @Test
    fun refreshMetadata_preservesLastContentChangeWhenPayloadIsUnchanged() = runBlocking {
        val testClock = FakeClock(monoMs = 1_000L, wallMs = 1_000L)
        val stableClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                metadataResponse(
                    request = chain.request(),
                    generatedEpochSec = 1_771_554_638L
                )
            }
            .build()
        val refreshRepository = WeatherRadarMetadataRepository(
            clock = testClock,
            httpClient = stableClient,
            ioDispatcher = Dispatchers.Default
        )

        val firstState = refreshRepository.refreshMetadata()
        testClock.advanceMonoMs(2_000L)
        testClock.advanceWallMs(2_000L)
        val secondState = refreshRepository.refreshMetadata()

        assertEquals(1_000L, firstState.lastContentChangeWallMs)
        assertEquals(1_000L, secondState.lastContentChangeWallMs)
    }

    @Test
    fun refreshMetadata_updatesLastSuccessAndContentChangeWhenGenerationChanges() = runBlocking {
        val testClock = FakeClock(monoMs = 1_000L, wallMs = 1_000L)
        val callCount = AtomicInteger(0)
        val rotatingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val nextGenerated = if (callCount.getAndIncrement() == 0) {
                    1_771_554_638L
                } else {
                    1_771_555_238L
                }
                metadataResponse(
                    request = chain.request(),
                    generatedEpochSec = nextGenerated
                )
            }
            .build()
        val refreshRepository = WeatherRadarMetadataRepository(
            clock = testClock,
            httpClient = rotatingClient,
            ioDispatcher = Dispatchers.Default
        )

        val firstState = refreshRepository.refreshMetadata()
        testClock.advanceMonoMs(2_000L)
        testClock.advanceWallMs(2_000L)
        val secondState = refreshRepository.refreshMetadata()

        assertEquals(1_000L, firstState.lastSuccessfulFetchWallMs)
        assertEquals(3_000L, secondState.lastSuccessfulFetchWallMs)
        assertEquals(1_000L, firstState.lastContentChangeWallMs)
        assertEquals(3_000L, secondState.lastContentChangeWallMs)
    }

    @Test
    fun refreshMetadata_sendsConditionalHeadersAfterInitialSuccess() = runBlocking {
        val ifNoneMatchHeaders = mutableListOf<String?>()
        val ifModifiedSinceHeaders = mutableListOf<String?>()
        val testClock = FakeClock(monoMs = 1_000L, wallMs = 1_000L)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                ifNoneMatchHeaders.add(request.header("If-None-Match"))
                ifModifiedSinceHeaders.add(request.header("If-Modified-Since"))
                metadataResponse(
                    request = request,
                    generatedEpochSec = 1_771_554_638L,
                    etag = "etag-1",
                    lastModified = "Thu, 01 Jan 1970 00:00:00 GMT"
                )
            }
            .build()
        val refreshRepository = WeatherRadarMetadataRepository(
            clock = testClock,
            httpClient = client,
            ioDispatcher = Dispatchers.Default
        )

        refreshRepository.refreshMetadata()
        testClock.advanceMonoMs(2_000L)
        testClock.advanceWallMs(2_000L)
        refreshRepository.refreshMetadata()

        assertEquals(2, ifNoneMatchHeaders.size)
        assertNull(ifNoneMatchHeaders[0])
        assertNull(ifModifiedSinceHeaders[0])
        assertEquals("etag-1", ifNoneMatchHeaders[1])
        assertEquals("Thu, 01 Jan 1970 00:00:00 GMT", ifModifiedSinceHeaders[1])
    }

    @Test
    fun refreshMetadata_handlesNotModifiedAsSuccessfulFetch() = runBlocking {
        val testClock = FakeClock(monoMs = 1_000L, wallMs = 1_000L)
        val callCount = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (callCount.getAndIncrement() == 0) {
                    metadataResponse(
                        request = request,
                        generatedEpochSec = 1_771_554_638L,
                        etag = "etag-1",
                        lastModified = "Thu, 01 Jan 1970 00:00:00 GMT"
                    )
                } else {
                    notModifiedResponse(request)
                }
            }
            .build()
        val refreshRepository = WeatherRadarMetadataRepository(
            clock = testClock,
            httpClient = client,
            ioDispatcher = Dispatchers.Default
        )

        val firstState = refreshRepository.refreshMetadata()
        testClock.advanceMonoMs(2_000L)
        testClock.advanceWallMs(2_000L)
        val secondState = refreshRepository.refreshMetadata()

        assertEquals(WeatherRadarStatusCode.OK, secondState.status)
        assertEquals(firstState.metadata, secondState.metadata)
        assertEquals(3_000L, secondState.lastSuccessfulFetchWallMs)
        assertEquals(1_000L, secondState.lastContentChangeWallMs)
        assertEquals("HTTP 304 not modified", secondState.detail)
    }

    @Test
    fun refreshMetadata_returnsNoMetadataWhenNotModifiedWithoutCache() = runBlocking {
        val testClock = FakeClock(monoMs = 1_000L, wallMs = 1_000L)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> notModifiedResponse(chain.request()) }
            .build()
        val refreshRepository = WeatherRadarMetadataRepository(
            clock = testClock,
            httpClient = client,
            ioDispatcher = Dispatchers.Default
        )

        val state = refreshRepository.refreshMetadata()

        assertEquals(WeatherRadarStatusCode.NO_METADATA, state.status)
        assertNull(state.metadata)
        assertNull(state.lastSuccessfulFetchWallMs)
        assertNull(state.lastContentChangeWallMs)
        assertEquals("HTTP 304 without cached metadata", state.detail)
    }

    @Test
    fun refreshMetadata_returnsNoFramesWhenPayloadHasNoPastFrames() = runBlocking {
        val testClock = FakeClock(monoMs = 1_000L, wallMs = 1_000L)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                noFramesMetadataResponse(
                    request = chain.request(),
                    generatedEpochSec = 1_771_554_638L
                )
            }
            .build()
        val refreshRepository = WeatherRadarMetadataRepository(
            clock = testClock,
            httpClient = client,
            ioDispatcher = Dispatchers.Default
        )

        val state = refreshRepository.refreshMetadata()

        assertEquals(WeatherRadarStatusCode.NO_FRAMES, state.status)
        assertEquals(0, state.metadata?.pastFrames?.size)
        assertEquals(1_000L, state.lastSuccessfulFetchWallMs)
        assertNull(state.lastContentChangeWallMs)
        assertEquals("radar.past is empty", state.detail)
    }

    @Test
    fun refreshMetadata_preservesCachedMetadataWhenNoFramesAfterSuccess() = runBlocking {
        val testClock = FakeClock(monoMs = 1_000L, wallMs = 1_000L)
        val callCount = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (callCount.getAndIncrement() == 0) {
                    metadataResponse(
                        request = request,
                        generatedEpochSec = 1_771_554_638L
                    )
                } else {
                    noFramesMetadataResponse(
                        request = request,
                        generatedEpochSec = 1_771_555_238L
                    )
                }
            }
            .build()
        val refreshRepository = WeatherRadarMetadataRepository(
            clock = testClock,
            httpClient = client,
            ioDispatcher = Dispatchers.Default
        )

        val firstState = refreshRepository.refreshMetadata()
        testClock.advanceMonoMs(2_000L)
        testClock.advanceWallMs(2_000L)
        val secondState = refreshRepository.refreshMetadata()

        assertEquals(WeatherRadarStatusCode.OK, firstState.status)
        assertEquals(WeatherRadarStatusCode.NO_FRAMES, secondState.status)
        assertEquals(firstState.metadata, secondState.metadata)
        assertEquals(3_000L, secondState.lastSuccessfulFetchWallMs)
        assertEquals(firstState.lastContentChangeWallMs, secondState.lastContentChangeWallMs)
        assertEquals("radar.past is empty", secondState.detail)
    }

    private fun metadataResponse(
        request: Request,
        generatedEpochSec: Long,
        etag: String? = null,
        lastModified: String? = null
    ): Response {
        val responseBuilder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(
                """
                {
                  "generated": $generatedEpochSec,
                  "host": "https://tilecache.rainviewer.com",
                  "radar": {
                    "past": [
                      {"time": 1771554600, "path": "/v2/radar/1771554600"}
                    ]
                  }
                }
                """.trimIndent().toResponseBody("application/json".toMediaType())
            )
        if (!etag.isNullOrBlank()) {
            responseBuilder.header("ETag", etag)
        }
        if (!lastModified.isNullOrBlank()) {
            responseBuilder.header("Last-Modified", lastModified)
        }
        return responseBuilder.build()
    }

    private fun noFramesMetadataResponse(
        request: Request,
        generatedEpochSec: Long
    ): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(
                """
                {
                  "generated": $generatedEpochSec,
                  "host": "https://tilecache.rainviewer.com",
                  "radar": {
                    "past": []
                  }
                }
                """.trimIndent().toResponseBody("application/json".toMediaType())
            )
            .build()

    private fun notModifiedResponse(request: Request): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(304)
            .message("Not Modified")
            .body("".toResponseBody("application/json".toMediaType()))
            .build()
}
