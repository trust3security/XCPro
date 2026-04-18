package com.trust3.xcpro.adsb

import java.io.InterruptedIOException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import com.trust3.xcpro.testing.OkHttpClientRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpenSkyProviderClientTest {
    private val okHttpClients = OkHttpClientRegistry()

    @After
    fun tearDown() {
        okHttpClients.shutdownAll()
    }

    @Test
    fun unknownHost_mapsToDnsFailureKind() = runTest {
        assertNetworkFailureKind(
            expected = AdsbNetworkFailureKind.DNS,
            throwable = UnknownHostException("dns"),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
    }

    @Test
    fun socketTimeout_mapsToTimeoutFailureKind() = runTest {
        assertNetworkFailureKind(
            expected = AdsbNetworkFailureKind.TIMEOUT,
            throwable = SocketTimeoutException("timeout"),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
    }

    @Test
    fun connectException_mapsToConnectFailureKind() = runTest {
        assertNetworkFailureKind(
            expected = AdsbNetworkFailureKind.CONNECT,
            throwable = ConnectException("connect"),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
    }

    @Test
    fun noRoute_mapsToNoRouteFailureKind() = runTest {
        assertNetworkFailureKind(
            expected = AdsbNetworkFailureKind.NO_ROUTE,
            throwable = NoRouteToHostException("route"),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
    }

    @Test
    fun sslException_mapsToTlsFailureKind() = runTest {
        assertNetworkFailureKind(
            expected = AdsbNetworkFailureKind.TLS,
            throwable = SSLException("tls"),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
    }

    @Test
    fun genericIo_mapsToUnknownFailureKind() = runTest {
        assertNetworkFailureKind(
            expected = AdsbNetworkFailureKind.UNKNOWN,
            throwable = IOException("io"),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
    }

    @Test
    fun interruptedIo_mapsToTimeoutFailureKind() = runTest {
        assertNetworkFailureKind(
            expected = AdsbNetworkFailureKind.TIMEOUT,
            throwable = InterruptedIOException("interrupted"),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
    }

    @Test
    fun malformedJson_mapsToMalformedResponseKind() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("not-json".toResponseBody())
                        .build()
                }
                .build()
        )
        val provider = OpenSkyProviderClient(
            httpClient = client,
            dispatcher = dispatcher
        )

        val result = provider.fetchStates(
            bbox = BBox(
                lamin = -33.90,
                lomin = 151.10,
                lamax = -33.80,
                lomax = 151.30
            ),
            auth = null
        )

        assertTrue(result is ProviderResult.NetworkError)
        val error = result as ProviderResult.NetworkError
        assertEquals(AdsbNetworkFailureKind.MALFORMED_RESPONSE, error.kind)
    }

    @Test
    fun fetchStates_serializesQueryWithExpectedPrecisionAndExtendedFlag() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var capturedRequest: okhttp3.Request? = null
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedRequest = chain.request()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{\"time\":1710000000,\"states\":[]}".toResponseBody())
                        .build()
                }
                .build()
        )
        val provider = OpenSkyProviderClient(
            httpClient = client,
            dispatcher = dispatcher
        )

        val result = provider.fetchStates(
            bbox = BBox(
                lamin = -33.9012349,
                lomin = 151.1098764,
                lamax = -33.8012349,
                lomax = 151.3098764
            ),
            auth = null
        )

        assertTrue(result is ProviderResult.Success)
        val request = capturedRequest
        assertTrue(request != null)
        assertEquals("-33.901235", request?.url?.queryParameter("lamin"))
        assertEquals("151.109876", request?.url?.queryParameter("lomin"))
        assertEquals("-33.801235", request?.url?.queryParameter("lamax"))
        assertEquals("151.309876", request?.url?.queryParameter("lomax"))
        assertEquals("1", request?.url?.queryParameter("extended"))
    }

    @Test
    fun fetchStates_setsAuthorizationHeaderOnlyForNonBlankBearerTokens() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val capturedAuthHeaders = mutableListOf<String?>()
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedAuthHeaders += chain.request().header("Authorization")
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{\"time\":1710000000,\"states\":[]}".toResponseBody())
                        .build()
                }
                .build()
        )
        val provider = OpenSkyProviderClient(
            httpClient = client,
            dispatcher = dispatcher
        )

        provider.fetchStates(
            bbox = BBox(
                lamin = -33.90,
                lomin = 151.10,
                lamax = -33.80,
                lomax = 151.30
            ),
            auth = AdsbAuth("token-123")
        )
        provider.fetchStates(
            bbox = BBox(
                lamin = -33.90,
                lomin = 151.10,
                lamax = -33.80,
                lomax = 151.30
            ),
            auth = AdsbAuth("   ")
        )

        assertEquals("Bearer token-123", capturedAuthHeaders[0])
        assertNull(capturedAuthHeaders[1])
    }

    private suspend fun assertNetworkFailureKind(
        expected: AdsbNetworkFailureKind,
        throwable: IOException,
        dispatcher: TestDispatcher
    ) {
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor {
                    throw throwable
                }
                .build()
        )
        val provider = OpenSkyProviderClient(
            httpClient = client,
            dispatcher = dispatcher
        )

        val result = provider.fetchStates(
            bbox = BBox(
                lamin = -33.90,
                lomin = 151.10,
                lamax = -33.80,
                lomax = 151.30
            ),
            auth = null
        )

        assertTrue(result is ProviderResult.NetworkError)
        val error = result as ProviderResult.NetworkError
        assertEquals(expected, error.kind)
    }
}
