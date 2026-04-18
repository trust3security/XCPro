package com.trust3.xcpro.livefollow.data.session

import com.trust3.xcpro.livefollow.data.ownship.LiveOwnshipSnapshotSource
import com.trust3.xcpro.livefollow.data.task.LiveFollowTaskSnapshotSource
import com.trust3.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.trust3.xcpro.livefollow.model.LiveFollowAircraftIdentityType
import com.trust3.xcpro.livefollow.model.LiveFollowConfidence
import com.trust3.xcpro.livefollow.model.LiveFollowIdentityProfile
import com.trust3.xcpro.livefollow.model.LiveFollowTaskPoint
import com.trust3.xcpro.livefollow.model.LiveFollowTaskSnapshot
import com.trust3.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.trust3.xcpro.livefollow.model.LiveOwnshipSourceLabel
import com.trust3.xcpro.livefollow.model.LiveFollowValueQuality
import com.trust3.xcpro.livefollow.model.LiveFollowValueState
import com.trust3.xcpro.livefollow.state.LiveFollowRuntimeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.liveFollowSessionRepositoryScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

internal fun identityProfile(hex: String): LiveFollowIdentityProfile = LiveFollowIdentityProfile(
    canonicalIdentity = LiveFollowAircraftIdentity.create(
        type = LiveFollowAircraftIdentityType.FLARM,
        rawValue = hex,
        verified = true
    )
)

internal class FakeOwnshipSnapshotSource(
    override val snapshot: StateFlow<LiveOwnshipSnapshot?> = MutableStateFlow(null),
    override val runtimeMode: MutableStateFlow<LiveFollowRuntimeMode> =
        MutableStateFlow(LiveFollowRuntimeMode.LIVE)
) : LiveOwnshipSnapshotSource

internal class FakeTaskSnapshotSource(
    override val taskSnapshot: StateFlow<LiveFollowTaskSnapshot?> = MutableStateFlow(null)
) : LiveFollowTaskSnapshotSource

internal class FakeSessionGateway(
    initialSnapshot: LiveFollowSessionGatewaySnapshot = liveFollowGatewayIdleSnapshot(),
    var startResult: LiveFollowSessionGatewayResult = LiveFollowSessionGatewayResult.Success(
        liveFollowGatewayIdleSnapshot()
    ),
    var updateVisibilityResult: LiveFollowSessionGatewayResult = LiveFollowSessionGatewayResult.Success(
        liveFollowGatewayIdleSnapshot()
    ),
    var stopResult: LiveFollowSessionGatewayResult = LiveFollowSessionGatewayResult.Success(
        liveFollowGatewayIdleSnapshot()
    ),
    var joinResult: LiveFollowSessionGatewayResult = LiveFollowSessionGatewayResult.Success(
        liveFollowGatewayIdleSnapshot()
    ),
    var joinAuthenticatedResult: LiveFollowSessionGatewayResult = LiveFollowSessionGatewayResult.Success(
        liveFollowGatewayIdleSnapshot()
    ),
    var joinByShareCodeResult: LiveFollowSessionGatewayResult = LiveFollowSessionGatewayResult.Success(
        liveFollowGatewayIdleSnapshot()
    ),
    var leaveResult: LiveFollowSessionGatewayResult = LiveFollowSessionGatewayResult.Success(
        liveFollowGatewayIdleSnapshot()
    )
) : LiveFollowSessionGateway {
    override val sessionState = MutableStateFlow(initialSnapshot)
    var startCalls: Int = 0
    var updateVisibilityCalls: Int = 0
    var stopCalls: Int = 0
    var joinCalls: Int = 0
    var joinAuthenticatedCalls: Int = 0
    var joinByShareCodeCalls: Int = 0
    var leaveCalls: Int = 0
    var uploadCalls: Int = 0
    var taskUploadCalls: Int = 0
    val taskUploadSnapshots = mutableListOf<LiveFollowTaskSnapshot?>()

    override suspend fun startPilotSession(
        request: StartPilotLiveFollowSession
    ): LiveFollowSessionGatewayResult {
        startCalls += 1
        return startResult.also(::applyResult)
    }

    override suspend fun updatePilotVisibility(
        sessionId: String,
        visibility: LiveFollowSessionVisibility
    ): LiveFollowSessionGatewayResult {
        updateVisibilityCalls += 1
        return updateVisibilityResult.also(::applyResult)
    }

    override suspend fun stopCurrentSession(sessionId: String): LiveFollowSessionGatewayResult {
        stopCalls += 1
        return stopResult.also(::applyResult)
    }

    override suspend fun joinWatchSession(sessionId: String): LiveFollowSessionGatewayResult {
        joinCalls += 1
        return joinResult.also(::applyResult)
    }

    override suspend fun joinAuthenticatedWatchSession(
        sessionId: String
    ): LiveFollowSessionGatewayResult {
        joinAuthenticatedCalls += 1
        return joinAuthenticatedResult.also(::applyResult)
    }

    override suspend fun joinWatchSessionByShareCode(
        shareCode: String
    ): LiveFollowSessionGatewayResult {
        joinByShareCodeCalls += 1
        return joinByShareCodeResult.also(::applyResult)
    }

    override suspend fun leaveSession(sessionId: String): LiveFollowSessionGatewayResult {
        leaveCalls += 1
        return leaveResult.also(::applyResult)
    }

    override suspend fun uploadPilotPosition(
        snapshot: LiveOwnshipSnapshot
    ): LiveFollowPilotPositionUploadResult {
        uploadCalls += 1
        return LiveFollowPilotPositionUploadResult.Uploaded
    }

    override suspend fun uploadPilotTask(
        snapshot: LiveFollowTaskSnapshot?
    ): LiveFollowPilotTaskUploadResult {
        taskUploadCalls += 1
        taskUploadSnapshots += snapshot
        return LiveFollowPilotTaskUploadResult.Uploaded
    }

    private fun applyResult(result: LiveFollowSessionGatewayResult) {
        if (result is LiveFollowSessionGatewayResult.Success) {
            sessionState.value = result.snapshot
        }
    }
}

internal fun sampleOwnshipSnapshot(): LiveOwnshipSnapshot = LiveOwnshipSnapshot(
    latitudeDeg = -33.9,
    longitudeDeg = 151.2,
    gpsAltitudeMslMeters = 500.0,
    pressureAltitudeMslMeters = 495.0,
    groundSpeedMs = 12.0,
    trackDeg = 180.0,
    verticalSpeedMs = 1.2,
    fixMonoMs = 10_000L,
    fixWallMs = 20_000L,
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

internal fun sampleTaskSnapshot(): LiveFollowTaskSnapshot = LiveFollowTaskSnapshot(
    taskName = "task-alpha",
    points = listOf(
        LiveFollowTaskPoint(0, -33.9, 151.2, 10_000.0, "Start", "START_LINE"),
        LiveFollowTaskPoint(1, -33.8, 151.3, 500.0, "TP1", "TURN_POINT_CYLINDER")
    )
)
