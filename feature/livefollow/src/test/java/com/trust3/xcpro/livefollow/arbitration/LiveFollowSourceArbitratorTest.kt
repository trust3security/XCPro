package com.trust3.xcpro.livefollow.arbitration

import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.livefollow.model.LiveFollowConfidence
import com.trust3.xcpro.livefollow.model.LiveFollowIdentityProfile
import com.trust3.xcpro.livefollow.model.LiveFollowIdentityResolution
import com.trust3.xcpro.livefollow.model.LiveFollowSourceArbitrationPolicy
import com.trust3.xcpro.livefollow.model.LiveFollowSourceEligibility
import com.trust3.xcpro.livefollow.model.LiveFollowSourceSample
import com.trust3.xcpro.livefollow.model.LiveFollowSourceSelectionReason
import com.trust3.xcpro.livefollow.model.LiveFollowSourceState
import com.trust3.xcpro.livefollow.model.LiveFollowSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFollowSourceArbitratorTest {

    private val clock = FakeClock(monoMs = 0L, wallMs = 0L)
    private val arbitrator = LiveFollowSourceArbitrator(
        clock = clock,
        policy = LiveFollowSourceArbitrationPolicy(
            freshnessTimeoutMs = 15_000L,
            minSwitchDwellMs = 5_000L
        )
    )

    @Test
    fun evaluate_prefersOgnWhenValidAndFresh() {
        clock.setMonoMs(10_000L)

        val decision = arbitrator.evaluate(
            ognSample = ognSample(fixMonoMs = 9_500L),
            directSample = directSample(fixMonoMs = 9_000L)
        )

        assertEquals(LiveFollowSourceType.OGN, decision.selectedSource)
        assertEquals(LiveFollowSourceSelectionReason.SELECTED_OGN, decision.reason)
        assertEquals(LiveFollowSourceEligibility.SELECTABLE, decision.ognEligibility)
        assertEquals(LiveFollowSourceEligibility.SELECTABLE, decision.directEligibility)
        assertTrue(decision.switched)
    }

    @Test
    fun evaluate_selectsDirectWhenOgnIsStale() {
        clock.setMonoMs(20_000L)

        val decision = arbitrator.evaluate(
            ognSample = ognSample(fixMonoMs = 1_000L),
            directSample = directSample(fixMonoMs = 19_500L)
        )

        assertEquals(LiveFollowSourceType.DIRECT, decision.selectedSource)
        assertEquals(LiveFollowSourceSelectionReason.SELECTED_DIRECT, decision.reason)
        assertEquals(LiveFollowSourceEligibility.STALE, decision.ognEligibility)
        assertEquals(LiveFollowSourceEligibility.SELECTABLE, decision.directEligibility)
    }

    @Test
    fun evaluate_selectsDirectWhenOgnIdentityIsUnresolved() {
        clock.setMonoMs(10_000L)

        val decision = arbitrator.evaluate(
            ognSample = ognSample(
                fixMonoMs = 9_800L,
                identityResolution = LiveFollowIdentityResolution.NoMatch
            ),
            directSample = directSample(fixMonoMs = 9_900L)
        )

        assertEquals(LiveFollowSourceType.DIRECT, decision.selectedSource)
        assertEquals(LiveFollowSourceSelectionReason.SELECTED_DIRECT, decision.reason)
        assertEquals(
            LiveFollowSourceEligibility.UNRESOLVED_IDENTITY,
            decision.ognEligibility
        )
    }

    @Test
    fun evaluate_hysteresisPreventsRapidDirectToOgnFlapping() {
        clock.setMonoMs(20_000L)
        arbitrator.evaluate(
            ognSample = ognSample(fixMonoMs = 1_000L),
            directSample = directSample(fixMonoMs = 19_900L)
        )

        clock.setMonoMs(22_000L)
        val heldDecision = arbitrator.evaluate(
            ognSample = ognSample(fixMonoMs = 21_900L),
            directSample = directSample(fixMonoMs = 21_800L)
        )
        assertEquals(LiveFollowSourceType.DIRECT, heldDecision.selectedSource)
        assertEquals(
            LiveFollowSourceSelectionReason.HOLDING_DIRECT_DWELL,
            heldDecision.reason
        )
        assertFalse(heldDecision.switched)

        clock.setMonoMs(26_000L)
        val switchedDecision = arbitrator.evaluate(
            ognSample = ognSample(fixMonoMs = 25_900L),
            directSample = directSample(fixMonoMs = 25_800L)
        )
        assertEquals(LiveFollowSourceType.OGN, switchedDecision.selectedSource)
        assertEquals(LiveFollowSourceSelectionReason.FALLBACK_TO_OGN, switchedDecision.reason)
        assertTrue(switchedDecision.switched)
    }

    @Test
    fun evaluate_exposesUncertaintyWhenNoUsableSourceExists() {
        clock.setMonoMs(10_000L)

        val decision = arbitrator.evaluate(
            ognSample = ognSample(
                fixMonoMs = 9_800L,
                identityResolution = LiveFollowIdentityResolution.NoMatch
            ),
            directSample = directSample(
                fixMonoMs = 9_900L,
                authorized = false
            )
        )

        assertEquals(null, decision.selectedSource)
        assertEquals(LiveFollowSourceSelectionReason.NO_USABLE_SOURCE, decision.reason)
        assertEquals(
            LiveFollowSourceEligibility.UNRESOLVED_IDENTITY,
            decision.ognEligibility
        )
        assertEquals(LiveFollowSourceEligibility.UNAUTHORIZED, decision.directEligibility)
    }

    private fun ognSample(
        fixMonoMs: Long,
        identityResolution: LiveFollowIdentityResolution =
            LiveFollowIdentityResolution.ExactVerifiedMatch(
                LiveFollowIdentityProfile(canonicalIdentity = null)
            ),
        state: LiveFollowSourceState = LiveFollowSourceState.VALID
    ): LiveFollowSourceSample = LiveFollowSourceSample(
        source = LiveFollowSourceType.OGN,
        state = state,
        confidence = LiveFollowConfidence.HIGH,
        fixMonoMs = fixMonoMs,
        identityResolution = identityResolution
    )

    private fun directSample(
        fixMonoMs: Long,
        state: LiveFollowSourceState = LiveFollowSourceState.VALID,
        authorized: Boolean = true
    ): LiveFollowSourceSample = LiveFollowSourceSample(
        source = LiveFollowSourceType.DIRECT,
        state = state,
        confidence = LiveFollowConfidence.HIGH,
        fixMonoMs = fixMonoMs,
        sessionAuthorized = authorized
    )
}
