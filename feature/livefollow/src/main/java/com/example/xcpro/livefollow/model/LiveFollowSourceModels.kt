package com.example.xcpro.livefollow.model

enum class LiveFollowSourceType {
    OGN,
    DIRECT
}

enum class LiveFollowSourceState {
    VALID,
    INVALID,
    UNAVAILABLE
}

enum class LiveFollowConfidence {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}

data class LiveFollowSourceSample(
    val source: LiveFollowSourceType,
    val state: LiveFollowSourceState,
    val confidence: LiveFollowConfidence,
    val fixMonoMs: Long?,
    val identityResolution: LiveFollowIdentityResolution? = null,
    val sessionAuthorized: Boolean = true
) {
    fun ageMs(nowMonoMs: Long): Long? = fixMonoMs?.let { sampleMonoMs ->
        (nowMonoMs - sampleMonoMs).coerceAtLeast(0L)
    }
}

data class LiveFollowSourceArbitrationPolicy(
    val freshnessTimeoutMs: Long = 15_000L,
    val minSwitchDwellMs: Long = 5_000L
) {
    init {
        require(freshnessTimeoutMs > 0L) { "freshnessTimeoutMs must be > 0" }
        require(minSwitchDwellMs >= 0L) { "minSwitchDwellMs must be >= 0" }
    }
}

enum class LiveFollowSourceEligibility {
    SELECTABLE,
    INVALID,
    STALE,
    UNAUTHORIZED,
    UNRESOLVED_IDENTITY,
    MISSING_FIX,
    UNAVAILABLE
}

enum class LiveFollowSourceSelectionReason {
    SELECTED_OGN,
    SELECTED_DIRECT,
    FALLBACK_TO_DIRECT,
    FALLBACK_TO_OGN,
    HOLDING_DIRECT_DWELL,
    NO_USABLE_SOURCE
}

data class LiveFollowSourceArbitrationDecision(
    val selectedSource: LiveFollowSourceType?,
    val selectedSample: LiveFollowSourceSample?,
    val reason: LiveFollowSourceSelectionReason,
    val switched: Boolean,
    val lastSwitchMonoMs: Long?,
    val ognEligibility: LiveFollowSourceEligibility,
    val directEligibility: LiveFollowSourceEligibility
)
