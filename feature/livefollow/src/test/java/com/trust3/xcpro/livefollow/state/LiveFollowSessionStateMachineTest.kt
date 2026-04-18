package com.trust3.xcpro.livefollow.state

import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.livefollow.model.LiveFollowConfidence
import com.trust3.xcpro.livefollow.model.LiveFollowIdentityAmbiguityReason
import com.trust3.xcpro.livefollow.model.LiveFollowIdentityProfile
import com.trust3.xcpro.livefollow.model.LiveFollowIdentityResolution
import com.trust3.xcpro.livefollow.model.LiveFollowSourceArbitrationDecision
import com.trust3.xcpro.livefollow.model.LiveFollowSourceEligibility
import com.trust3.xcpro.livefollow.model.LiveFollowSourceSample
import com.trust3.xcpro.livefollow.model.LiveFollowSourceSelectionReason
import com.trust3.xcpro.livefollow.model.LiveFollowSourceState
import com.trust3.xcpro.livefollow.model.LiveFollowSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFollowSessionStateMachineTest {

    @Test
    fun evaluate_transitionsAcrossWaitingAmbiguousLiveStaleOfflineAndStopped() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val stateMachine = LiveFollowSessionStateMachine(
            clock = clock,
            policy = LiveFollowSessionStatePolicy(
                staleAfterMs = 15_000L,
                offlineAfterMs = 45_000L
            )
        )

        val waiting = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision()
            )
        )
        assertEquals(LiveFollowSessionState.WAITING, waiting.state)

        val ambiguous = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision(),
                ognIdentityResolution = LiveFollowIdentityResolution.Ambiguous(
                    reason = LiveFollowIdentityAmbiguityReason.MULTIPLE_ALIAS_MATCHES,
                    candidates = listOf(
                        LiveFollowIdentityProfile(canonicalIdentity = null),
                        LiveFollowIdentityProfile(canonicalIdentity = null)
                    )
                )
            )
        )
        assertEquals(LiveFollowSessionState.AMBIGUOUS, ambiguous.state)

        clock.setMonoMs(10_000L)
        val liveOgn = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = liveDecision(
                    source = LiveFollowSourceType.OGN,
                    fixMonoMs = 9_500L
                )
            )
        )
        assertEquals(LiveFollowSessionState.LIVE_OGN, liveOgn.state)
        assertEquals(LiveFollowSourceType.OGN, liveOgn.activeSource)

        clock.setMonoMs(12_000L)
        val liveDirect = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = liveDecision(
                    source = LiveFollowSourceType.DIRECT,
                    fixMonoMs = 11_900L
                )
            )
        )
        assertEquals(LiveFollowSessionState.LIVE_DIRECT, liveDirect.state)
        assertEquals(LiveFollowSourceType.DIRECT, liveDirect.activeSource)

        clock.setMonoMs(31_000L)
        val stale = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision()
            )
        )
        assertEquals(LiveFollowSessionState.STALE, stale.state)
        assertTrue((stale.ageMs ?: 0L) > 15_000L)

        clock.setMonoMs(60_000L)
        val offline = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision()
            )
        )
        assertEquals(LiveFollowSessionState.OFFLINE, offline.state)
        assertTrue((offline.ageMs ?: 0L) > 45_000L)

        val stopped = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision(),
                stopRequested = true
            )
        )
        assertEquals(LiveFollowSessionState.STOPPED, stopped.state)
        assertEquals(null, stopped.lastLiveSource)
    }

    @Test
    fun evaluate_sameInputSequence_isDeterministicAcrossInstances() {
        val firstRun = runSequence()
        val secondRun = runSequence()

        assertEquals(firstRun, secondRun)
    }

    @Test
    fun replayPolicy_blocksSideEffectsDuringReplay() {
        val policy = LiveFollowReplayPolicy()

        val liveDecision = policy.evaluate(LiveFollowRuntimeMode.LIVE)
        val replayDecision = policy.evaluate(LiveFollowRuntimeMode.REPLAY)

        assertTrue(liveDecision.sideEffectsAllowed)
        assertEquals(LiveFollowReplayBlockReason.NONE, liveDecision.blockReason)
        assertFalse(replayDecision.sideEffectsAllowed)
        assertEquals(LiveFollowReplayBlockReason.REPLAY_MODE, replayDecision.blockReason)
    }

    @Test
    fun evaluate_liveOgnToAmbiguous_clearsRetainedLiveBind() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val stateMachine = LiveFollowSessionStateMachine(clock = clock)

        clock.setMonoMs(10_000L)
        stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = liveDecision(
                    source = LiveFollowSourceType.OGN,
                    fixMonoMs = 9_900L
                )
            )
        )

        clock.setMonoMs(11_000L)
        val ambiguous = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision(),
                ognIdentityResolution = ambiguousIdentity()
            )
        )

        assertEquals(LiveFollowSessionState.AMBIGUOUS, ambiguous.state)
        assertEquals(null, ambiguous.activeSource)
        assertEquals(null, ambiguous.lastLiveSource)
        assertEquals(null, ambiguous.ageMs)

        val waiting = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision()
            )
        )
        assertEquals(LiveFollowSessionState.WAITING, waiting.state)
        assertEquals(null, waiting.lastLiveSource)
    }

    @Test
    fun evaluate_liveDirectToAmbiguous_clearsRetainedLiveBind() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val stateMachine = LiveFollowSessionStateMachine(clock = clock)

        clock.setMonoMs(10_000L)
        stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = liveDecision(
                    source = LiveFollowSourceType.DIRECT,
                    fixMonoMs = 9_950L
                )
            )
        )

        clock.setMonoMs(11_000L)
        val ambiguous = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision(),
                ognIdentityResolution = ambiguousIdentity()
            )
        )

        assertEquals(LiveFollowSessionState.AMBIGUOUS, ambiguous.state)
        assertEquals(null, ambiguous.activeSource)
        assertEquals(null, ambiguous.lastLiveSource)
        assertEquals(null, ambiguous.ageMs)

        val waiting = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision()
            )
        )
        assertEquals(LiveFollowSessionState.WAITING, waiting.state)
        assertEquals(null, waiting.lastLiveSource)
    }

    @Test
    fun evaluate_staleToLiveOgn_returnsToLiveOgn() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val stateMachine = LiveFollowSessionStateMachine(clock = clock)

        clock.setMonoMs(10_000L)
        stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = liveDecision(
                    source = LiveFollowSourceType.OGN,
                    fixMonoMs = 9_500L
                )
            )
        )

        clock.setMonoMs(25_000L)
        val stale = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision()
            )
        )
        assertEquals(LiveFollowSessionState.STALE, stale.state)

        clock.setMonoMs(26_000L)
        val recovered = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = liveDecision(
                    source = LiveFollowSourceType.OGN,
                    fixMonoMs = 25_900L
                )
            )
        )
        assertEquals(LiveFollowSessionState.LIVE_OGN, recovered.state)
        assertEquals(LiveFollowSourceType.OGN, recovered.activeSource)
    }

    @Test
    fun evaluate_staleToLiveDirect_returnsToLiveDirect() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val stateMachine = LiveFollowSessionStateMachine(clock = clock)

        clock.setMonoMs(10_000L)
        stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = liveDecision(
                    source = LiveFollowSourceType.DIRECT,
                    fixMonoMs = 9_500L
                )
            )
        )

        clock.setMonoMs(25_000L)
        val stale = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision()
            )
        )
        assertEquals(LiveFollowSessionState.STALE, stale.state)

        clock.setMonoMs(26_000L)
        val recovered = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = liveDecision(
                    source = LiveFollowSourceType.DIRECT,
                    fixMonoMs = 25_900L
                )
            )
        )
        assertEquals(LiveFollowSessionState.LIVE_DIRECT, recovered.state)
        assertEquals(LiveFollowSourceType.DIRECT, recovered.activeSource)
    }

    @Test
    fun evaluate_offlineToLiveOgn_returnsToLiveOgn() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val stateMachine = LiveFollowSessionStateMachine(clock = clock)

        clock.setMonoMs(10_000L)
        stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = liveDecision(
                    source = LiveFollowSourceType.OGN,
                    fixMonoMs = 9_500L
                )
            )
        )

        clock.setMonoMs(55_000L)
        val offline = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision()
            )
        )
        assertEquals(LiveFollowSessionState.OFFLINE, offline.state)

        clock.setMonoMs(56_000L)
        val recovered = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = liveDecision(
                    source = LiveFollowSourceType.OGN,
                    fixMonoMs = 55_900L
                )
            )
        )
        assertEquals(LiveFollowSessionState.LIVE_OGN, recovered.state)
        assertEquals(LiveFollowSourceType.OGN, recovered.activeSource)
    }

    @Test
    fun evaluate_offlineToLiveDirect_returnsToLiveDirect() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val stateMachine = LiveFollowSessionStateMachine(clock = clock)

        clock.setMonoMs(10_000L)
        stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = liveDecision(
                    source = LiveFollowSourceType.DIRECT,
                    fixMonoMs = 9_500L
                )
            )
        )

        clock.setMonoMs(55_000L)
        val offline = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision()
            )
        )
        assertEquals(LiveFollowSessionState.OFFLINE, offline.state)

        clock.setMonoMs(56_000L)
        val recovered = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = liveDecision(
                    source = LiveFollowSourceType.DIRECT,
                    fixMonoMs = 55_900L
                )
            )
        )
        assertEquals(LiveFollowSessionState.LIVE_DIRECT, recovered.state)
        assertEquals(LiveFollowSourceType.DIRECT, recovered.activeSource)
    }

    @Test
    fun evaluate_holdsLiveUntilStaleThreshold() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val stateMachine = LiveFollowSessionStateMachine(clock = clock)

        clock.setMonoMs(10_000L)
        stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = liveDecision(
                    source = LiveFollowSourceType.OGN,
                    fixMonoMs = 9_500L
                )
            )
        )

        clock.setMonoMs(24_500L)
        val held = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision()
            )
        )

        assertEquals(LiveFollowSessionState.LIVE_OGN, held.state)
        assertEquals(null, held.activeSource)
        assertEquals(LiveFollowSourceType.OGN, held.lastLiveSource)
        assertEquals(15_000L, held.ageMs)
    }

    private fun runSequence(): List<LiveFollowSessionStateDecision> {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val stateMachine = LiveFollowSessionStateMachine(clock = clock)
        val outputs = mutableListOf<LiveFollowSessionStateDecision>()

        outputs += stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision()
            )
        )

        clock.setMonoMs(5_000L)
        outputs += stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = liveDecision(
                    source = LiveFollowSourceType.OGN,
                    fixMonoMs = 4_900L
                )
            )
        )

        clock.setMonoMs(25_000L)
        outputs += stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision()
            )
        )

        clock.setMonoMs(50_000L)
        outputs += stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = noSourceDecision()
            )
        )

        return outputs
    }

    private fun ambiguousIdentity(): LiveFollowIdentityResolution.Ambiguous =
        LiveFollowIdentityResolution.Ambiguous(
            reason = LiveFollowIdentityAmbiguityReason.MULTIPLE_ALIAS_MATCHES,
            candidates = listOf(
                LiveFollowIdentityProfile(canonicalIdentity = null),
                LiveFollowIdentityProfile(canonicalIdentity = null)
            )
        )

    private fun liveDecision(
        source: LiveFollowSourceType,
        fixMonoMs: Long
    ): LiveFollowSourceArbitrationDecision = LiveFollowSourceArbitrationDecision(
        selectedSource = source,
        selectedSample = LiveFollowSourceSample(
            source = source,
            state = LiveFollowSourceState.VALID,
            confidence = LiveFollowConfidence.HIGH,
            fixMonoMs = fixMonoMs,
            identityResolution = LiveFollowIdentityResolution.ExactVerifiedMatch(
                profile = LiveFollowIdentityProfile(canonicalIdentity = null)
            )
        ),
        reason = when (source) {
            LiveFollowSourceType.OGN -> LiveFollowSourceSelectionReason.SELECTED_OGN
            LiveFollowSourceType.DIRECT -> LiveFollowSourceSelectionReason.SELECTED_DIRECT
        },
        switched = true,
        lastSwitchMonoMs = fixMonoMs,
        ognEligibility = if (source == LiveFollowSourceType.OGN) {
            LiveFollowSourceEligibility.SELECTABLE
        } else {
            LiveFollowSourceEligibility.UNAVAILABLE
        },
        directEligibility = if (source == LiveFollowSourceType.DIRECT) {
            LiveFollowSourceEligibility.SELECTABLE
        } else {
            LiveFollowSourceEligibility.UNAVAILABLE
        }
    )

    private fun noSourceDecision(): LiveFollowSourceArbitrationDecision =
        LiveFollowSourceArbitrationDecision(
            selectedSource = null,
            selectedSample = null,
            reason = LiveFollowSourceSelectionReason.NO_USABLE_SOURCE,
            switched = false,
            lastSwitchMonoMs = null,
            ognEligibility = LiveFollowSourceEligibility.UNAVAILABLE,
            directEligibility = LiveFollowSourceEligibility.UNAVAILABLE
        )
}
