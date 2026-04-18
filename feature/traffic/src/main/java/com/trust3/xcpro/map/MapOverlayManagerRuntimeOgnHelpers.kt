package com.trust3.xcpro.map

data class OgnOverlayStatusSnapshot(
    val displayUpdateMode: OgnDisplayUpdateMode,
    val targetsCount: Int,
    val thermalHotspotsCount: Int,
    val gliderTrailSegmentsCount: Int,
    val targetEnabled: Boolean,
    val targetResolved: Boolean
)

internal data class OgnRenderThrottleState(
    var lastRenderMonoMs: Long = 0L,
    var pendingJob: kotlinx.coroutines.Job? = null,
    var pendingDueMonoMs: Long = Long.MAX_VALUE
)

internal fun gliderTrailSegmentIdentitySignature(segments: List<OgnGliderTrailSegment>): Int {
    var hash = 1
    for (segment in segments) {
        hash = 31 * hash + segment.id.hashCode()
    }
    return hash
}

internal fun sameGliderTrailSegmentsByIdentity(
    previous: List<OgnGliderTrailSegment>,
    current: List<OgnGliderTrailSegment>
): Boolean {
    if (previous === current) return true
    if (previous.size != current.size) return false
    for (index in previous.indices) {
        if (previous[index].id != current[index].id) {
            return false
        }
    }
    return true
}
