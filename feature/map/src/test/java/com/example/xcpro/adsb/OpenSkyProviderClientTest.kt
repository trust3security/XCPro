package com.example.xcpro.adsb

import java.io.InterruptedIOException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpenSkyProviderClientTest {

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
        val client = OkHttpClient.Builder()
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

    private suspend fun assertNetworkFailureKind(
        expected: AdsbNetworkFailureKind,
        throwable: IOException,
        dispatcher: TestDispatcher
    ) {
        val client = OkHttpClient.Builder()
            .addInterceptor {
                throw throwable
            }
            .build()
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
