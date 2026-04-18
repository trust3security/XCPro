package com.trust3.xcpro.map

data class AdsbTrafficOverlayDiagnosticsSnapshot(
    val animationFrameScheduledCount: Long = 0L,
    val animationFrameRenderedCount: Long = 0L,
    val animationFrameSkippedCount: Long = 0L,
    val activeAnimatedTargetCount: Int = 0,
    val emergencyAnimatedTargetCount: Int = 0,
    val interactionReducedMotionActive: Boolean = false
)

internal class AdsbTrafficOverlayDiagnosticsState {
    private var animationFrameScheduledCount: Long = 0L
    private var animationFrameRenderedCount: Long = 0L
    private var animationFrameSkippedCount: Long = 0L
    private var activeAnimatedTargetCount: Int = 0
    private var emergencyAnimatedTargetCount: Int = 0
    private var interactionReducedMotionActive: Boolean = false

    fun setInteractionReducedMotionActive(active: Boolean) {
        interactionReducedMotionActive = active
    }

    fun recordAnimationFrameScheduled() {
        animationFrameScheduledCount += 1L
    }

    fun recordAnimationFrameRendered(
        activeAnimatedTargetCount: Int,
        emergencyAnimatedTargetCount: Int
    ) {
        animationFrameRenderedCount += 1L
        this.activeAnimatedTargetCount = activeAnimatedTargetCount
        this.emergencyAnimatedTargetCount = emergencyAnimatedTargetCount
    }

    fun recordAnimationFrameSkipped(
        activeAnimatedTargetCount: Int,
        emergencyAnimatedTargetCount: Int
    ) {
        animationFrameSkippedCount += 1L
        this.activeAnimatedTargetCount = activeAnimatedTargetCount
        this.emergencyAnimatedTargetCount = emergencyAnimatedTargetCount
    }

    fun snapshot(): AdsbTrafficOverlayDiagnosticsSnapshot = AdsbTrafficOverlayDiagnosticsSnapshot(
        animationFrameScheduledCount = animationFrameScheduledCount,
        animationFrameRenderedCount = animationFrameRenderedCount,
        animationFrameSkippedCount = animationFrameSkippedCount,
        activeAnimatedTargetCount = activeAnimatedTargetCount,
        emergencyAnimatedTargetCount = emergencyAnimatedTargetCount,
        interactionReducedMotionActive = interactionReducedMotionActive
    )
}
