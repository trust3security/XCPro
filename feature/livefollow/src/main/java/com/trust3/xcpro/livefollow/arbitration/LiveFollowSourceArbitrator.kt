package com.trust3.xcpro.livefollow.arbitration

import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.livefollow.model.LiveFollowIdentityResolution
import com.trust3.xcpro.livefollow.model.LiveFollowSourceArbitrationDecision
import com.trust3.xcpro.livefollow.model.LiveFollowSourceArbitrationPolicy
import com.trust3.xcpro.livefollow.model.LiveFollowSourceEligibility
import com.trust3.xcpro.livefollow.model.LiveFollowSourceSample
import com.trust3.xcpro.livefollow.model.LiveFollowSourceSelectionReason
import com.trust3.xcpro.livefollow.model.LiveFollowSourceState
import com.trust3.xcpro.livefollow.model.LiveFollowSourceType

class LiveFollowSourceArbitrator(
    private val clock: Clock,
    private val policy: LiveFollowSourceArbitrationPolicy = LiveFollowSourceArbitrationPolicy()
) {
    private var selectedSource: LiveFollowSourceType? = null
    private var lastSwitchMonoMs: Long? = null
    private var lastDecisionMonoMs: Long = 0L

    fun clear() {
        selectedSource = null
        lastSwitchMonoMs = null
        lastDecisionMonoMs = 0L
    }

    fun evaluate(
        ognSample: LiveFollowSourceSample?,
        directSample: LiveFollowSourceSample?
    ): LiveFollowSourceArbitrationDecision {
        val nowMonoMs = normalizedNow(clock.nowMonoMs())
        val ognEligibility = evaluateEligibility(
            sample = ognSample,
            nowMonoMs = nowMonoMs
        )
        val directEligibility = evaluateEligibility(
            sample = directSample,
            nowMonoMs = nowMonoMs
        )

        val decision = when {
            ognEligibility == LiveFollowSourceEligibility.SELECTABLE &&
                directEligibility == LiveFollowSourceEligibility.SELECTABLE &&
                selectedSource == LiveFollowSourceType.DIRECT &&
                shouldHoldCurrentSelection(nowMonoMs) -> {
                LiveFollowSourceArbitrationDecision(
                    selectedSource = LiveFollowSourceType.DIRECT,
                    selectedSample = directSample,
                    reason = LiveFollowSourceSelectionReason.HOLDING_DIRECT_DWELL,
                    switched = false,
                    lastSwitchMonoMs = lastSwitchMonoMs,
                    ognEligibility = ognEligibility,
                    directEligibility = directEligibility
                )
            }

            ognEligibility == LiveFollowSourceEligibility.SELECTABLE -> {
                buildDecision(
                    nextSource = LiveFollowSourceType.OGN,
                    nextSample = ognSample,
                    reason = when (selectedSource) {
                        null,
                        LiveFollowSourceType.OGN ->
                            LiveFollowSourceSelectionReason.SELECTED_OGN

                        LiveFollowSourceType.DIRECT ->
                            LiveFollowSourceSelectionReason.FALLBACK_TO_OGN
                    },
                    nowMonoMs = nowMonoMs,
                    ognEligibility = ognEligibility,
                    directEligibility = directEligibility
                )
            }

            directEligibility == LiveFollowSourceEligibility.SELECTABLE -> {
                buildDecision(
                    nextSource = LiveFollowSourceType.DIRECT,
                    nextSample = directSample,
                    reason = when (selectedSource) {
                        null,
                        LiveFollowSourceType.DIRECT ->
                            LiveFollowSourceSelectionReason.SELECTED_DIRECT

                        LiveFollowSourceType.OGN ->
                            LiveFollowSourceSelectionReason.FALLBACK_TO_DIRECT
                    },
                    nowMonoMs = nowMonoMs,
                    ognEligibility = ognEligibility,
                    directEligibility = directEligibility
                )
            }

            else -> {
                buildDecision(
                    nextSource = null,
                    nextSample = null,
                    reason = LiveFollowSourceSelectionReason.NO_USABLE_SOURCE,
                    nowMonoMs = nowMonoMs,
                    ognEligibility = ognEligibility,
                    directEligibility = directEligibility
                )
            }
        }

        selectedSource = decision.selectedSource
        if (decision.switched) {
            lastSwitchMonoMs = decision.lastSwitchMonoMs
        }
        return decision
    }

    private fun evaluateEligibility(
        sample: LiveFollowSourceSample?,
        nowMonoMs: Long
    ): LiveFollowSourceEligibility {
        if (sample == null) return LiveFollowSourceEligibility.UNAVAILABLE
        if (sample.state == LiveFollowSourceState.UNAVAILABLE) {
            return LiveFollowSourceEligibility.UNAVAILABLE
        }
        if (sample.state == LiveFollowSourceState.INVALID) {
            return LiveFollowSourceEligibility.INVALID
        }
        val fixMonoMs = sample.fixMonoMs ?: return LiveFollowSourceEligibility.MISSING_FIX
        if (nowMonoMs - fixMonoMs > policy.freshnessTimeoutMs) {
            return LiveFollowSourceEligibility.STALE
        }
        if (sample.source == LiveFollowSourceType.DIRECT && !sample.sessionAuthorized) {
            return LiveFollowSourceEligibility.UNAUTHORIZED
        }
        if (sample.source == LiveFollowSourceType.OGN && !hasResolvedIdentity(sample)) {
            return LiveFollowSourceEligibility.UNRESOLVED_IDENTITY
        }
        return LiveFollowSourceEligibility.SELECTABLE
    }

    private fun hasResolvedIdentity(sample: LiveFollowSourceSample): Boolean {
        return when (sample.identityResolution) {
            is LiveFollowIdentityResolution.ExactVerifiedMatch,
            is LiveFollowIdentityResolution.AliasVerifiedMatch -> true

            is LiveFollowIdentityResolution.Ambiguous,
            LiveFollowIdentityResolution.NoMatch,
            null -> false
        }
    }

    private fun shouldHoldCurrentSelection(nowMonoMs: Long): Boolean {
        val lastSwitch = lastSwitchMonoMs ?: return false
        return nowMonoMs - lastSwitch < policy.minSwitchDwellMs
    }

    private fun buildDecision(
        nextSource: LiveFollowSourceType?,
        nextSample: LiveFollowSourceSample?,
        reason: LiveFollowSourceSelectionReason,
        nowMonoMs: Long,
        ognEligibility: LiveFollowSourceEligibility,
        directEligibility: LiveFollowSourceEligibility
    ): LiveFollowSourceArbitrationDecision {
        val switched = selectedSource != nextSource
        val decisionSwitchMonoMs = if (switched) nowMonoMs else lastSwitchMonoMs
        return LiveFollowSourceArbitrationDecision(
            selectedSource = nextSource,
            selectedSample = nextSample,
            reason = reason,
            switched = switched,
            lastSwitchMonoMs = decisionSwitchMonoMs,
            ognEligibility = ognEligibility,
            directEligibility = directEligibility
        )
    }

    private fun normalizedNow(nowMonoMs: Long): Long {
        val normalizedNow = if (lastDecisionMonoMs > 0L && nowMonoMs < lastDecisionMonoMs) {
            lastDecisionMonoMs
        } else {
            nowMonoMs
        }
        lastDecisionMonoMs = normalizedNow
        return normalizedNow
    }
}
