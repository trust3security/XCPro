package com.example.xcpro.adsb.metadata

import com.example.xcpro.adsb.metadata.data.OpenSkyMetadataClient
import com.example.xcpro.testing.OkHttpClientRegistry
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpenSkyMetadataClientTest {
    private val okHttpClients = OkHttpClientRegistry()

    @After
    fun tearDown() {
        okHttpClients.shutdownAll()
    }

    @Test
    fun listMetadataKeys_collectsPaginatedKeysWithContinuationToken() = runTest {
        val client = OpenSkyMetadataClient(
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
