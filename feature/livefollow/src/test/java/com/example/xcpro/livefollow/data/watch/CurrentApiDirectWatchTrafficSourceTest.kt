package com.example.xcpro.livefollow.data.watch

import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.livefollow.account.mockXcAccountRepository
import com.example.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.example.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.example.xcpro.livefollow.data.session.LiveFollowWatchLookup
import com.example.xcpro.livefollow.data.session.liveFollowShareCodeLookup
import com.example.xcpro.livefollow.model.LiveFollowTransportState
import com.example.xcpro.livefollow.model.liveFollowAvailableTransport
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import com.example.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.example.xcpro.livefollow.state.LiveFollowRuntimeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
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
class CurrentApiDirectWatchTrafficSourceTest {

    @Test
    fun poll_doesNotStartWhenReplayBlocksSideEffects() = runTest {
        val scope = repoScope()
        try {
            val requestCount = AtomicInteger(0)
            val sessionState = MutableStateFlow(
                activeWatchSession(
                    runtimeMode = LiveFollowRuntimeMode.REPLAY,
                    sideEffectsAllowed = false,
                    replayBlockReason = LiveFollowReplayBlockReason.REPLAY_MODE
                )
            )
            val source = CurrentApiDirectWatchTrafficSource(
                scope = scope,
                clock = FakeClock(monoMs = 20_000L, wallMs = 1_000_000L),
                sessionState = sessionState,
                xcAccountRepository = mockXcAccountRepository(),
                httpClient = clientForBody(sampleLivePayload(), requestCount),
                ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                pollIntervalMs = 60_000L
            )
            runCurrent()

            assertEquals(0, requestCount.get())
            assertNull(source.aircraft.value)
            assertEquals(
                LiveFollowTransportState.AVAILABLE,
                source.transportAvailability.value.state
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun poll_stopsWhenReplayBecomesActiveAfterWatchSessionStarts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val requestCount = AtomicInteger(0)
            val sessionState = MutableStateFlow(activeWatchSession())
            val source = CurrentApiDirectWatchTrafficSource(
                scope = scope,
                clock = FakeClock(monoMs = 20_000L, wallMs = 1_000_000L),
                sessionState = sessionState,
                xcAccountRepository = mockXcAccountRepository(),
                httpClient = clientForBody(sampleLivePayload(), requestCount),
                ioDispatcher = dispatcher,
                pollIntervalMs = 1_000L
            )
            runCurrent()
            assertEquals(1, requestCount.get())
            requireNotNull(source.aircraft.value)

            sessionState.value = activeWatchSession(
                runtimeMode = LiveFollowRuntimeMode.REPLAY,
                sideEffectsAllowed = false,
                replayBlockReason = LiveFollowReplayBlockReason.REPLAY_MODE
            )
            runCurrent()

            assertNull(source.aircraft.value)
            advanceTimeBy(5_000L)
            runCurrent()
            assertEquals(1, requestCount.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun poll_mapsLivePayload_intoDirectWatchSample() = runTest {
        val scope = repoScope()
        try {
            val clock = FakeClock(monoMs = 20_000L, wallMs = 1_000_000L)
            val sessionState = MutableStateFlow(activeWatchSession())
            val source = CurrentApiDirectWatchTrafficSource(
                scope = scope,
                clock = clock,
                sessionState = sessionState,
                xcAccountRepository = mockXcAccountRepository(),
                httpClient = clientForBody(sampleLivePayload()),
                ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                pollIntervalMs = 60_000L
            )
            runCurrent()

            val sample = source.aircraft.value
            requireNotNull(sample)
            assertEquals(-33.91, sample.latitudeDeg, 0.0)
            assertEquals(151.21, sample.longitudeDeg, 0.0)
            assertEquals(510.0, requireNotNull(sample.altitudeMslMeters), 0.0)
            assertEquals(45.0, requireNotNull(sample.aglMeters), 0.0)
            assertEquals(13.0, requireNotNull(sample.groundSpeedMs), 0.0)
            assertEquals(185.0, requireNotNull(sample.trackDeg), 0.0)
            assertEquals(19_000L, sample.fixMonoMs)
            assertEquals(999_000L, requireNotNull(sample.fixWallMs))
            assertNull(sample.canonicalIdentity)
            assertNull(sample.verticalSpeedMs)
            assertEquals("WATCH123", sample.displayLabel)
            val task = source.task.value
            requireNotNull(task)
            assertEquals("spectator-task", task.taskName)
            assertEquals(3, task.points.size)
            assertEquals(10_000.0, requireNotNull(task.points.first().radiusMeters), 0.0)
            assertEquals(3_000.0, requireNotNull(task.points.last().radiusMeters), 0.0)
            assertEquals(
                LiveFollowTransportState.AVAILABLE,
                source.transportAvailability.value.state
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun poll_shareCodeWatch_usesPublicShareEndpoint_andMapsPayload() = runTest {
        val scope = repoScope()
        try {
            val clock = FakeClock(monoMs = 20_000L, wallMs = 1_000_000L)
            val sessionState = MutableStateFlow(
                activeWatchSession(
                    sessionId = "watch-1",
                    shareCode = "WATCH123",
                    watchLookup = liveFollowShareCodeLookup("WATCH123")
                )
            )
            val requestedPath = AtomicReference<String>()
            val source = CurrentApiDirectWatchTrafficSource(
                scope = scope,
                clock = clock,
                sessionState = sessionState,
                xcAccountRepository = mockXcAccountRepository(),
                httpClient = OkHttpClient.Builder().addInterceptor(
                    Interceptor { chain ->
                        requestedPath.set(chain.request().url.encodedPath)
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(sampleLivePayload().toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                ).build(),
                ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                pollIntervalMs = 60_000L
            )
            runCurrent()

            val sample = source.aircraft.value
            requireNotNull(sample)
            assertEquals("/api/v1/live/share/WATCH123", requestedPath.get())
            assertEquals("WATCH123", sample.displayLabel)
            assertEquals("spectator-task", source.task.value?.taskName)
            assertNull(sample.canonicalIdentity)
            assertNull(sample.verticalSpeedMs)
            assertEquals(
                LiveFollowTransportState.AVAILABLE,
                source.transportAvailability.value.state
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun poll_usesLastPositionWhenLatestIsNull() = runTest {
        val scope = repoScope()
        try {
            val clock = FakeClock(monoMs = 30_000L, wallMs = 2_000_000L)
            val sessionState = MutableStateFlow(activeWatchSession())
            val source = CurrentApiDirectWatchTrafficSource(
                scope = scope,
                clock = clock,
                sessionState = sessionState,
                xcAccountRepository = mockXcAccountRepository(),
                httpClient = clientForBody(
                    """
                        {
                          "session": "watch-1",
                          "share_code": "WATCH123",
                          "status": "active",
                          "created_at": "2026-03-20T10:00:00Z",
                          "last_position_at": "1970-01-01T00:33:18Z",
                          "ended_at": null,
                          "latest": null,
                          "positions": [
                            {
                              "lat": -33.90,
                              "lon": 151.20,
                              "alt": 500.0,
                              "agl_meters": 40.0,
                              "speed": 12.0,
                              "heading": 180.0,
                              "timestamp": "1970-01-01T00:33:15Z"
                            },
                            {
                              "lat": -33.92,
                              "lon": 151.22,
                              "alt": 515.0,
                              "agl_meters": 52.0,
                              "speed": 14.0,
                              "heading": 188.0,
                              "timestamp": "1970-01-01T00:33:18Z"
                            }
                          ],
                          "task": null
                        }
                    """.trimIndent()
                ),
                ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                pollIntervalMs = 60_000L
            )
            runCurrent()

            val sample = source.aircraft.value
            requireNotNull(sample)
            assertEquals(-33.92, sample.latitudeDeg, 0.0)
            assertEquals(151.22, sample.longitudeDeg, 0.0)
            assertEquals(515.0, requireNotNull(sample.altitudeMslMeters), 0.0)
            assertEquals(52.0, requireNotNull(sample.aglMeters), 0.0)
            assertEquals(14.0, requireNotNull(sample.groundSpeedMs), 0.0)
            assertEquals(188.0, requireNotNull(sample.trackDeg), 0.0)
            assertEquals(1_998_000L, requireNotNull(sample.fixWallMs))
            assertEquals(28_000L, sample.fixMonoMs)
            assertNull(source.task.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun poll_whenServerClearsTask_replacesPreviousWatchedTaskWithNull() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val clock = FakeClock(monoMs = 20_000L, wallMs = 1_000_000L)
            val sessionState = MutableStateFlow(activeWatchSession())
            val requestIndex = AtomicInteger(0)
            val source = CurrentApiDirectWatchTrafficSource(
                scope = scope,
                clock = clock,
                sessionState = sessionState,
                xcAccountRepository = mockXcAccountRepository(),
                httpClient = OkHttpClient.Builder().addInterceptor(
                    Interceptor { chain ->
                        val body = when (requestIndex.getAndIncrement()) {
                            0 -> sampleLivePayload()
                            1 -> sampleLivePayloadWithoutTask()
                            else -> error("Unexpected request index")
                        }
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                ).build(),
                ioDispatcher = dispatcher,
                pollIntervalMs = 1_000L
            )
            runCurrent()

            requireNotNull(source.task.value)

            advanceTimeBy(1_000L)
            runCurrent()

            assertNull(source.task.value)
            requireNotNull(source.aircraft.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun poll_httpFailure_marksDirectTransportUnavailable() = runTest {
        val scope = repoScope()
        try {
            val clock = FakeClock(monoMs = 20_000L, wallMs = 1_000_000L)
            val sessionState = MutableStateFlow(activeWatchSession())
            val source = CurrentApiDirectWatchTrafficSource(
                scope = scope,
                clock = clock,
                sessionState = sessionState,
                xcAccountRepository = mockXcAccountRepository(),
                httpClient = OkHttpClient.Builder().addInterceptor(
                    Interceptor { chain ->
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(503)
                            .message("Service Unavailable")
                            .body("""{"detail":"poll unavailable"}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                ).build(),
                ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                pollIntervalMs = 60_000L
            )
            runCurrent()

            assertEquals(
                LiveFollowTransportState.DEGRADED,
                source.transportAvailability.value.state
            )
            assertTrue(source.transportAvailability.value.message?.contains("poll unavailable") == true)
        } finally {
            scope.cancel()
        }
    }

    private fun TestScope.repoScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

    private fun activeWatchSession(
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

    private fun sampleLivePayload(): String {
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

    private fun sampleLivePayloadWithoutTask(): String {
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

    private fun clientForBody(
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
}
