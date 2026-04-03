package com.example.xcpro.livefollow.data.following

import com.example.xcpro.livefollow.account.mockXcAccountRepository
import com.example.xcpro.livefollow.account.signedInAccountSnapshot
import com.example.xcpro.livefollow.model.LiveFollowTransportState
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentApiFollowingActivePilotsDataSourceTest {

    @Test
    fun fetchFollowingActivePilots_usesAuthenticatedEndpoint_andMapsPayload() = runTest {
        var authorizationHeader: String? = null
        val dataSource = CurrentApiFollowingActivePilotsDataSource(
            xcAccountRepository = mockXcAccountRepository(
                MutableStateFlow(signedInAccountSnapshot(accessToken = "follow-token"))
            ),
            httpClient = OkHttpClient.Builder().addInterceptor(
                Interceptor { chain ->
                    authorizationHeader = chain.request().header("Authorization")
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """
                                {
                                  "items": [
                                    {
                                      "session_id": "watch-1",
                                      "user_id": "pilot-1",
                                      "visibility": "followers",
                                      "share_code": "WATCH123",
                                      "status": "active",
                                      "display_label": "Pilot One",
                                      "last_position_at": "2024-03-21T00:20:05Z",
                                      "latest": {
                                        "lat": -33.9100,
                                        "lon": 151.2100,
                                        "alt": 510.0,
                                        "speed": 13.0,
                                        "heading": 185.0,
                                        "timestamp": "2024-03-21T00:20:05Z"
                                      }
                                    }
                                  ],
                                  "generated_at": "2024-03-21T00:20:10Z"
                                }
                            """.trimIndent().toResponseBody(JSON_MEDIA_TYPE)
                        )
                        .build()
                }
            ).build(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = dataSource.fetchFollowingActivePilots()

        require(result is FollowingActivePilotsFetchResult.Success)
        assertEquals("Bearer follow-token", authorizationHeader)
        assertEquals(1, result.items.size)
        assertEquals("watch-1", result.items.single().sessionId)
        assertEquals("Pilot One", result.items.single().displayLabel)
    }

    @Test
    fun fetchFollowingActivePilots_ioFailure_usesFriendlyTransportMessage() = runTest {
        val dataSource = CurrentApiFollowingActivePilotsDataSource(
            xcAccountRepository = mockXcAccountRepository(
                MutableStateFlow(signedInAccountSnapshot(accessToken = "follow-token"))
            ),
            httpClient = OkHttpClient.Builder().addInterceptor(
                Interceptor { throw UnknownHostException("api.xcpro.com.au") }
            ).build(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = dataSource.fetchFollowingActivePilots()

        require(result is FollowingActivePilotsFetchResult.Failure)
        assertEquals(LiveFollowTransportState.UNAVAILABLE, result.availability.state)
        assertEquals("LiveFollow network error. Check connection and retry.", result.message)
        assertFalse(result.message.contains("hostname", ignoreCase = true))
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
