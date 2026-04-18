package com.trust3.xcpro.forecast

import com.trust3.xcpro.testing.OkHttpClientRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ConnectException
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ForecastAuthRepositoryTest {
    private val okHttpClients = OkHttpClientRegistry()
    private val credentialsRepository: ForecastCredentialsRepository = mock()

    @After
    fun tearDown() {
        okHttpClients.shutdownAll()
    }

    @Test
    fun verifySavedCredentials_missingCredentials_returnsMissingCredentials() = runTest {
        whenever(credentialsRepository.loadCredentials()).thenReturn(null)
        val repository = createRepository(
            httpClient = okHttpClients.register(OkHttpClient()),
            apiKey = "test-key",
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.verifySavedCredentials()

        assertEquals(ForecastAuthCheckResult.MissingCredentials, result)
    }

    @Test
    fun verifySavedCredentials_blankApiKey_returnsMissingApiKey() = runTest {
        whenever(credentialsRepository.loadCredentials()).thenReturn(
            ForecastProviderCredentials(
                username = "pilot@example.com",
                password = "secret"
            )
        )
        val repository = createRepository(
            httpClient = okHttpClients.register(OkHttpClient()),
            apiKey = "   ",
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.verifySavedCredentials()

        assertEquals(ForecastAuthCheckResult.MissingApiKey, result)
    }

    @Test
    fun verifySavedCredentials_success_returnsSuccessAndSendsApiKeyHeader() = runTest {
        whenever(credentialsRepository.loadCredentials()).thenReturn(
            ForecastProviderCredentials(
                username = "pilot@example.com",
                password = "secret"
            )
        )
        var capturedRequest: Request? = null
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedRequest = chain.request()
                    response(
                        request = chain.request(),
                        code = 200,
                        message = "OK",
                        body = "{\"message\":\"Authenticated\"}"
                    )
                }
                .build()
        )
        val repository = createRepository(
            httpClient = client,
            apiKey = "test-key",
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.verifySavedCredentials()

        assertTrue(result is ForecastAuthCheckResult.Success)
        val success = result as ForecastAuthCheckResult.Success
        assertEquals(200, success.code)
        assertEquals("Authenticated", success.message)
        assertEquals("test-key", capturedRequest?.header("X-API-KEY"))
    }

    @Test
    fun verifySavedCredentials_invalidCredentials_mapsToInvalidCredentials() = runTest {
        whenever(credentialsRepository.loadCredentials()).thenReturn(
            ForecastProviderCredentials(
                username = "pilot@example.com",
                password = "wrong"
            )
        )
        var callCount = 0
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    callCount += 1
                    response(
                        request = chain.request(),
                        code = 401,
                        message = "Unauthorized",
                        body = "{\"message\":\"Invalid credentials\"}"
                    )
                }
                .build()
        )
        val repository = createRepository(
            httpClient = client,
            apiKey = "test-key",
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.verifySavedCredentials()

        assertTrue(result is ForecastAuthCheckResult.InvalidCredentials)
        val invalid = result as ForecastAuthCheckResult.InvalidCredentials
        assertEquals(401, invalid.code)
        assertEquals("Invalid credentials", invalid.message)
        assertEquals(1, callCount)
    }

    @Test
    fun verifySavedCredentials_rateLimited_mapsRetryAfter() = runTest {
        whenever(credentialsRepository.loadCredentials()).thenReturn(
            ForecastProviderCredentials(
                username = "pilot@example.com",
                password = "secret"
            )
        )
        var callCount = 0
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    callCount += 1
                    response(
                        request = chain.request(),
                        code = 429,
                        message = "Too Many Requests",
                        body = "{\"error\":\"Rate limited\"}",
                        retryAfter = "1"
                    )
                }
                .build()
        )
        val repository = createRepository(
            httpClient = client,
            apiKey = "test-key",
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.verifySavedCredentials()

        assertTrue(result is ForecastAuthCheckResult.RateLimited)
        val rateLimited = result as ForecastAuthCheckResult.RateLimited
        assertEquals(429, rateLimited.code)
        assertEquals("Rate limited", rateLimited.message)
        assertEquals(1, rateLimited.retryAfterSec)
        assertEquals(3, callCount)
    }

    @Test
    fun verifySavedCredentials_serverErrorThenSuccess_retriesAndReturnsSuccess() = runTest {
        whenever(credentialsRepository.loadCredentials()).thenReturn(
            ForecastProviderCredentials(
                username = "pilot@example.com",
                password = "secret"
            )
        )
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
                            body = "{\"message\":\"Authenticated\"}"
                        )
                    }
                }
                .build()
        )
        val repository = createRepository(
            httpClient = client,
            apiKey = "test-key",
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.verifySavedCredentials()

        assertTrue(result is ForecastAuthCheckResult.Success)
        val success = result as ForecastAuthCheckResult.Success
        assertEquals(200, success.code)
        assertEquals("Authenticated", success.message)
        assertEquals(2, callCount)
    }

    @Test
    fun verifySavedCredentials_retryableNetworkFailureThenSuccess_retriesAndReturnsSuccess() = runTest {
        whenever(credentialsRepository.loadCredentials()).thenReturn(
            ForecastProviderCredentials(
                username = "pilot@example.com",
                password = "secret"
            )
        )
        var callCount = 0
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    callCount += 1
                    if (callCount == 1) {
                        throw ConnectException("connect timeout")
                    }
                    response(
                        request = chain.request(),
                        code = 200,
                        message = "OK",
                        body = "{\"message\":\"Authenticated\"}"
                    )
                }
                .build()
        )
        val repository = createRepository(
            httpClient = client,
            apiKey = "test-key",
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.verifySavedCredentials()

        assertTrue(result is ForecastAuthCheckResult.Success)
        val success = result as ForecastAuthCheckResult.Success
        assertEquals(200, success.code)
        assertEquals("Authenticated", success.message)
        assertEquals(2, callCount)
    }

    @Test
    fun verifySavedCredentials_unknownHost_mapsToDnsNetworkError() = runTest {
        whenever(credentialsRepository.loadCredentials()).thenReturn(
            ForecastProviderCredentials(
                username = "pilot@example.com",
                password = "secret"
            )
        )
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor {
                    throw UnknownHostException("dns")
                }
                .build()
        )
        val repository = createRepository(
            httpClient = client,
            apiKey = "test-key",
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.verifySavedCredentials()

        assertTrue(result is ForecastAuthCheckResult.NetworkError)
        val networkError = result as ForecastAuthCheckResult.NetworkError
        assertEquals(ForecastAuthNetworkFailureKind.DNS, networkError.kind)
        assertEquals(false, networkError.retryable)
    }

    private fun createRepository(
        httpClient: OkHttpClient,
        apiKey: String,
        dispatcher: TestDispatcher
    ): ForecastAuthRepository {
        return ForecastAuthRepository(
            credentialsRepository = credentialsRepository,
            httpClient = httpClient,
            skySightApiKey = apiKey,
            dispatcher = dispatcher
        )
    }

    private fun response(
        request: Request,
        code: Int,
        message: String,
        body: String,
        retryAfter: String? = null
    ): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body(body.toResponseBody())
        if (!retryAfter.isNullOrBlank()) {
            builder.header("Retry-After", retryAfter)
        }
        return builder.build()
    }
}
