package com.example.xcpro.livefollow.data.watch

import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.example.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.example.xcpro.livefollow.model.LiveFollowTransportState
import com.example.xcpro.livefollow.model.liveFollowAvailableTransport
import java.util.concurrent.atomic.AtomicInteger
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
            assertEquals(13.0, requireNotNull(sample.groundSpeedMs), 0.0)
            assertEquals(185.0, requireNotNull(sample.trackDeg), 0.0)
            assertEquals(19_000L, sample.fixMonoMs)
            assertEquals(999_000L, requireNotNull(sample.fixWallMs))
            assertNull(sample.canonicalIdentity)
            assertNull(sample.verticalSpeedMs)
            assertEquals("WATCH123", sample.displayLabel)
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
                              "speed": 12.0,
                              "heading": 180.0,
                              "timestamp": "1970-01-01T00:33:15Z"
                            },
                            {
                              "lat": -33.92,
                              "lon": 151.22,
                              "alt": 515.0,
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
            assertEquals(14.0, requireNotNull(sample.groundSpeedMs), 0.0)
            assertEquals(188.0, requireNotNull(sample.trackDeg), 0.0)
            assertEquals(1_998_000L, requireNotNull(sample.fixWallMs))
            assertEquals(28_000L, sample.fixMonoMs)
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
        runtimeMode: LiveFollowRuntimeMode = LiveFollowRuntimeMode.LIVE,
        sideEffectsAllowed: Boolean = true,
        replayBlockReason: LiveFollowReplayBlockReason = LiveFollowReplayBlockReason.NONE
    ): LiveFollowSessionSnapshot {
        return LiveFollowSessionSnapshot(
            sessionId = "watch-1",
            role = LiveFollowSessionRole.WATCHER,
            lifecycle = LiveFollowSessionLifecycle.ACTIVE,
            runtimeMode = runtimeMode,
            watchIdentity = null,
            directWatchAuthorized = true,
            transportAvailability = liveFollowAvailableTransport(),
            sideEffectsAllowed = sideEffectsAllowed,
            replayBlockReason = replayBlockReason,
            lastError = null
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
                  "task_name": "Ignored task"
                }
              }
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
