package com.example.xcpro.livefollow.data.watch

import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.example.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentityType
import com.example.xcpro.livefollow.model.LiveFollowConfidence
import com.example.xcpro.livefollow.model.LiveFollowIdentityAmbiguityReason
import com.example.xcpro.livefollow.model.LiveFollowIdentityProfile
import com.example.xcpro.livefollow.model.LiveFollowSourceEligibility
import com.example.xcpro.livefollow.model.LiveFollowSourceState
import com.example.xcpro.livefollow.model.LiveFollowSourceType
import com.example.xcpro.livefollow.model.liveFollowAvailableTransport
import com.example.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.example.xcpro.livefollow.state.LiveFollowRuntimeMode
import com.example.xcpro.livefollow.state.LiveFollowSessionState
import com.example.xcpro.ogn.OgnAddressType
import com.example.xcpro.ogn.OgnConnectionState
import com.example.xcpro.ogn.OgnTrafficIdentity
import com.example.xcpro.ogn.OgnTrafficRepository
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.ogn.OgnTrafficTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchTrafficRepositoryTest {

    @Test
    fun state_prefersOgnWhenTypedIdentityMatchesAndIsFresh() = runTest {
        val scope = repoScope()
        try {
        val clock = FakeClock(monoMs = 10_000L, wallMs = 0L)
        val sessionState = MutableStateFlow(
            watchSessionSnapshot(
                watchIdentity = canonicalProfile("AB12CD"),
                directWatchAuthorized = true
            )
        )
        val ognRepository = FakeOgnTrafficRepository()
        ognRepository.targets.value = listOf(
            ognTarget(
                addressHex = "AB12CD",
                addressType = OgnAddressType.FLARM,
                lastSeenMillis = 9_900L
            )
        )
        val directSource = FakeDirectWatchTrafficSource()
        val repository = WatchTrafficRepository(
            scope = scope,
            clock = clock,
            sessionState = sessionState,
            ognTrafficRepository = ognRepository,
            directWatchTrafficSource = directSource
        )
        runCurrent()

        val snapshot = repository.state.value
        assertEquals(LiveFollowSessionState.LIVE_OGN, snapshot.sourceState)
        assertEquals(LiveFollowSourceType.OGN, snapshot.activeSource)
        assertEquals(LiveFollowSourceEligibility.SELECTABLE, snapshot.ognEligibility)
        assertEquals(LiveFollowSourceEligibility.UNAVAILABLE, snapshot.directEligibility)
        assertEquals("FLARM:AB12CD", snapshot.aircraft?.canonicalIdentity?.canonicalKey)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun state_prefersDirectWhenOgnIsUnresolvedButDirectIsAuthorized() = runTest {
        val scope = repoScope()
        try {
        val clock = FakeClock(monoMs = 11_000L, wallMs = 0L)
        val sessionState = MutableStateFlow(
            watchSessionSnapshot(
                watchIdentity = canonicalProfile("AAAAAA"),
                directWatchAuthorized = true
            )
        )
        val ognRepository = FakeOgnTrafficRepository()
        ognRepository.targets.value = listOf(
            ognTarget(
                addressHex = "BBBBBB",
                addressType = OgnAddressType.FLARM,
                lastSeenMillis = 10_900L
            )
        )
        val directSource = FakeDirectWatchTrafficSource()
        directSource.aircraft.value = directAircraftSample(fixMonoMs = 10_950L)
        val repository = WatchTrafficRepository(
            scope = scope,
            clock = clock,
            sessionState = sessionState,
            ognTrafficRepository = ognRepository,
            directWatchTrafficSource = directSource
        )
        runCurrent()

        val snapshot = repository.state.value
        assertEquals(LiveFollowSessionState.LIVE_DIRECT, snapshot.sourceState)
        assertEquals(LiveFollowSourceType.DIRECT, snapshot.activeSource)
        assertEquals(LiveFollowSourceEligibility.UNRESOLVED_IDENTITY, snapshot.ognEligibility)
        assertEquals(LiveFollowSourceEligibility.SELECTABLE, snapshot.directEligibility)
        assertEquals("DIRECT-1", snapshot.aircraft?.displayLabel)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun state_marksAmbiguousWhenMultipleTypedOgnCandidatesMatch() = runTest {
        val scope = repoScope()
        try {
        val clock = FakeClock(monoMs = 15_000L, wallMs = 0L)
        val sessionState = MutableStateFlow(
            watchSessionSnapshot(
                watchIdentity = canonicalProfile("ABC123"),
                directWatchAuthorized = false
            )
        )
        val ognRepository = FakeOgnTrafficRepository()
        ognRepository.targets.value = listOf(
            ognTarget(
                addressHex = "ABC123",
                addressType = OgnAddressType.FLARM,
                lastSeenMillis = 14_900L,
                registration = "REG1"
            ),
            ognTarget(
                addressHex = "ABC123",
                addressType = OgnAddressType.FLARM,
                lastSeenMillis = 14_800L,
                registration = "REG2"
            )
        )
        val repository = WatchTrafficRepository(
            scope = scope,
            clock = clock,
            sessionState = sessionState,
            ognTrafficRepository = ognRepository,
            directWatchTrafficSource = FakeDirectWatchTrafficSource()
        )
        runCurrent()

        val snapshot = repository.state.value
        assertEquals(LiveFollowSessionState.AMBIGUOUS, snapshot.sourceState)
        assertNull(snapshot.activeSource)
        assertEquals(
            LiveFollowIdentityAmbiguityReason.MULTIPLE_EXACT_MATCHES,
            (snapshot.identityResolution as? com.example.xcpro.livefollow.model.LiveFollowIdentityResolution.Ambiguous)
                ?.reason
        )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun state_transitionsFromDirectToStaleAndOfflineUsingMonotonicTicker() = runTest {
        val scope = repoScope()
        try {
        val clock = FakeClock(monoMs = 10_000L, wallMs = 0L)
        val sessionState = MutableStateFlow(
            watchSessionSnapshot(
                watchIdentity = null,
                directWatchAuthorized = true
            )
        )
        val directSource = FakeDirectWatchTrafficSource()
        directSource.aircraft.value = directAircraftSample(fixMonoMs = 9_950L)
        val repository = WatchTrafficRepository(
            scope = scope,
            clock = clock,
            sessionState = sessionState,
            ognTrafficRepository = FakeOgnTrafficRepository(),
            directWatchTrafficSource = directSource
        )
        runCurrent()
        assertEquals(LiveFollowSessionState.LIVE_DIRECT, repository.state.value.sourceState)

        directSource.aircraft.value = null
        runCurrent()

        clock.setMonoMs(26_000L)
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(LiveFollowSessionState.STALE, repository.state.value.sourceState)
        assertNull(repository.state.value.aircraft)

        clock.setMonoMs(56_000L)
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(LiveFollowSessionState.OFFLINE, repository.state.value.sourceState)
        assertEquals(LiveFollowSourceEligibility.UNAVAILABLE, repository.state.value.ognEligibility)
        assertEquals(LiveFollowSourceEligibility.UNAVAILABLE, repository.state.value.directEligibility)
        } finally {
            scope.cancel()
        }
    }

    private fun TestScope.repoScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

    private fun watchSessionSnapshot(
        watchIdentity: LiveFollowIdentityProfile?,
        directWatchAuthorized: Boolean
    ): LiveFollowSessionSnapshot = LiveFollowSessionSnapshot(
        sessionId = "watch-session",
        role = LiveFollowSessionRole.WATCHER,
        lifecycle = LiveFollowSessionLifecycle.ACTIVE,
        runtimeMode = LiveFollowRuntimeMode.LIVE,
        watchIdentity = watchIdentity,
        directWatchAuthorized = directWatchAuthorized,
        transportAvailability = liveFollowAvailableTransport(),
        sideEffectsAllowed = true,
        replayBlockReason = LiveFollowReplayBlockReason.NONE,
        lastError = null
    )

    private fun canonicalProfile(hex: String): LiveFollowIdentityProfile =
        LiveFollowIdentityProfile(
            canonicalIdentity = LiveFollowAircraftIdentity.create(
                type = LiveFollowAircraftIdentityType.FLARM,
                rawValue = hex,
                verified = true
            )
        )

    private fun ognTarget(
        addressHex: String,
        addressType: OgnAddressType,
        lastSeenMillis: Long,
        registration: String? = null
    ): OgnTrafficTarget = OgnTrafficTarget(
        id = "target-$addressHex-$registration",
        callsign = "TGT$addressHex",
        destination = "",
        latitude = -33.9,
        longitude = 151.2,
        altitudeMeters = 500.0,
        trackDegrees = 180.0,
        groundSpeedMps = 12.0,
        verticalSpeedMps = 1.5,
        deviceIdHex = addressHex,
        signalDb = null,
        displayLabel = registration ?: "OGN-$addressHex",
        identity = OgnTrafficIdentity(
            registration = registration,
            competitionNumber = null,
            aircraftModel = null,
            tracked = true,
            identified = true,
            aircraftTypeCode = null
        ),
        rawComment = null,
        rawLine = "",
        timestampMillis = 100_000L,
        lastSeenMillis = lastSeenMillis,
        sourceTimestampWallMs = 100_000L,
        addressType = addressType,
        addressHex = addressHex
    )

    private fun directAircraftSample(fixMonoMs: Long): DirectWatchAircraftSample =
        DirectWatchAircraftSample(
            state = LiveFollowSourceState.VALID,
            confidence = LiveFollowConfidence.HIGH,
            latitudeDeg = -33.91,
            longitudeDeg = 151.21,
            altitudeMslMeters = 510.0,
            groundSpeedMs = 13.0,
            trackDeg = 185.0,
            verticalSpeedMs = 2.0,
            fixMonoMs = fixMonoMs,
            fixWallMs = 101_000L,
            canonicalIdentity = null,
            displayLabel = "DIRECT-1"
        )

    private class FakeDirectWatchTrafficSource : DirectWatchTrafficSource {
        override val aircraft = MutableStateFlow<DirectWatchAircraftSample?>(null)
        override val transportAvailability = MutableStateFlow(liveFollowAvailableTransport())
    }

    private class FakeOgnTrafficRepository : OgnTrafficRepository {
        override val targets = MutableStateFlow<List<OgnTrafficTarget>>(emptyList())
        override val suppressedTargetIds = MutableStateFlow<Set<String>>(emptySet())
        override val snapshot = MutableStateFlow(
            OgnTrafficSnapshot(
                targets = emptyList(),
                connectionState = OgnConnectionState.DISCONNECTED,
                lastError = null,
                subscriptionCenterLat = null,
                subscriptionCenterLon = null,
                receiveRadiusKm = 0,
                ddbCacheAgeMs = null,
                reconnectBackoffMs = null,
                lastReconnectWallMs = null
            )
        )
        override val isEnabled = MutableStateFlow(false)

        override fun setEnabled(enabled: Boolean) {
            isEnabled.value = enabled
        }

        override fun updateCenter(latitude: Double, longitude: Double) = Unit

        override fun start() {
            isEnabled.value = true
        }

        override fun stop() {
            isEnabled.value = false
        }
    }
}
