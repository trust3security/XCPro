package com.example.xcpro.livefollow.data.friends

import com.example.xcpro.livefollow.model.LiveFollowTransportState
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentApiActivePilotsDataSourceTest {

    @Test
    fun fetchActivePilots_usesActiveEndpoint_andMapsPayload() = runTest {
        val requestedPath = AtomicReference<String>()
        val dataSource = CurrentApiActivePilotsDataSource(
            httpClient = OkHttpClient.Builder().addInterceptor(
                Interceptor { chain ->
                    requestedPath.set(chain.request().url.encodedPath)
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(samplePayload().toResponseBody(JSON_MEDIA_TYPE))
                        .build()
                }
            ).build(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = dataSource.fetchActivePilots()

        require(result is ActivePilotsFetchResult.Success)
        assertEquals("/api/v1/live/active", requestedPath.get())
        assertEquals(2, result.items.size)

        val first = result.items[0]
        assertEquals("WATCH123", first.shareCode)
        assertEquals("Pilot One", first.displayLabel)
        assertEquals("active", first.status)
        assertEquals("watch-1", first.sessionId)
        assertEquals(1_710_980_405_000L, first.lastPositionWallMs)
        assertEquals(510.0, requireNotNull(requireNotNull(first.latest).altitudeMslMeters), 0.0)

        val second = result.items[1]
        assertEquals("Z4WAA57N", second.shareCode)
        assertEquals("Z4WAA57N", second.displayLabel)
        assertEquals("stale", second.status)
        assertNull(second.latest)
    }

    @Test
    fun fetchActivePilots_httpFailure_marksTransportDegraded() = runTest {
        val dataSource = CurrentApiActivePilotsDataSource(
            httpClient = OkHttpClient.Builder().addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Service Unavailable")
                        .body("""{"detail":"active list unavailable"}""".toResponseBody(JSON_MEDIA_TYPE))
                        .build()
                }
            ).build(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = dataSource.fetchActivePilots()

        require(result is ActivePilotsFetchResult.Failure)
        assertEquals(LiveFollowTransportState.DEGRADED, result.availability.state)
        assertTrue(result.message.contains("active list unavailable"))
    }

    private fun samplePayload(): String {
        return """
            {
              "items": [
                {
                  "session": "watch-1",
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
                },
                {
                  "session": "watch-2",
                  "share_code": "z4waa57n",
                  "status": "stale",
                  "display_label": "",
                  "last_position_at": "2024-03-21T00:18:05Z",
                  "latest": null
                }
              ],
              "generated_at": "2024-03-21T00:20:10Z"
            }
        """.trimIndent()
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
