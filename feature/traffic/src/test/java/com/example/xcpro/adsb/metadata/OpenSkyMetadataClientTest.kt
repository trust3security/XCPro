package com.example.xcpro.adsb.metadata

import android.content.Context
import com.example.xcpro.adsb.metadata.data.OpenSkyMetadataClient
import com.example.xcpro.testing.OkHttpClientRegistry
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class OpenSkyMetadataClientTest {
    private val okHttpClients = OkHttpClientRegistry()
    private val tempDirs = mutableListOf<File>()

    @After
    fun tearDown() {
        okHttpClients.shutdownAll()
        tempDirs.forEach { dir ->
            runCatching { dir.deleteRecursively() }
        }
        tempDirs.clear()
    }

    @Test
    fun listMetadataKeys_collectsPaginatedKeysWithContinuationToken() = runTest {
        val client = OpenSkyMetadataClient(
            context = mockContextWithTempCacheDir(),
            httpClient = okHttpClients.register(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val token = chain.request().url.queryParameter("continuation-token")
                        val body = if (token == null) {
                            listingXml(
                                keys = listOf("metadata/a.csv"),
                                isTruncated = true,
                                nextTokenTag = "NextContinuationToken",
                                nextToken = "page-2"
                            )
                        } else {
                            listingXml(
                                keys = listOf("metadata/b.csv"),
                                isTruncated = false,
                                nextTokenTag = null,
                                nextToken = null
                            )
                        }
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody())
                            .build()
                    }
                    .build()
            ),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.listMetadataKeys()

        assertTrue(result.isSuccess)
        assertEquals(listOf("metadata/a.csv", "metadata/b.csv"), result.getOrNull())
    }

    @Test
    fun listMetadataKeys_supportsNextMarkerFallbackToken() = runTest {
        val client = OpenSkyMetadataClient(
            context = mockContextWithTempCacheDir(),
            httpClient = okHttpClients.register(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val token = chain.request().url.queryParameter("continuation-token")
                        val body = if (token == null) {
                            listingXml(
                                keys = listOf("metadata/a.csv"),
                                isTruncated = true,
                                nextTokenTag = "NextMarker",
                                nextToken = "marker-2"
                            )
                        } else {
                            listingXml(
                                keys = listOf("metadata/c.csv"),
                                isTruncated = false,
                                nextTokenTag = null,
                                nextToken = null
                            )
                        }
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody())
                            .build()
                    }
                    .build()
            ),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.listMetadataKeys()

        assertTrue(result.isSuccess)
        assertEquals(listOf("metadata/a.csv", "metadata/c.csv"), result.getOrNull())
    }

    @Test
    fun listMetadataKeys_failsFastOnContinuationTokenLoop() = runTest {
        val requestCount = AtomicInteger(0)
        val client = OpenSkyMetadataClient(
            context = mockContextWithTempCacheDir(),
            httpClient = okHttpClients.register(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        requestCount.incrementAndGet()
                        val body = listingXml(
                            keys = listOf("metadata/a.csv"),
                            isTruncated = true,
                            nextTokenTag = "NextContinuationToken",
                            nextToken = "loop-token"
                        )
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody())
                            .build()
                    }
                    .build()
            ),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.listMetadataKeys()

        assertTrue(result.isFailure)
        assertTrue(requestCount.get() <= 3)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("continuation token loop", ignoreCase = true) == true
        )
    }

    @Test
    fun downloadCsv_closesHttpResponseBeforeImporterBlockRuns() = runTest {
        val csv = "icao24,registration\nabc123,N1\n"
        val responseClosed = AtomicBoolean(false)
        val responseBody = object : ResponseBody() {
            private val delegate = csv.toResponseBody()
            override fun contentType() = delegate.contentType()
            override fun contentLength() = delegate.contentLength()
            override fun source(): BufferedSource = delegate.source()
            override fun close() {
                responseClosed.set(true)
                delegate.close()
            }
        }
        val client = OpenSkyMetadataClient(
            context = mockContextWithTempCacheDir(),
            httpClient = okHttpClients.register(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("ETag", "\"etag-123\"")
                            .body(responseBody)
                            .build()
                    }
                    .build()
            ),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.downloadCsv("https://example.test/metadata.csv") { input, etag ->
            assertTrue(responseClosed.get())
            assertEquals("etag-123", etag)
            input.bufferedReader().readText()
        }

        assertTrue(result.isSuccess)
        assertEquals(csv, result.getOrNull())
    }

    private fun mockContextWithTempCacheDir(): Context {
        val tempDir = Files.createTempDirectory("adsb-metadata-client-test").toFile()
        tempDirs += tempDir
        val context = mock<Context>()
        whenever(context.cacheDir).thenReturn(tempDir)
        return context
    }

    private fun listingXml(
        keys: List<String>,
        isTruncated: Boolean,
        nextTokenTag: String?,
        nextToken: String?
    ): String {
        val keyRows = keys.joinToString(separator = "") { key ->
            "<Contents><Key>$key</Key></Contents>"
        }
        val tokenRow = if (nextTokenTag != null && !nextToken.isNullOrBlank()) {
            "<$nextTokenTag>$nextToken</$nextTokenTag>"
        } else {
            ""
        }
        return """
            <ListBucketResult>
              $keyRows
              <IsTruncated>${isTruncated.toString().lowercase()}</IsTruncated>
              $tokenRow
            </ListBucketResult>
        """.trimIndent()
    }
}
