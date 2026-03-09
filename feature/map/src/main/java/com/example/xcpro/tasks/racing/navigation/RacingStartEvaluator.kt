package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.core.RacingPevCustomParams
import com.example.xcpro.tasks.core.RacingAltitudeReference
import com.example.xcpro.tasks.core.RacingStartCustomParams
import com.example.xcpro.tasks.racing.models.RacingStartPointType

internal class RacingStartEvaluator {

    fun updatePreStartAltitudeSatisfied(
        state: RacingNavigationState,
        fix: RacingNavigationFix,
        rules: RacingStartCustomParams
    ): Boolean {
        if (state.preStartAltitudeSatisfied) return true
        val threshold = rules.preStartAltitudeMeters ?: return true
        val altitude = resolveStartAltitudeMeters(fix, rules.altitudeReference) ?: return false
        val gateOpen = rules.gateOpenTimeMillis
        val gateIsOpenForEvidence = gateOpen == null || fix.timestampMillis >= gateOpen
        return gateIsOpenForEvidence && altitude < threshold
    }

    fun evaluateStrictCandidate(
        state: RacingNavigationState,
        fix: RacingNavigationFix,
        timestampMillis: Long,
        startType: RacingStartPointType,
        rules: RacingStartCustomParams
    ): RacingStartCandidate {
        return evaluateCandidate(
            state = state,
            fix = fix,
            timestampMillis = timestampMillis,
            startType = startType,
            rules = rules,
            candidateType = RacingStartCandidateType.STRICT
        )
    }

    fun evaluateToleranceCandidate(
        state: RacingNavigationState,
        fix: RacingNavigationFix,
        timestampMillis: Long,
        startType: RacingStartPointType,
        rules: RacingStartCustomParams
    ): RacingStartCandidate {
        return evaluateCandidate(
            state = state,
            fix = fix,
            timestampMillis = timestampMillis,
            startType = startType,
            rules = rules,
            candidateType = RacingStartCandidateType.TOLERANCE
        )
    }

    fun rejectedDirectionCandidate(timestampMillis: Long): RacingStartCandidate {
        return RacingStartCandidate(
            timestampMillis = nearestSecondTimestamp(timestampMillis),
            candidateType = RacingStartCandidateType.STRICT,
            isValid = false,
            rejectionReason = RacingStartRejectionReason.WRONG_DIRECTION
        )
    }

    fun appendCandidate(state: RacingNavigationState, candidate: RacingStartCandidate): RacingNavigationState {
        val last = state.startCandidates.lastOrNull()
        if (last == candidate) return state
        return state.copy(startCandidates = state.startCandidates + candidate)
    }

    private fun evaluateCandidate(
        state: RacingNavigationState,
        fix: RacingNavigationFix,
        timestampMillis: Long,
        startType: RacingStartPointType,
        rules: RacingStartCustomParams,
        candidateType: RacingStartCandidateType
    ): RacingStartCandidate {
        val normalizedTimestamp = nearestSecondTimestamp(timestampMillis)
        if (rules.gateOpenTimeMillis != null && normalizedTimestamp < rules.gateOpenTimeMillis) {
            return RacingStartCandidate(
                timestampMillis = normalizedTimestamp,
                candidateType = candidateType,
                isValid = false,
                rejectionReason = RacingStartRejectionReason.GATE_NOT_OPEN
            )
        }
        if (rules.gateCloseTimeMillis != null && normalizedTimestamp > rules.gateCloseTimeMillis) {
            return RacingStartCandidate(
                timestampMillis = normalizedTimestamp,
                candidateType = candidateType,
                isValid = false,
                rejectionReason = RacingStartRejectionReason.GATE_CLOSED
            )
        }

        val penalties = linkedSetOf<RacingStartPenaltyFlag>()
        if (candidateType == RacingStartCandidateType.TOLERANCE) {
            penalties += RacingStartPenaltyFlag.TOLERANCE_START
        }
        if (rules.preStartAltitudeMeters != null && !state.preStartAltitudeSatisfied) {
            penalties += RacingStartPenaltyFlag.PRE_START_ALTITUDE_NOT_SATISFIED
        }
        if (rules.altitudeReference == RacingAltitudeReference.QNH &&
            fix.altitudeQnhMeters == null &&
            fix.altitudeMslMeters != null
        ) {
            penalties += RacingStartPenaltyFlag.ALTITUDE_REFERENCE_FALLBACK_TO_MSL
        }
        val startAltitude = resolveStartAltitudeMeters(fix, rules.altitudeReference)
        if (rules.maxStartAltitudeMeters != null &&
            startAltitude != null &&
            startAltitude > rules.maxStartAltitudeMeters
        ) {
            penalties += RacingStartPenaltyFlag.MAX_START_ALTITUDE_EXCEEDED
        }
        if (rules.maxStartGroundspeedMs != null &&
            fix.groundSpeedMs != null &&
            fix.groundSpeedMs > rules.maxStartGroundspeedMs
        ) {
            penalties += RacingStartPenaltyFlag.MAX_START_GROUNDSPEED_EXCEEDED
        }
        penalties += evaluatePevPenalties(
            timestampMillis = normalizedTimestamp,
            startType = startType,
            pev = rules.pev
        )

        return RacingStartCandidate(
            timestampMillis = normalizedTimestamp,
            candidateType = candidateType,
            isValid = true,
            penaltyFlags = penalties
        )
    }

