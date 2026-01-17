package com.example.xcpro.tasks.racing.navigation

enum class RacingNavigationStatus {
    PENDING_START,
    STARTED,
    IN_PROGRESS,
    FINISHED,
    INVALIDATED
}

data class RacingNavigationFix(
    val lat: Double,
    val lon: Double,
    val timestampMillis: Long,
    val accuracyMeters: Double? = null
)

data class RacingNavigationState(
    val status: RacingNavigationStatus = RacingNavigationStatus.PENDING_START,
    val currentLegIndex: Int = 0,
    val lastFix: RacingNavigationFix? = null,
    val lastTransitionTimeMillis: Long = 0L,
    val taskSignature: String = ""
)

data class RacingNavigationDecision(
    val state: RacingNavigationState,
    val event: RacingNavigationEvent?
)
