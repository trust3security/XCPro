package com.trust3.xcpro.adsb

internal fun selectEmergencyAudioCandidate(
    targets: List<AdsbTrafficUiModel>
): AdsbTrafficUiModel? {
    var best: AdsbTrafficUiModel? = null
    for (target in targets) {
        if (!target.isEmergencyAudioEligible) continue
        val currentBest = best
        if (currentBest == null || compareEmergencyCandidate(target, currentBest) < 0) {
            best = target
        }
    }
    return best
}

private fun compareEmergencyCandidate(
    candidate: AdsbTrafficUiModel,
    incumbent: AdsbTrafficUiModel
): Int {
    if (candidate.isEmergencyCollisionRisk != incumbent.isEmergencyCollisionRisk) {
        return if (candidate.isEmergencyCollisionRisk) -1 else 1
    }
    if (candidate.isCirclingEmergencyRedRule != incumbent.isCirclingEmergencyRedRule) {
        return if (candidate.isCirclingEmergencyRedRule) -1 else 1
    }
    val distanceCompare = candidate.distanceMeters.compareTo(incumbent.distanceMeters)
    if (distanceCompare != 0) return distanceCompare
    val ageCompare = candidate.ageSec.compareTo(incumbent.ageSec)
    if (ageCompare != 0) return ageCompare
    return candidate.id.raw.compareTo(incumbent.id.raw)
}

internal val ADSB_DISPLAY_PRIORITY_COMPARATOR =
    compareByDescending<AdsbTrafficUiModel> { it.isEmergencyCollisionRisk }
        .thenBy { it.distanceMeters }
        .thenBy { it.ageSec }
        .thenBy { it.id.raw }
