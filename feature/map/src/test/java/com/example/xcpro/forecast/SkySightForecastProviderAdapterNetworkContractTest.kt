package com.example.xcpro.forecast

import com.example.xcpro.testing.OkHttpClientRegistry
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SkySightForecastProviderAdapterNetworkContractTest {
    private val okHttpClients = OkHttpClientRegistry()

    @After
    fun tearDown() {
        okHttpClients.shutdownAll()
    }

    @Test
    fun getLegend_retriesTransientServerFailure_thenSucceeds() = runTest {
        var callCount = 0
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    callCount += 1
                    if (callCount == 1) {
                        response(
                            request = chain.request(),
                            code = 503,
                            message = "Service Unavailable",
                            body = "{\"message\":\"Try again\"}"
                        )
                    } else {
                        response(
                            request = chain.request(),
                            code = 200,
                            message = "OK",
                            body = """[{"value":0.0,"rgb":[0,0,0]}]"""
                        )
                    }
                }
                .build()
        )
        val adapter = createAdapter(
            client = client,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val legend = adapter.getLegend(
            parameterId = ForecastParameterId("wstar_bsratio"),
            timeSlot = ForecastTimeSlot(validTimeUtcMs = 1_739_448_000_000L),
            regionCode = "WEST_US"
        )

        assertEquals(2, callCount)
        assertEquals("m/s", legend.unitLabel)
        assertEquals(1, legend.stops.size)
    }

    @Test
    fun getLegend_nonRetryableHttpFailure_throwsWithoutRetry() = runTest {
        var callCount = 0
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    callCount += 1
                    response(
                        request = chain.request(),
                        code = 404,
                        message = "Not Found",
                        body = "{\"message\":\"Missing\"}"
                    )
                }
                .build()
        )
        val adapter = createAdapter(
            client = client,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val failure = runCatching {
            adapter.getLegend(
                parameterId = ForecastParameterId("wstar_bsratio"),
                timeSlot = ForecastTimeSlot(validTimeUtcMs = 1_739_448_000_000L),
                regionCode = "WEST_US"
            )
        }.exceptionOrNull()

        assertEquals(1, callCount)
        assertTrue(failure is IOException)
        assertTrue(failure?.message?.contains("HTTP 404") == true)
        assertTrue(failure?.message?.contains("legend request") == true)
        assertTrue(failure?.message?.contains("https://") == false)
    }

    @Test
    fun getValue_addsSkySightHeadersOnPointRequest() = runTest {
        var capturedRequest: Request? = null
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedRequest = chain.request()
                    response(
                        request = chain.request(),
                        code = 200,
                        message = "OK",
                        body = """{"sfcwindspd":[12.5],"sfcwinddir":[240.0]}"""
                    )
                }
                .build()
        )
        val adapter = createAdapter(
            client = client,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        adapter.getValue(
            latitude = 37.5,
            longitude = -122.2,
            parameterId = ForecastParameterId("sfcwind0"),
            timeSlot = ForecastTimeSlot(validTimeUtcMs = 1_739_448_000_000L),
            regionCode = "WEST_US"
        )

        assertEquals("test-key", capturedRequest?.header("X-API-KEY"))
        assertEquals("https://xalps.skysight.io", capturedRequest?.header("Origin"))
        assertTrue(capturedRequest?.header("Accept")?.contains("application/json") == true)
    }

    @Test
    fun getTileSpec_unsafeParameterId_fallsBackToDefaultParameter() = runTest {
        val adapter = createAdapter(
            client = okHttpClients.register(OkHttpClient()),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val tileSpec = adapter.getTileSpec(
            parameterId = ForecastParameterId("../bad"),
            timeSlot = ForecastTimeSlot(validTimeUtcMs = 1_739_448_000_000L),
            regionCode = "WEST_US"
        )

        assertTrue(tileSpec.urlTemplate.contains("/wstar_bsratio/{z}/{x}/{y}"))
    }

    @Test
    fun getValue_httpFailure_messageDoesNotLeakCoordinatesOrRawUrl() = runTest {
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    response(
                        request = chain.request(),
                        code = 404,
                        message = "Not Found",
                        body = "{\"message\":\"Missing\"}"
                    )
                }
                .build()
        )
        val adapter = createAdapter(
            client = client,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val failure = runCatching {
            adapter.getValue(
                latitude = 37.5,
                longitude = -122.2,
                parameterId = ForecastParameterId("sfcwind0"),
                timeSlot = ForecastTimeSlot(validTimeUtcMs = 1_739_448_000_000L),
                regionCode = "WEST_US"
            )
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure?.message?.contains("point-value request") == true)
        assertTrue(failure?.message?.contains("https://") == false)
        assertTrue(failure?.message?.contains("37.5") == false)
        assertTrue(failure?.message?.contains("-122.2") == false)
    }

    @Test
    fun getValue_missingApiKey_failsFastBeforeNetworkCall() = runTest {
        var callCount = 0
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    callCount += 1
                    response(
                        request = chain.request(),
                        code = 200,
                        message = "OK",
                        body = """{"sfcwindspd":[12.5],"sfcwinddir":[240.0]}"""
                    )
                }
                .build()
        )
        val adapter = createAdapter(
            client = client,
            dispatcher = StandardTestDispatcher(testScheduler),
            apiKey = "   "
        )

        val failure = runCatching {
            adapter.getValue(
                latitude = 37.5,
                longitude = -122.2,
                parameterId = ForecastParameterId("sfcwind0"),
                timeSlot = ForecastTimeSlot(validTimeUtcMs = 1_739_448_000_000L),
                regionCode = "WEST_US"
            )
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure?.message?.contains("API key is missing") == true)
        assertEquals(0, callCount)
    }

    private fun createAdapter(
        client: OkHttpClient,
        dispatcher: CoroutineDispatcher,
        apiKey: String = "test-key"
    ): SkySightForecastProviderAdapter {
        return SkySightForecastProviderAdapter(
            httpClient = client,
            skySightApiKey = apiKey,
            dispatcher = dispatcher
        )
    }

    private fun response(
        request: Request,
        code: Int,
        message: String,
        body: String
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body(body.toResponseBody())
            .build()
    }
}
