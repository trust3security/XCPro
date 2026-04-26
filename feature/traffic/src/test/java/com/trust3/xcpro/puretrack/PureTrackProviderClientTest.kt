package com.trust3.xcpro.puretrack

import com.trust3.xcpro.testing.OkHttpClientRegistry
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PureTrackProviderClientTest {
    private val okHttpClients = OkHttpClientRegistry()

    @After
    fun tearDown() {
        okHttpClients.shutdownAll()
    }

    @Test
    fun login_missingAppKeyDoesNotCallNetwork() = runTest {
        val provider = provider(
            appKey = "   ",
            client = failOnNetworkClient()
        )

        val result = provider.login(email = "pilot@example.com", password = "password")

        assertTrue(result is PureTrackProviderResult.MissingAppKey)
    }

    @Test
    fun login_serializesFormAndParsesProSession() = runTest {
        var capturedRequest: Request? = null
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedRequest = chain.request()
                    jsonResponse(
                        request = chain.request(),
                        body = """{"access_token":"token-123","pro":true}"""
                    )
                }
                .build()
        )
        val provider = provider(appKey = " app-key ", client = client)

        val result = provider.login(
            email = "pilot@example.com",
            password = "secret-password"
        )

        assertTrue(result is PureTrackProviderResult.Success)
        val success = result as PureTrackProviderResult.Success
        assertEquals(PureTrackLoginSession(accessToken = "token-123", pro = true), success.value)
        val form = capturedRequest?.body as FormBody
        assertEquals("app-key", form.value("key"))
        assertEquals("pilot@example.com", form.value("email"))
        assertEquals("secret-password", form.value("password"))
        assertEquals("POST", capturedRequest?.method)
        assertEquals("https://puretrack.io/api/login", capturedRequest?.url.toString())
    }

    @Test
    fun login_parsesNonProSession() = runTest {
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    jsonResponse(
                        request = chain.request(),
                        body = """{"access_token":"token-123","pro":false}"""
                    )
                }
                .build()
        )
        val provider = provider(appKey = "app-key", client = client)

        val result = provider.login(email = "pilot@example.com", password = "password")

        assertTrue(result is PureTrackProviderResult.Success)
        val success = result as PureTrackProviderResult.Success
        assertEquals(false, success.value.pro)
    }

    @Test
    fun login_malformedJsonMapsToMalformedResponse() = runTest {
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain -> jsonResponse(chain.request(), "not-json") }
                .build()
        )
        val provider = provider(appKey = "app-key", client = client)

        val result = provider.login(email = "pilot@example.com", password = "password")

        assertTrue(result is PureTrackProviderResult.NetworkError)
        val error = result as PureTrackProviderResult.NetworkError
        assertEquals(PureTrackNetworkFailureKind.MALFORMED_RESPONSE, error.kind)
    }

    @Test
    fun fetchTraffic_blankBearerDoesNotCallNetwork() = runTest {
        val provider = provider(
            appKey = "app-key",
            client = failOnNetworkClient()
        )

        val result = provider.fetchTraffic(
            request = trafficRequest(),
            bearerToken = "  "
        )

        assertTrue(result is PureTrackProviderResult.MissingBearerToken)
    }

    @Test
    fun fetchTraffic_serializesFormHeadersAndParsesRows() = runTest {
        var capturedRequest: Request? = null
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedRequest = chain.request()
                    jsonResponse(
                        request = chain.request(),
                        body = """{"data":["T1710000000,L-33.8,G151.2,Kpt-1","bad"]}"""
                    )
                }
                .build()
        )
        val provider = provider(appKey = "app-key", client = client)

        val result = provider.fetchTraffic(
            request = trafficRequest(),
            bearerToken = " token-123 "
        )

        assertTrue(result is PureTrackProviderResult.Success)
        val success = result as PureTrackProviderResult.Success
        assertEquals(1, success.value.targets.size)
        assertEquals("pt-1", success.value.targets.single().key)
        assertEquals(1, success.value.diagnostics.parsedRows)
        assertEquals(1, success.value.diagnostics.droppedRows)
        val form = capturedRequest?.body as FormBody
        assertEquals("app-key", form.value("key"))
        assertEquals("-33.800123", form.value("lat1"))
        assertEquals("151.200988", form.value("long1"))
        assertEquals("-33.900123", form.value("lat2"))
        assertEquals("151.100988", form.value("long2"))
        assertEquals("5", form.value("t"))
        assertEquals("air", form.value("cat"))
        assertEquals("3,8", form.value("o"))
        assertEquals("pt-1,pt-2", form.value("s"))
        assertEquals("1", form.value("i"))
        assertEquals("Bearer token-123", capturedRequest?.header("Authorization"))
        assertEquals("https://puretrack.io/api/traffic", capturedRequest?.url.toString())
    }

    @Test
    fun fetchTraffic_acceptsRootArrayRows() = runTest {
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    jsonResponse(
                        request = chain.request(),
                        body = """["T1710000000,L-33.8,G151.2,Kpt-1"]"""
                    )
                }
                .build()
        )
        val provider = provider(appKey = "app-key", client = client)

        val result = provider.fetchTraffic(
            request = trafficRequest(),
            bearerToken = "token-123"
        )

        assertTrue(result is PureTrackProviderResult.Success)
        val success = result as PureTrackProviderResult.Success
        assertEquals("pt-1", success.value.targets.single().key)
    }

    @Test
    fun fetchTraffic_malformedJsonMapsToMalformedResponse() = runTest {
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain -> jsonResponse(chain.request(), "not-json") }
                .build()
        )
        val provider = provider(appKey = "app-key", client = client)

        val result = provider.fetchTraffic(
            request = trafficRequest(),
            bearerToken = "token-123"
        )

        assertTrue(result is PureTrackProviderResult.NetworkError)
        val error = result as PureTrackProviderResult.NetworkError
        assertEquals(PureTrackNetworkFailureKind.MALFORMED_RESPONSE, error.kind)
    }

    @Test
    fun fetchTraffic_rateLimitedReadsRetryAfter() = runTest {
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("Too Many Requests")
                        .header("Retry-After", "30")
                        .body("".toResponseBody())
                        .build()
                }
                .build()
        )
        val provider = provider(appKey = "app-key", client = client)

        val result = provider.fetchTraffic(
            request = trafficRequest(),
            bearerToken = "token-123"
        )

        assertEquals(PureTrackProviderResult.RateLimited(retryAfterSec = 30), result)
    }

    @Test
    fun unknownHostMapsToDnsFailure() = runTest {
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { throw UnknownHostException("dns") }
                .build()
        )
        val provider = provider(appKey = "app-key", client = client)

        val result = provider.fetchTraffic(
            request = trafficRequest(),
            bearerToken = "token-123"
        )

        assertTrue(result is PureTrackProviderResult.NetworkError)
        val error = result as PureTrackProviderResult.NetworkError
        assertEquals(PureTrackNetworkFailureKind.DNS, error.kind)
    }

    @Test
    fun httpErrorDoesNotReturnResponseBody() = runTest {
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(401)
                        .message("Unauthorized")
                        .body("secret-body".toResponseBody())
                        .build()
                }
                .build()
        )
        val provider = provider(appKey = "app-key", client = client)

        val result = provider.login(email = "pilot@example.com", password = "password")

        assertEquals(PureTrackProviderResult.HttpError(401, "Unauthorized"), result)
    }

    private fun provider(
        appKey: String?,
        client: OkHttpClient
    ): OkHttpPureTrackProviderClient =
        OkHttpPureTrackProviderClient(
            appKeyProvider = FakePureTrackAppKeyProvider(appKey),
            httpClient = client,
            dispatcher = UnconfinedTestDispatcher()
        )

    private fun failOnNetworkClient(): OkHttpClient =
        okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { throw IOException("network should not be called") }
                .build()
        )

    private fun trafficRequest(): PureTrackTrafficRequest =
        PureTrackTrafficRequest(
            bounds = PureTrackBounds(
                topRightLatitude = -33.8001234,
                topRightLongitude = 151.2009876,
                bottomLeftLatitude = -33.9001234,
                bottomLeftLongitude = 151.1009876
            ),
            category = PureTrackCategory.AIR,
            objectTypeIds = setOf(8, 3),
            maxAgeMinutes = 5,
            alwaysIncludeKeys = setOf("pt-2", "pt-1"),
            isolateAlwaysIncludeKeys = true
        )

    private fun jsonResponse(request: Request, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody())
            .build()

    private fun FormBody.value(fieldName: String): String? {
        for (index in 0 until size) {
            if (name(index) == fieldName) {
                return value(index)
            }
        }
        return null
    }

    private class FakePureTrackAppKeyProvider(
        private val appKey: String?
    ) : PureTrackAppKeyProvider {
        override fun loadAppKey(): String? = appKey
    }
}
