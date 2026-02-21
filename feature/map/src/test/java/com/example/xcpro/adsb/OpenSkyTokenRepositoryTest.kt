package com.example.xcpro.adsb

import com.example.xcpro.core.time.FakeClock
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class OpenSkyTokenRepositoryTest {

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
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                networkFetchCount.incrementAndGet()
                Thread.sleep(200L)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"access_token":"token-123","expires_in":1800}""".toResponseBody())
                    .build()
            }
            .build()

        val repository = OpenSkyTokenRepositoryImpl(
            credentialsRepository = credentialsRepository,
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            httpClient = client,
            dispatcher = Dispatchers.IO
        )

        val tokens = coroutineScope {
            (1..8).map {
                async(Dispatchers.Default) {
                    repository.getValidTokenOrNull()
                }
            }.awaitAll()
        }

        assertTrue(tokens.all { it == "token-123" })
        assertEquals(1, networkFetchCount.get())
    }
}
