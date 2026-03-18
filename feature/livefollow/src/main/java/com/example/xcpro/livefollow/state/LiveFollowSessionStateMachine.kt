package com.example.xcpro.livefollow.state

import com.example.xcpro.core.time.Clock
import com.example.xcpro.livefollow.model.LiveFollowIdentityResolution
import com.example.xcpro.livefollow.model.LiveFollowSourceType

class LiveFollowSessionStateMachine(
    private val clock: Clock,
    private val policy: LiveFollowSessionStatePolicy = LiveFollowSessionStatePolicy()
) {
    private var state: LiveFollowSessionState = LiveFollowSessionState.WAITING
    private var lastLiveSource: LiveFollowSourceType? = null
    private var lastLiveFixMonoMs: Long? = null
    private var lastDecisionMonoMs: Long = 0L

    fun clear() {
        state = LiveFollowSessionState.WAITING
        lastLiveSource = null
        lastLiveFixMonoMs = null
        lastDecisionMonoMs = 0L
    }

    fun evaluate(input: LiveFollowSessionStateInput): LiveFollowSessionStateDecision {
        val nowMonoMs = normalizedNow(clock.nowMonoMs())

        if (input.stopRequested) {
            state = LiveFollowSessionState.STOPPED
            lastLiveSource = null
            lastLiveFixMonoMs = null
            return LiveFollowSessionStateDecision(
                state = state,
                activeSource = null,
                lastLiveSource = null,
                ageMs = null
            )
        }

        if (input.ognIdentityResolution is LiveFollowIdentityResolution.Ambiguous) {
            state = LiveFollowSessionState.AMBIGUOUS
            lastLiveSource = null
            lastLiveFixMonoMs = null
            return LiveFollowSessionStateDecision(
                state = state,
                activeSource = null,
                lastLiveSource = null,
                ageMs = null
            )
        }

        val selectedSource = input.arbitrationDecision.selectedSource
        val selectedSample = input.arbitrationDecision.selectedSample
        if (selectedSource != null && selectedSample?.fixMonoMs != null) {
            lastLiveSource = selectedSource
            lastLiveFixMonoMs = selectedSample.fixMonoMs
            state = when (selectedSource) {
                LiveFollowSourceType.OGN -> LiveFollowSessionState.LIVE_OGN
                LiveFollowSourceType.DIRECT -> LiveFollowSessionState.LIVE_DIRECT
            }
            return LiveFollowSessionStateDecision(
                state = state,
                activeSource = selectedSource,
                lastLiveSource = lastLiveSource,
                ageMs = selectedSample.ageMs(nowMonoMs)
            )
        }

        val lastAgeMs = lastLiveFixMonoMs?.let { fixMonoMs ->
            (nowMonoMs - fixMonoMs).coerceAtLeast(0L)
        }
        if (lastAgeMs != null && lastLiveSource != null) {
            state = when {
                lastAgeMs > policy.offlineAfterMs -> LiveFollowSessionState.OFFLINE
                lastAgeMs > policy.staleAfterMs -> LiveFollowSessionState.STALE
                lastLiveSource == LiveFollowSourceType.OGN -> LiveFollowSessionState.LIVE_OGN
                else -> LiveFollowSessionState.LIVE_DIRECT
            }
            return LiveFollowSessionStateDecision(
                state = state,
                activeSource = null,
                lastLiveSource = lastLiveSource,
                ageMs = lastAgeMs
            )
        }

        state = LiveFollowSessionState.WAITING
        return LiveFollowSessionStateDecision(
            state = state,
            activeSource = null,
            lastLiveSource = lastLiveSource,
            ageMs = null
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
