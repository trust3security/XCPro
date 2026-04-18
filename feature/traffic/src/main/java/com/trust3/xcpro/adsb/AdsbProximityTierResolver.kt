package com.trust3.xcpro.adsb

internal class AdsbProximityTierResolver(
    private val distanceTierStateByTargetId: MutableMap<Icao24, AdsbProximityTier>,
    private val resolvedTierStateByTargetId: MutableMap<Icao24, AdsbProximityTier>
) {
    fun resolve(
        targetId: Icao24,
        distanceMeters: Double,
        hasOwnshipReference: Boolean,
        isVerticalNonThreat: Boolean,
        hasFreshTrendSample: Boolean,
        showClosingAlert: Boolean,
        postPassDivergingSampleCount: Int,
        isCirclingEmergencyRedRule: Boolean,
        isEmergencyCollisionRisk: Boolean
    ): AdsbProximityTier {
        if (!hasOwnshipReference) {
            resolvedTierStateByTargetId.remove(targetId)
            return AdsbProximityTier.NEUTRAL
        }
        if (isVerticalNonThreat) {
            resolvedTierStateByTargetId[targetId] = AdsbProximityTier.GREEN
            return AdsbProximityTier.GREEN
        }
        if (isCirclingEmergencyRedRule) {
            resolvedTierStateByTargetId[targetId] = AdsbProximityTier.RED
            return AdsbProximityTier.RED
        }
        if (isEmergencyCollisionRisk) return AdsbProximityTier.EMERGENCY

        val distanceTier = distanceTierWithHysteresis(
            targetId = targetId,
            distanceMeters = distanceMeters,
            hasOwnshipReference = hasOwnshipReference
        )
        val candidateTier = when {
            showClosingAlert -> distanceTier
            !hasFreshTrendSample -> staleTrendTierCandidate(
                targetId = targetId,
                distanceTier = distanceTier
            )
            else -> freshTrendTierCandidate(
                distanceTier = distanceTier,
                postPassDivergingSampleCount = postPassDivergingSampleCount
            )
        }
        resolvedTierStateByTargetId[targetId] = candidateTier
        return candidateTier
    }

    private fun staleTrendTierCandidate(
        targetId: Icao24,
        distanceTier: AdsbProximityTier
    ): AdsbProximityTier {
        val previousTier = resolvedTierStateByTargetId[targetId] ?: return distanceTier
        return when {
            distanceTier == AdsbProximityTier.RED && previousTier != AdsbProximityTier.RED ->
                AdsbProximityTier.RED
            else -> previousTier
        }
    }

    private fun freshTrendTierCandidate(
        distanceTier: AdsbProximityTier,
        postPassDivergingSampleCount: Int
    ): AdsbProximityTier = when (distanceTier) {
        // RED traffic de-escalates in two fresh post-pass samples: RED -> AMBER -> GREEN.
        AdsbProximityTier.RED -> when {
            postPassDivergingSampleCount >= POST_PASS_RED_TO_GREEN_MIN_SAMPLES ->
                AdsbProximityTier.GREEN
            postPassDivergingSampleCount >= POST_PASS_RED_TO_AMBER_MIN_SAMPLES ->
                AdsbProximityTier.AMBER
            else -> AdsbProximityTier.RED
        }
        // AMBER de-escalation to GREEN requires evidence of a prior closing episode.
        AdsbProximityTier.AMBER -> when {
            postPassDivergingSampleCount >= POST_PASS_AMBER_TO_GREEN_MIN_SAMPLES ->
                AdsbProximityTier.GREEN
            else -> AdsbProximityTier.AMBER
        }
        AdsbProximityTier.GREEN -> AdsbProximityTier.GREEN
        else -> AdsbProximityTier.GREEN
    }

    private fun distanceTierWithHysteresis(
        targetId: Icao24,
        distanceMeters: Double,
        hasOwnshipReference: Boolean
    ): AdsbProximityTier {
        if (!hasOwnshipReference || !distanceMeters.isFinite()) {
            distanceTierStateByTargetId.remove(targetId)
            return AdsbProximityTier.GREEN
        }
        val previous = distanceTierStateByTargetId[targetId]
        val next = when (previous) {
            AdsbProximityTier.RED -> when {
                distanceMeters <= RED_EXIT_DISTANCE_METERS -> AdsbProximityTier.RED
                distanceMeters <= AMBER_EXIT_DISTANCE_METERS -> AdsbProximityTier.AMBER
                else -> AdsbProximityTier.GREEN
            }
            AdsbProximityTier.AMBER -> when {
                distanceMeters <= RED_ENTER_DISTANCE_METERS -> AdsbProximityTier.RED
                distanceMeters > AMBER_EXIT_DISTANCE_METERS -> AdsbProximityTier.GREEN
                else -> AdsbProximityTier.AMBER
            }
            else -> when {
                distanceMeters <= RED_ENTER_DISTANCE_METERS -> AdsbProximityTier.RED
                distanceMeters <= AMBER_ENTER_DISTANCE_METERS -> AdsbProximityTier.AMBER
                else -> AdsbProximityTier.GREEN
            }
        }
        distanceTierStateByTargetId[targetId] = next
        return next
    }

    private companion object {
        private const val RED_ENTER_DISTANCE_METERS = 2_000.0
        private const val RED_EXIT_DISTANCE_METERS = 2_200.0
        private const val AMBER_ENTER_DISTANCE_METERS = 5_000.0
        private const val AMBER_EXIT_DISTANCE_METERS = 5_300.0
        private const val POST_PASS_RED_TO_AMBER_MIN_SAMPLES = 1
        private const val POST_PASS_AMBER_TO_GREEN_MIN_SAMPLES = 1
        private const val POST_PASS_RED_TO_GREEN_MIN_SAMPLES = 2
    }
}
