package com.example.xcpro.adsb

import com.example.xcpro.core.time.FakeClock
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import com.example.xcpro.testing.OkHttpClientRegistry
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class OpenSkyTokenRepositoryTest {
    private val okHttpClients = OkHttpClientRegistry()

    @After
    fun tearDown() {
        okHttpClients.shutdownAll()
    }

    @Test
    fun concurrentRequests_shareSingleTokenFetch() = runTest {
        val credentialsRepository = mock<OpenSkyCredentialsRepository>()
        whenever(credentialsRepository.loadCredentials()).thenReturn(
            OpenSkyClientCredentials(
                clientId = "client-id",
                clientSecret = "client-secret"
            )
        )
        val networkFetchCount = AtomicInteger(0)
        val requestStarted = CompletableDeferred<Unit>()
        val allowResponse = CompletableDeferred<Unit>()
        val client = okHttpClients.register(
            OkHttpClient.Builder()
            .addInterceptor { chain ->
                networkFetchCount.incrementAndGet()
                requestStarted.complete(Unit)
                runBlocking { allowResponse.await() }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"access_token":"token-123","expires_in":1800}""".toResponseBody())
                    .build()
            }
            .build()
        )

        val repository = OpenSkyTokenRepositoryImpl(
            credentialsRepository = credentialsRepository,
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            httpClient = client,
            dispatcher = Dispatchers.IO
        )

        val tokens = coroutineScope {
            val inFlight = (1..8).map {
                async(Dispatchers.Default) {
                    repository.getValidTokenOrNull()
                }
            }
            requestStarted.await()
            allowResponse.complete(Unit)
            inFlight.awaitAll()
        }

        assertTrue(tokens.all { it == "token-123" })
        assertEquals(1, networkFetchCount.get())
    }

    @Test
    fun transientFailure_entersCooldownAndSkipsImmediateRefetch() = runTest {
        val credentialsRepository = mock<OpenSkyCredentialsRepository>()
        whenever(credentialsRepository.loadCredentials()).thenReturn(
            OpenSkyClientCredentials(
                clientId = "client-id",
                clientSecret = "client-secret"
            )
        )
        val fetchCount = AtomicInteger(0)
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    fetchCount.incrementAndGet()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(500)
                        .message("Server Error")
                        .body("""{"error":"temporary"}""".toResponseBody())
                        .build()
                }
                .build()
        )
        val clock = FakeClock(monoMs = 10_000L, wallMs = 0L)
        val repository = OpenSkyTokenRepositoryImpl(
            credentialsRepository = credentialsRepository,
            clock = clock,
            httpClient = client,
            dispatcher = Dispatchers.IO
        )

        val first = repository.getTokenAccessState()
        val second = repository.getTokenAccessState()

        assertTrue(first is OpenSkyTokenAccessState.TransientFailure)
        assertTrue(second is OpenSkyTokenAccessState.TransientFailure)
        assertEquals(1, fetchCount.get())
    }

    @Test
    fun transientFailure_retriesAfterCooldownWindow() = runTest {
        val credentialsRepository = mock<OpenSkyCredentialsRepository>()
        whenever(credentialsRepository.loadCredentials()).thenReturn(
            OpenSkyClientCredentials(
                clientId = "client-id",
                clientSecret = "client-secret"
            )
        )
        val fetchCount = AtomicInteger(0)
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val call = fetchCount.incrementAndGet()
                    if (call == 1) {
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(500)
                            .message("Server Error")
                            .body("""{"error":"temporary"}""".toResponseBody())
                            .build()
                    } else {
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"access_token":"token-123","expires_in":1800}""".toResponseBody())
                            .build()
                    }
                }
                .build()
        )
        val clock = FakeClock(monoMs = 100_000L, wallMs = 0L)
        val repository = OpenSkyTokenRepositoryImpl(
            credentialsRepository = credentialsRepository,
            clock = clock,
            httpClient = client,
            dispatcher = Dispatchers.IO
        )

        val first = repository.getTokenAccessState()
        val duringCooldown = repository.getTokenAccessState()
        clock.advanceMonoMs(30_001L)
        val afterCooldown = repository.getTokenAccessState()

        assertTrue(first is OpenSkyTokenAccessState.TransientFailure)
        assertTrue(duringCooldown is OpenSkyTokenAccessState.TransientFailure)
        assertTrue(afterCooldown is OpenSkyTokenAccessState.Available)
        assertEquals(2, fetchCount.get())
    }

    @Test
    fun invalidate_clearsTransientFailureCooldown_andAllowsImmediateRetry() = runTest {
        val credentialsRepository = mock<OpenSkyCredentialsRepository>()
        whenever(credentialsRepository.loadCredentials()).thenReturn(
            OpenSkyClientCredentials(
                clientId = "client-id",
                clientSecret = "client-secret"
            )
        )
        val fetchCount = AtomicInteger(0)
        val client = okHttpClients.register(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val call = fetchCount.incrementAndGet()
                    if (call == 1) {
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(500)
                            .message("Server Error")
                            .body("""{"error":"temporary"}""".toResponseBody())
                            .build()
                    } else {
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"access_token":"token-123","expires_in":1800}""".toResponseBody())
                            .build()
                    }
                }
                .build()
        )
        val clock = FakeClock(monoMs = 300_000L, wallMs = 0L)
        val repository = OpenSkyTokenRepositoryImpl(
            credentialsRepository = credentialsRepository,
            clock = clock,
            httpClient = client,
            dispatcher = Dispatchers.IO
        )

        val first = repository.getTokenAccessState()
        val duringCooldown = repository.getTokenAccessState()
        repository.invalidate()
        val afterInvalidate = repository.getTokenAccessState()

        assertTrue(first is OpenSkyTokenAccessState.TransientFailure)
        assertTrue(duringCooldown is OpenSkyTokenAccessState.TransientFailure)
        assertTrue(afterInvalidate is OpenSkyTokenAccessState.Available)
        assertEquals(2, fetchCount.get())
    }
}