    private fun evaluatePevPenalties(
        timestampMillis: Long,
        startType: RacingStartPointType,
        pev: RacingPevCustomParams
    ): Set<RacingStartPenaltyFlag> {
        if (!pev.enabled) return emptySet()
        val waitMinutes = pev.waitTimeMinutes
        val windowMinutes = pev.startWindowMinutes
        if (waitMinutes == null || windowMinutes == null) {
            return setOf(RacingStartPenaltyFlag.PEV_MISSING)
        }

        val presses = effectivePresses(
            rawPresses = pev.pressTimestampsMillis,
            dedupeSeconds = pev.dedupeSeconds,
            maxPresses = pev.maxPressesPerLaunch,
            minIntervalMinutes = if (startType == RacingStartPointType.START_CYLINDER) pev.minIntervalMinutes else 0
        )
        if (presses.isEmpty()) {
            return setOf(RacingStartPenaltyFlag.PEV_MISSING)
        }

        val activePress = presses.lastOrNull { it <= timestampMillis } ?: return setOf(RacingStartPenaltyFlag.PEV_OUTSIDE_WINDOW)
        val windowStart = activePress + waitMinutes * MILLIS_PER_MINUTE
        val windowEnd = windowStart + windowMinutes * MILLIS_PER_MINUTE
        return if (timestampMillis in windowStart..windowEnd) {
            emptySet()
        } else {
            setOf(RacingStartPenaltyFlag.PEV_OUTSIDE_WINDOW)
        }
    }

    private fun effectivePresses(
        rawPresses: List<Long>,
        dedupeSeconds: Long,
        maxPresses: Int,
        minIntervalMinutes: Int
    ): List<Long> {
        if (rawPresses.isEmpty()) return emptyList()
        val sorted = rawPresses.sorted()
        val dedupeWindowMs = dedupeSeconds.coerceAtLeast(0L) * 1000L
        val deduped = mutableListOf<Long>()
        sorted.forEach { press ->
            val previous = deduped.lastOrNull()
            if (previous == null || press - previous > dedupeWindowMs) {
                deduped += press
            }
        }

        val minIntervalMs = minIntervalMinutes.coerceAtLeast(0) * MILLIS_PER_MINUTE
        val intervalFiltered = mutableListOf<Long>()
        deduped.forEach { press ->
            val previous = intervalFiltered.lastOrNull()
            if (previous == null || press - previous >= minIntervalMs) {
                intervalFiltered += press
            }
        }

        val clampedMaxPresses = maxPresses.coerceIn(1, 10)
        return intervalFiltered.take(clampedMaxPresses)
    }

    private fun resolveStartAltitudeMeters(
        fix: RacingNavigationFix,
        reference: RacingAltitudeReference
    ): Double? {
        return when (reference) {
            RacingAltitudeReference.MSL -> fix.altitudeMslMeters
            RacingAltitudeReference.QNH -> fix.altitudeQnhMeters ?: fix.altitudeMslMeters
        }
    }

    private fun nearestSecondTimestamp(timestampMillis: Long): Long {
        val rounded = ((timestampMillis + 500L) / 1000L) * 1000L
        return rounded
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
