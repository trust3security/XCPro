package com.example.xcpro.livefollow.data.session

import com.google.gson.JsonParser
import com.example.xcpro.livefollow.account.mockXcAccountRepository
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentityType
import com.example.xcpro.livefollow.model.LiveFollowConfidence
import com.example.xcpro.livefollow.model.LiveFollowTaskPoint
import com.example.xcpro.livefollow.model.LiveFollowTaskSnapshot
import com.example.xcpro.livefollow.model.LiveFollowTransportState
import com.example.xcpro.livefollow.model.LiveFollowValueQuality
import com.example.xcpro.livefollow.model.LiveFollowValueState
import com.example.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.example.xcpro.livefollow.model.LiveOwnshipSourceLabel
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentApiLiveFollowSessionGatewayTest {

    @Test
    fun startPilotSession_mapsResponse_andStoresTransportCredentials() = runTest {
        val interceptor = RecordingInterceptor { request ->
            assertEquals("/api/v1/session/start", request.url.encodedPath)
            testResponse(
                request = request,
                body = """
                    {
                      "session_id": "pilot-1",
                      "share_code": "SHARE123",
                      "status": "active",
                      "write_token": "token-abc"
                    }
                """.trimIndent()
            )
        }
        val gateway = gateway(interceptor)

        val result = gateway.startPilotSession(
            StartPilotLiveFollowSession(pilotIdentity = identityProfile("AB12CD"))
        )

        require(result is LiveFollowSessionGatewayResult.Success)
        assertEquals("pilot-1", result.snapshot.sessionId)
        assertEquals(LiveFollowSessionRole.PILOT, result.snapshot.role)
        assertEquals(LiveFollowSessionLifecycle.ACTIVE, result.snapshot.lifecycle)
        assertEquals("SHARE123", result.snapshot.shareCode)
        assertEquals(1, interceptor.requests.size)
        assertEquals(
            StoredPilotTransport(
                sessionId = "pilot-1",
                shareCode = "SHARE123",
                writeToken = "token-abc",
                lastUploadedFixWallMs = null,
                lastUploadedTaskPayloadJson = null
            ),
            gateway.storedPilotTransportForTests()
        )
    }

    @Test
    fun joinWatchSession_treatsPublicLiveReadAsAuthorized_andKeepsWatchIdentityNull() = runTest {
        val interceptor = RecordingInterceptor { request ->
            testResponse(
                request = request,
                body = """
                    {
                      "session": "watch-1",
                      "share_code": "WATCH123",
                      "status": "active",
                      "created_at": "2026-03-20T10:00:00Z",
                      "last_position_at": null,
                      "ended_at": null,
                      "latest": null,
                      "positions": [],
                      "task": null
                    }
                """.trimIndent()
            )
        }
        val gateway = gateway(interceptor)

        val result = gateway.joinWatchSession("watch-1")

        require(result is LiveFollowSessionGatewayResult.Success)
        assertEquals("watch-1", result.snapshot.sessionId)
        assertNull(result.snapshot.watchIdentity)
        assertTrue(result.snapshot.directWatchAuthorized)
    }

    @Test
    fun joinWatchSessionByShareCode_usesPublicEndpoint_andKeepsWatchIdentityNull() = runTest {
        val interceptor = RecordingInterceptor { request ->
            assertEquals("/api/v1/live/share/WATCH123", request.url.encodedPath)
            testResponse(
                request = request,
                body = """
                    {
                      "session": "watch-1",
                      "share_code": "WATCH123",
                      "status": "active",
                      "created_at": "2026-03-20T10:00:00Z",
                      "last_position_at": null,
                      "ended_at": null,
                      "latest": null,
                      "positions": [],
                      "task": null
                    }
                """.trimIndent()
            )
        }
        val gateway = gateway(interceptor)

        val result = gateway.joinWatchSessionByShareCode("watch123")

        require(result is LiveFollowSessionGatewayResult.Success)
        assertEquals("watch-1", result.snapshot.sessionId)
        assertEquals("WATCH123", result.snapshot.shareCode)
        assertEquals(LiveFollowWatchLookupType.SHARE_CODE, result.snapshot.watchLookup?.type)
        assertNull(result.snapshot.watchIdentity)
        assertTrue(result.snapshot.directWatchAuthorized)
    }

    @Test
    fun stopCurrentSession_postsSessionId_andClearsStoredPilotTransport() = runTest {
        val interceptor = RecordingInterceptor { request ->
            when (request.url.encodedPath) {
                "/api/v1/session/start" -> testResponse(
                    request = request,
                    body = """
                        {
                          "session_id": "pilot-1",
                          "share_code": "SHARE123",
                          "status": "active",
                          "write_token": "token-abc"
                        }
                    """.trimIndent()
                )

                "/api/v1/session/end" -> testResponse(
                    request = request,
                    body = """
                        {
                          "ok": true,
                          "session_id": "pilot-1",
                          "status": "ended",
                          "ended_at": "2026-03-20T10:10:00Z"
                        }
                    """.trimIndent()
                )

                else -> error("Unexpected path ${request.url.encodedPath}")
            }
        }
        val gateway = gateway(interceptor)

        gateway.startPilotSession(
            StartPilotLiveFollowSession(pilotIdentity = identityProfile("AB12CD"))
        )
        val result = gateway.stopCurrentSession("pilot-1")

        require(result is LiveFollowSessionGatewayResult.Success)
        val endRequest = interceptor.requests.last()
        assertEquals("/api/v1/session/end", endRequest.path)
        assertEquals("token-abc", endRequest.headerValue("X-Session-Token"))
        assertEquals(
            "pilot-1",
            JsonParser.parseString(endRequest.body).asJsonObject.get("session_id").asString
        )
        assertEquals(
            StoredPilotTransport(),
            gateway.storedPilotTransportForTests()
        )
        assertEquals(LiveFollowSessionRole.NONE, gateway.sessionState.value.role)
    }

    @Test
    fun startFailure_updatesTransportAvailabilityAndLastError() = runTest {
        val interceptor = RecordingInterceptor { request ->
            testResponse(
                request = request,
                code = 503,
                message = "Service Unavailable",
                body = """{"detail":"backend unavailable"}"""
            )
        }
        val gateway = gateway(interceptor)

        val result = gateway.startPilotSession(
            StartPilotLiveFollowSession(pilotIdentity = identityProfile("AB12CD"))
        )

        assertEquals(
            LiveFollowSessionGatewayResult.Failure("backend unavailable"),
            result
        )
        assertEquals(LiveFollowTransportState.DEGRADED, gateway.sessionState.value.transportAvailability.state)
        assertEquals("backend unavailable", gateway.sessionState.value.lastError)
    }

    @Test
    fun startPilotSession_ioFailure_usesFriendlyTransportMessage() = runTest {
        val interceptor = RecordingInterceptor { throw UnknownHostException("api.xcpro.com.au") }
        val gateway = gateway(interceptor)

        val result = gateway.startPilotSession(
            StartPilotLiveFollowSession(pilotIdentity = identityProfile("AB12CD"))
        )

        assertEquals(
            LiveFollowSessionGatewayResult.Failure(
                "LiveFollow network error. Check connection and retry."
            ),
            result
        )
        assertEquals(LiveFollowTransportState.UNAVAILABLE, gateway.sessionState.value.transportAvailability.state)
        assertEquals(
            "LiveFollow network error. Check connection and retry.",
            gateway.sessionState.value.lastError
        )
        assertFalse(gateway.sessionState.value.lastError.orEmpty().contains("hostname", ignoreCase = true))
    }

    @Test
    fun uploadPilotPosition_skipsWhenRequiredFieldsAreMissing() = runTest {
        val interceptor = RecordingInterceptor { request ->
            testResponse(
                request = request,
                body = """
                    {
                      "session_id": "pilot-1",
                      "share_code": "SHARE123",
                      "status": "active",
                      "write_token": "token-abc"
                    }
                """.trimIndent()
            )
        }
        val gateway = gateway(interceptor)
        gateway.startPilotSession(
            StartPilotLiveFollowSession(pilotIdentity = identityProfile("AB12CD"))
        )

        val result = gateway.uploadPilotPosition(
            ownshipSnapshot(trackDeg = null)
        )

        assertEquals(
            LiveFollowPilotPositionUploadResult.Skipped(
                LiveFollowPilotPositionSkipReason.MISSING_REQUIRED_FIELDS
            ),
            result
        )
        assertEquals(1, interceptor.requests.size)
    }

    @Test
    fun uploadPilotPosition_mapsRequestAgainstCurrentApiContract() = runTest {
        val requestIndex = AtomicInteger(0)
        val interceptor = RecordingInterceptor { request ->
            when (requestIndex.getAndIncrement()) {
                0 -> testResponse(
                    request = request,
                    body = """
                        {
                          "session_id": "pilot-1",
                          "share_code": "SHARE123",
                          "status": "active",
                          "write_token": "token-abc"
                        }
                    """.trimIndent()
                )

                1 -> testResponse(
                    request = request,
                    body = """{"ok":true}"""
                )

                else -> error("Unexpected request index")
            }
        }
        val gateway = gateway(interceptor)
        gateway.startPilotSession(
            StartPilotLiveFollowSession(pilotIdentity = identityProfile("AB12CD"))
        )

        val result = gateway.uploadPilotPosition(
            ownshipSnapshot(
                gpsAltitudeMslMeters = 500.0,
                pressureAltitudeMslMeters = 495.0,
                groundSpeedMs = 12.5,
                trackDeg = 182.0,
                fixWallMs = 1_700_000_123_000L
            )
        )

        assertEquals(LiveFollowPilotPositionUploadResult.Uploaded, result)
        val uploadRequest = interceptor.requests.last()
        assertEquals("/api/v1/position", uploadRequest.path)
        assertEquals("token-abc", uploadRequest.headerValue("X-Session-Token"))
        val json = JsonParser.parseString(uploadRequest.body).asJsonObject
        assertEquals("pilot-1", json.get("session_id").asString)
        assertEquals(-33.9, json.get("lat").asDouble, 0.0)
        assertEquals(151.2, json.get("lon").asDouble, 0.0)
        assertEquals(495.0, json.get("alt").asDouble, 0.0)
        assertEquals(45.0, json.get("agl_meters").asDouble, 0.0)
        assertEquals(12.5, json.get("speed").asDouble, 0.0)
        assertEquals(182.0, json.get("heading").asDouble, 0.0)
        assertEquals("2023-11-14T22:15:23Z", json.get("timestamp").asString)
        assertEquals(1_700_000_123_000L, gateway.storedPilotTransportForTests().lastUploadedFixWallMs)
    }

    @Test
    fun uploadPilotTask_mapsRequestAgainstCurrentApiContract_andDedupesUnchangedPayload() = runTest {
        val requestIndex = AtomicInteger(0)
        val interceptor = RecordingInterceptor { request ->
            when (requestIndex.getAndIncrement()) {
                0 -> testResponse(
                    request = request,
                    body = """
                        {
                          "session_id": "pilot-1",
                          "share_code": "SHARE123",
                          "status": "active",
                          "write_token": "token-abc"
                        }
                    """.trimIndent()
                )

                1 -> testResponse(
                    request = request,
                    body = """{"ok":true,"task_id":"task-1","revision":1}"""
                )

                else -> error("Unexpected request index")
            }
        }
        val gateway = gateway(interceptor)
        gateway.startPilotSession(
            StartPilotLiveFollowSession(pilotIdentity = identityProfile("AB12CD"))
        )

        val uploadResult = gateway.uploadPilotTask(sampleTaskSnapshot())
        val duplicateResult = gateway.uploadPilotTask(sampleTaskSnapshot())

        assertEquals(LiveFollowPilotTaskUploadResult.Uploaded, uploadResult)
        assertEquals(
            LiveFollowPilotTaskUploadResult.Skipped(LiveFollowPilotTaskSkipReason.UNCHANGED_TASK),
            duplicateResult
        )
        val taskRequest = interceptor.requests.last()
        assertEquals("/api/v1/task/upsert", taskRequest.path)
        assertEquals("token-abc", taskRequest.headerValue("X-Session-Token"))
        val json = JsonParser.parseString(taskRequest.body).asJsonObject
        assertEquals("pilot-1", json.get("session_id").asString)
        assertEquals("task-alpha", json.get("task_name").asString)
        val taskJson = json.getAsJsonObject("task")
        val turnpoints = taskJson.getAsJsonArray("turnpoints")
        assertEquals(3, turnpoints.size())
        assertEquals("Start", turnpoints[0].asJsonObject.get("name").asString)
        assertEquals("START_LINE", turnpoints[0].asJsonObject.get("type").asString)
        assertEquals(10_000.0, turnpoints[0].asJsonObject.get("radius_m").asDouble, 0.0)
        assertEquals("TURN_POINT_CYLINDER", turnpoints[1].asJsonObject.get("type").asString)
        assertEquals(500.0, turnpoints[1].asJsonObject.get("radius_m").asDouble, 0.0)
        assertEquals("FINISH_CYLINDER", turnpoints[2].asJsonObject.get("type").asString)
        assertEquals(3_000.0, turnpoints[2].asJsonObject.get("radius_m").asDouble, 0.0)
        assertEquals("START_LINE", taskJson.getAsJsonObject("start").get("type").asString)
        assertEquals(10_000.0, taskJson.getAsJsonObject("start").get("radius_m").asDouble, 0.0)
        assertEquals("FINISH_CYLINDER", taskJson.getAsJsonObject("finish").get("type").asString)
        assertEquals(3_000.0, taskJson.getAsJsonObject("finish").get("radius_m").asDouble, 0.0)
        assertEquals(taskRequest.body, gateway.storedPilotTransportForTests().lastUploadedTaskPayloadJson)
    }

    @Test
    fun uploadPilotTask_nullSnapshot_sendsExplicitClearPayload_andDedupesRepeatedClear() = runTest {
        val requestIndex = AtomicInteger(0)
        val interceptor = RecordingInterceptor { request ->
            when (requestIndex.getAndIncrement()) {
                0 -> testResponse(
                    request = request,
                    body = """
                        {
                          "session_id": "pilot-1",
                          "share_code": "SHARE123",
                          "status": "active",
                          "write_token": "token-abc"
                        }
                    """.trimIndent()
                )

                1 -> testResponse(
                    request = request,
                    body = """{"ok":true,"task_id":"task-1","revision":2,"cleared":true}"""
                )

                else -> error("Unexpected request index")
            }
        }
        val gateway = gateway(interceptor)
        gateway.startPilotSession(
            StartPilotLiveFollowSession(pilotIdentity = identityProfile("AB12CD"))
        )

        val uploadResult = gateway.uploadPilotTask(null)
        val duplicateResult = gateway.uploadPilotTask(null)

        assertEquals(LiveFollowPilotTaskUploadResult.Uploaded, uploadResult)
        assertEquals(
            LiveFollowPilotTaskUploadResult.Skipped(LiveFollowPilotTaskSkipReason.UNCHANGED_TASK),
            duplicateResult
        )
        val taskRequest = interceptor.requests.last()
        assertEquals("/api/v1/task/upsert", taskRequest.path)
        assertEquals("token-abc", taskRequest.headerValue("X-Session-Token"))
        val json = JsonParser.parseString(taskRequest.body).asJsonObject
        assertEquals("pilot-1", json.get("session_id").asString)
        assertEquals(true, json.get("clear_task").asBoolean)
        assertEquals(taskRequest.body, gateway.storedPilotTransportForTests().lastUploadedTaskPayloadJson)
    }

    private fun gateway(interceptor: RecordingInterceptor): CurrentApiLiveFollowSessionGateway {
        return CurrentApiLiveFollowSessionGateway(
            httpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            xcAccountRepository = mockXcAccountRepository(),
            ioDispatcher = UnconfinedTestDispatcher()
        )
    }

    private fun identityProfile(hex: String) = com.example.xcpro.livefollow.model.LiveFollowIdentityProfile(
        canonicalIdentity = LiveFollowAircraftIdentity.create(
            type = LiveFollowAircraftIdentityType.FLARM,
            rawValue = hex,
            verified = true
        )
    )

    private fun ownshipSnapshot(
        gpsAltitudeMslMeters: Double? = 500.0,
        pressureAltitudeMslMeters: Double? = 495.0,
        aglMeters: Double? = 45.0,
        groundSpeedMs: Double? = 12.0,
        trackDeg: Double? = 180.0,
        fixWallMs: Long? = 20_000L
    ): LiveOwnshipSnapshot {
        return LiveOwnshipSnapshot(
            latitudeDeg = -33.9,
            longitudeDeg = 151.2,
            gpsAltitudeMslMeters = gpsAltitudeMslMeters,
            pressureAltitudeMslMeters = pressureAltitudeMslMeters,
            aglMeters = aglMeters,
            groundSpeedMs = groundSpeedMs,
            trackDeg = trackDeg,
            verticalSpeedMs = 1.2,
            fixMonoMs = 10_000L,
            fixWallMs = fixWallMs,
            positionQuality = LiveFollowValueQuality(
                state = LiveFollowValueState.VALID,
                confidence = LiveFollowConfidence.HIGH
            ),
            verticalQuality = LiveFollowValueQuality(
                state = LiveFollowValueState.VALID,
                confidence = LiveFollowConfidence.HIGH
            ),
            canonicalIdentity = LiveFollowAircraftIdentity.create(
                type = LiveFollowAircraftIdentityType.FLARM,
                rawValue = "AB12CD",
                verified = true
            ),
            sourceLabel = LiveOwnshipSourceLabel.LIVE_FLIGHT_RUNTIME
        )
    }

    private fun sampleTaskSnapshot(): LiveFollowTaskSnapshot {
        return LiveFollowTaskSnapshot(
            taskName = "task-alpha",
            points = listOf(
                LiveFollowTaskPoint(
                    order = 0,
                    latitudeDeg = -33.9,
                    longitudeDeg = 151.2,
                    radiusMeters = 10_000.0,
                    name = "Start",
                    type = "START_LINE"
                ),
                LiveFollowTaskPoint(
                    order = 1,
                    latitudeDeg = -33.8,
                    longitudeDeg = 151.3,
                    radiusMeters = 500.0,
                    name = "TP1",
                    type = "TURN_POINT_CYLINDER"
                ),
                LiveFollowTaskPoint(
                    order = 2,
                    latitudeDeg = -33.7,
                    longitudeDeg = 151.4,
                    radiusMeters = 3_000.0,
                    name = "Finish",
                    type = "FINISH_CYLINDER"
                )
            )
        )
    }
}
