package com.example.xcpro.adsb

internal data class AdsbProximityTrendAssessment(
    val hasTrendSample: Boolean,
    val hasFreshTrendSample: Boolean,
    val isClosing: Boolean,
    val closingRateMps: Double?,
    val showClosingAlert: Boolean,
    val postPassDivergingSampleCount: Int
)

internal class AdsbProximityTrendEvaluator(
    private val closingEnterMs: Double = CLOSING_ENTER_MS,
    private val closingExitMs: Double = CLOSING_EXIT_MS,
    private val recoveryDwellMs: Long = RECOVERY_DWELL_MS,
    private val minTrendSampleDtMs: Long = MIN_TREND_SAMPLE_DT_MS
) {

    private val stateByTargetId = HashMap<Icao24, TrendState>()

    fun clear() {
        stateByTargetId.clear()
    }

    fun removeTarget(id: Icao24) {
        stateByTargetId.remove(id)
    }

    fun evaluate(
        id: Icao24,
        distanceMeters: Double,
        nowMonoMs: Long,
        hasOwnshipReference: Boolean,
        sampleMonoMs: Long = nowMonoMs
    ): AdsbProximityTrendAssessment {
        if (!hasOwnshipReference || !distanceMeters.isFinite()) {
            stateByTargetId.remove(id)
            return AdsbProximityTrendAssessment(
                hasTrendSample = false,
                hasFreshTrendSample = false,
                isClosing = false,
                closingRateMps = null,
                showClosingAlert = false,
                postPassDivergingSampleCount = 0
            )
        }

        val existing = stateByTargetId[id]
        if (existing == null) {
            stateByTargetId[id] = TrendState(
                previousDistanceMeters = distanceMeters,
                previousSampleMonoMs = sampleMonoMs,
                closestDistanceMeters = distanceMeters,
                hasTrendSample = false,
                isClosing = false,
                closingRateMps = null,
                recoveryUntilMonoMs = null,
                hadClosingEpisode = false,
                closingReentrySampleCount = 0,
                postPassDivergingSampleCount = 0
            )
            return AdsbProximityTrendAssessment(
                hasTrendSample = false,
                hasFreshTrendSample = false,
                isClosing = false,
                closingRateMps = null,
                showClosingAlert = true,
                postPassDivergingSampleCount = 0
            )
        }

        val sampleDtMs = sampleMonoMs - existing.previousSampleMonoMs
        if (sampleDtMs <= 0L || sampleDtMs < minTrendSampleDtMs) {
            val recoveryActive = isRecoveryActive(existing.recoveryUntilMonoMs, nowMonoMs)
            return AdsbProximityTrendAssessment(
                hasTrendSample = existing.hasTrendSample,
                hasFreshTrendSample = false,
                isClosing = existing.isClosing,
                closingRateMps = existing.closingRateMps,
                showClosingAlert = !existing.hasTrendSample || existing.isClosing || recoveryActive,
                postPassDivergingSampleCount = existing.postPassDivergingSampleCount
            )
        }

        val closingRateMps =
            (existing.previousDistanceMeters - distanceMeters) / (sampleDtMs.toDouble() / 1_000.0)
        val nextClosingCandidate = closingRateMps >= closingEnterMs
        val nextClosingReentrySampleCount = when {
            existing.isClosing -> 0
            existing.hasTrendSample && existing.hadClosingEpisode && nextClosingCandidate ->
                (existing.closingReentrySampleCount + 1)
                    .coerceAtMost(MAX_CLOSING_REENTRY_SAMPLES)
            else -> 0
        }
        val nextIsClosing = when {
            !existing.hasTrendSample -> closingRateMps >= closingEnterMs
            existing.isClosing -> closingRateMps > closingExitMs
            existing.hadClosingEpisode ->
                nextClosingReentrySampleCount >= CLOSING_REENTRY_MIN_CONSECUTIVE_SAMPLES
            else -> closingRateMps >= closingEnterMs
        }
        val nextRecoveryUntilMonoMs = when {
            nextIsClosing -> null
            existing.isClosing -> nowMonoMs + recoveryDwellMs
            isRecoveryActive(existing.recoveryUntilMonoMs, nowMonoMs) -> existing.recoveryUntilMonoMs
            else -> null
        }
        val nextClosestDistanceMeters = minOf(existing.closestDistanceMeters, distanceMeters)
        val hasPassedClosestApproach =
            distanceMeters - nextClosestDistanceMeters >= PASS_DISTANCE_DELTA_METERS
        val nextHadClosingEpisode = existing.hadClosingEpisode || nextIsClosing
        val recoveryActive = isRecoveryActive(nextRecoveryUntilMonoMs, nowMonoMs)
        val hasDivergingDeEscalationEvidence = nextHadClosingEpisode || hasPassedClosestApproach
        val nextPostPassDivergingSampleCount = when {
            nextIsClosing -> 0
            !hasDivergingDeEscalationEvidence -> 0
            recoveryActive -> 0
            else -> (existing.postPassDivergingSampleCount + 1)
                .coerceAtMost(MAX_POST_PASS_DIVERGING_SAMPLES)
        }
        val nextState = TrendState(
            previousDistanceMeters = distanceMeters,
            previousSampleMonoMs = sampleMonoMs,
            closestDistanceMeters = nextClosestDistanceMeters,
            hasTrendSample = true,
            isClosing = nextIsClosing,
            closingRateMps = closingRateMps,
            recoveryUntilMonoMs = nextRecoveryUntilMonoMs,
            hadClosingEpisode = nextHadClosingEpisode,
            closingReentrySampleCount = nextClosingReentrySampleCount.coerceAtLeast(0),
            postPassDivergingSampleCount = nextPostPassDivergingSampleCount
        )
        stateByTargetId[id] = nextState
        return AdsbProximityTrendAssessment(
            hasTrendSample = nextState.hasTrendSample,
            hasFreshTrendSample = true,
            isClosing = nextState.isClosing,
            closingRateMps = nextState.closingRateMps,
            showClosingAlert = nextState.isClosing || recoveryActive,
            postPassDivergingSampleCount = nextState.postPassDivergingSampleCount
        )
    }

    private fun isRecoveryActive(recoveryUntilMonoMs: Long?, nowMonoMs: Long): Boolean =
        recoveryUntilMonoMs != null && nowMonoMs < recoveryUntilMonoMs

    private data class TrendState(
        val previousDistanceMeters: Double,
        val previousSampleMonoMs: Long,
        val closestDistanceMeters: Double,
        val hasTrendSample: Boolean,
        val isClosing: Boolean,
        val closingRateMps: Double?,
        val recoveryUntilMonoMs: Long?,
        val hadClosingEpisode: Boolean,
        val closingReentrySampleCount: Int,
        val postPassDivergingSampleCount: Int
    )

    private companion object {
        const val CLOSING_ENTER_MS = 1.0
        const val CLOSING_EXIT_MS = 0.3
        const val RECOVERY_DWELL_MS = 4_000L
        const val MIN_TREND_SAMPLE_DT_MS = 800L
        const val PASS_DISTANCE_DELTA_METERS = 120.0
        const val CLOSING_REENTRY_MIN_CONSECUTIVE_SAMPLES = 2
        const val MAX_CLOSING_REENTRY_SAMPLES = 2
        const val MAX_POST_PASS_DIVERGING_SAMPLES = 8
    }
}
