package com.example.xcpro.adsb

internal data class AdsbProximityTrendAssessment(
    val hasTrendSample: Boolean,
    val isClosing: Boolean,
    val closingRateMps: Double?,
    val showClosingAlert: Boolean
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
        hasOwnshipReference: Boolean
    ): AdsbProximityTrendAssessment {
        if (!hasOwnshipReference || !distanceMeters.isFinite()) {
            stateByTargetId.remove(id)
            return AdsbProximityTrendAssessment(
                hasTrendSample = false,
                isClosing = false,
                closingRateMps = null,
                showClosingAlert = false
            )
        }

        val existing = stateByTargetId[id]
        if (existing == null) {
            stateByTargetId[id] = TrendState(
                previousDistanceMeters = distanceMeters,
                previousMonoMs = nowMonoMs,
                hasTrendSample = false,
                isClosing = false,
                closingRateMps = null,
                recoveryUntilMonoMs = null
            )
            return AdsbProximityTrendAssessment(
                hasTrendSample = false,
                isClosing = false,
                closingRateMps = null,
                showClosingAlert = true
            )
        }

        val dtMs = nowMonoMs - existing.previousMonoMs
        if (dtMs < minTrendSampleDtMs || dtMs <= 0L) {
            val recoveryActive = isRecoveryActive(existing.recoveryUntilMonoMs, nowMonoMs)
            return AdsbProximityTrendAssessment(
                hasTrendSample = existing.hasTrendSample,
                isClosing = existing.isClosing,
                closingRateMps = existing.closingRateMps,
                showClosingAlert = !existing.hasTrendSample || existing.isClosing || recoveryActive
            )
        }

        val closingRateMps =
            (existing.previousDistanceMeters - distanceMeters) / (dtMs.toDouble() / 1_000.0)
        val nextIsClosing = when {
            !existing.hasTrendSample -> closingRateMps >= closingEnterMs
            existing.isClosing -> closingRateMps > closingExitMs
            else -> closingRateMps >= closingEnterMs
        }
        val nextRecoveryUntilMonoMs = when {
            nextIsClosing -> null
            existing.isClosing -> nowMonoMs + recoveryDwellMs
            isRecoveryActive(existing.recoveryUntilMonoMs, nowMonoMs) -> existing.recoveryUntilMonoMs
            else -> null
        }
        val nextState = TrendState(
            previousDistanceMeters = distanceMeters,
            previousMonoMs = nowMonoMs,
            hasTrendSample = true,
            isClosing = nextIsClosing,
            closingRateMps = closingRateMps,
            recoveryUntilMonoMs = nextRecoveryUntilMonoMs
        )
        stateByTargetId[id] = nextState
        val recoveryActive = isRecoveryActive(nextState.recoveryUntilMonoMs, nowMonoMs)
        return AdsbProximityTrendAssessment(
            hasTrendSample = nextState.hasTrendSample,
            isClosing = nextState.isClosing,
            closingRateMps = nextState.closingRateMps,
            showClosingAlert = nextState.isClosing || recoveryActive
        )
    }

    private fun isRecoveryActive(recoveryUntilMonoMs: Long?, nowMonoMs: Long): Boolean =
        recoveryUntilMonoMs != null && nowMonoMs < recoveryUntilMonoMs

    private data class TrendState(
        val previousDistanceMeters: Double,
        val previousMonoMs: Long,
        val hasTrendSample: Boolean,
        val isClosing: Boolean,
        val closingRateMps: Double?,
        val recoveryUntilMonoMs: Long?
    )

    private companion object {
        const val CLOSING_ENTER_MS = 1.0
        const val CLOSING_EXIT_MS = 0.3
        const val RECOVERY_DWELL_MS = 4_000L
        const val MIN_TREND_SAMPLE_DT_MS = 800L
    }
}
