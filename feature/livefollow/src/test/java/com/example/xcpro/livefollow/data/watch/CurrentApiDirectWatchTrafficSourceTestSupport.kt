package com.example.xcpro.livefollow.data.watch

import com.example.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.example.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.example.xcpro.livefollow.data.session.LiveFollowWatchLookup
import com.example.xcpro.livefollow.model.liveFollowAvailableTransport
import com.example.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.example.xcpro.livefollow.state.LiveFollowRuntimeMode
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.repoScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

internal fun activeWatchSession(
    sessionId: String = "watch-1",
    shareCode: String? = null,
    watchLookup: LiveFollowWatchLookup? = null,
    runtimeMode: LiveFollowRuntimeMode = LiveFollowRuntimeMode.LIVE,
    sideEffectsAllowed: Boolean = true,
    replayBlockReason: LiveFollowReplayBlockReason = LiveFollowReplayBlockReason.NONE
): LiveFollowSessionSnapshot {
    return LiveFollowSessionSnapshot(
        sessionId = sessionId,
        role = LiveFollowSessionRole.WATCHER,
        lifecycle = LiveFollowSessionLifecycle.ACTIVE,
        runtimeMode = runtimeMode,
        watchIdentity = null,
        directWatchAuthorized = true,
        transportAvailability = liveFollowAvailableTransport(),
        sideEffectsAllowed = sideEffectsAllowed,
        replayBlockReason = replayBlockReason,
        lastError = null,
        shareCode = shareCode,
        watchLookup = watchLookup
    )
}

internal fun sampleLivePayload(): String {
    return """
        {
          "session": "watch-1",
          "share_code": "WATCH123",
          "status": "active",
          "created_at": "2026-03-20T10:00:00Z",
          "last_position_at": "1970-01-01T00:16:39Z",
          "ended_at": null,
          "latest": {
            "lat": -33.91,
            "lon": 151.21,
            "alt": 510.0,
            "agl_meters": 45.0,
            "speed": 13.0,
            "heading": 185.0,
            "timestamp": "1970-01-01T00:16:39Z"
          },
          "positions": [],
          "task": {
            "task_id": "task-1",
            "current_revision": 1,
            "updated_at": "2026-03-20T10:05:00Z",
            "payload": {
              "task_name": "spectator-task",
              "task": {
                "turnpoints": [
                  {
                    "name": "Start",
                    "type": "START_LINE",
                    "lat": -33.91,
                    "lon": 151.21,
                    "radius_m": 10000.0
                  },
                  {
                    "name": "TP1",
                    "type": "TURN_POINT_CYLINDER",
                    "lat": -33.85,
                    "lon": 151.26,
                    "radius_m": 500.0
                  },
                  {
                    "name": "Finish",
                    "type": "FINISH_CYLINDER",
                    "lat": -33.80,
                    "lon": 151.31
                  }
                ],
                "start": {
                  "type": "START_LINE",
                  "radius_m": 10000.0
                },
                "finish": {
                  "type": "FINISH_CYLINDER",
                  "radius_m": 3000.0
                }
              }
            }
          }
        }
    """.trimIndent()
}

internal fun sampleLivePayloadWithoutTask(): String {
    return """
        {
          "session": "watch-1",
          "share_code": "WATCH123",
          "status": "active",
          "created_at": "2026-03-20T10:00:00Z",
          "last_position_at": "1970-01-01T00:16:39Z",
          "ended_at": null,
          "latest": {
            "lat": -33.91,
            "lon": 151.21,
            "alt": 510.0,
            "agl_meters": 45.0,
            "speed": 13.0,
            "heading": 185.0,
            "timestamp": "1970-01-01T00:16:39Z"
          },
          "positions": [],
          "task": null
        }
    """.trimIndent()
}

internal fun clientForBody(
    body: String,
    requestCount: AtomicInteger? = null
): OkHttpClient {
    return OkHttpClient.Builder().addInterceptor(
        Interceptor { chain ->
            requestCount?.incrementAndGet()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    ).build()
}
