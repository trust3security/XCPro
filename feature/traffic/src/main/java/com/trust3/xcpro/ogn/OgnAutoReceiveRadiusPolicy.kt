package com.trust3.xcpro.ogn

internal data class OgnAutoReceiveRadiusContext(
    val zoomLevel: Float,
    val groundSpeedMs: Double,
    val isFlying: Boolean
)

internal object OgnAutoReceiveRadiusPolicy {
    val radiusBucketsKm: List<Int> = listOf(40, 80, 150, 220)
    const val STABLE_DURATION_MS: Long = 30_000L
    const val MIN_APPLY_INTERVAL_MS: Long = 60_000L

    private const val SPEED_CRUISE_THRESHOLD_MPS: Double = 15.0
    private const val SPEED_FAST_THRESHOLD_MPS: Double = 35.0
    private const val ZOOM_WIDE_THRESHOLD: Float = 7.0f
    private const val ZOOM_TIGHT_THRESHOLD: Float = 11.0f

    fun resolveRadiusKm(context: OgnAutoReceiveRadiusContext): Int {
        val baseIndex = when {
            !context.isFlying -> 0
            context.groundSpeedMs >= SPEED_FAST_THRESHOLD_MPS -> 3
            context.groundSpeedMs >= SPEED_CRUISE_THRESHOLD_MPS -> 2
            else -> 1
        }
        val zoomAdjustment = when {
            context.zoomLevel <= ZOOM_WIDE_THRESHOLD -> 1
            context.zoomLevel >= ZOOM_TIGHT_THRESHOLD -> -1
            else -> 0
        }
        val index = (baseIndex + zoomAdjustment).coerceIn(0, radiusBucketsKm.lastIndex)
        return radiusBucketsKm[index]
    }
}
