package com.example.xcpro.ogn

import java.time.ZoneId
import kotlin.math.min

internal fun computeNextThermalHousekeepingDelayMs(
    nowMonoMs: Long,
    nowWallMs: Long,
    retentionHours: Int,
    trackers: Collection<ThermalTracker>,
    hotspots: Collection<OgnThermalHotspot>,
    localZoneId: ZoneId,
    thermalContinuityGraceMs: Long,
    targetMissingTimeoutMs: Long,
    millisPerHour: Long
): Long? {
    val trackerDelayMs = nextTrackerHousekeepingDelayMs(
        nowMonoMs = nowMonoMs,
        trackers = trackers,
        thermalContinuityGraceMs = thermalContinuityGraceMs,
        targetMissingTimeoutMs = targetMissingTimeoutMs
    )
    val retentionDelayMs = nextRetentionHousekeepingDelayMs(
        nowWallMs = nowWallMs,
        retentionHours = retentionHours,
        hotspots = hotspots,
        localZoneId = localZoneId,
        millisPerHour = millisPerHour
    )
    return when {
        trackerDelayMs == null -> retentionDelayMs
        retentionDelayMs == null -> trackerDelayMs
        else -> min(trackerDelayMs, retentionDelayMs)
    }
}

private fun nextTrackerHousekeepingDelayMs(
    nowMonoMs: Long,
    trackers: Collection<ThermalTracker>,
    thermalContinuityGraceMs: Long,
    targetMissingTimeoutMs: Long
): Long? {
    if (trackers.isEmpty()) return null
    var nextDeadlineMonoMs = Long.MAX_VALUE
    for (tracker in trackers) {
        val continuityDeadlineMonoMs = tracker.lastStrongClimbMonoMs + thermalContinuityGraceMs
        val missingDeadlineMonoMs = tracker.lastSeenMonoMs + targetMissingTimeoutMs
        val trackerDeadlineMonoMs = min(continuityDeadlineMonoMs, missingDeadlineMonoMs)
        if (trackerDeadlineMonoMs < nextDeadlineMonoMs) {
            nextDeadlineMonoMs = trackerDeadlineMonoMs
        }
    }
    if (nextDeadlineMonoMs == Long.MAX_VALUE) return null
    return (nextDeadlineMonoMs - nowMonoMs).coerceAtLeast(0L)
}

private fun nextRetentionHousekeepingDelayMs(
    nowWallMs: Long,
    retentionHours: Int,
    hotspots: Collection<OgnThermalHotspot>,
    localZoneId: ZoneId,
    millisPerHour: Long
): Long? {
    if (hotspots.isEmpty()) return null
    val clamped = clampOgnThermalRetentionHours(retentionHours)
    if (isOgnThermalRetentionAllDay(clamped)) {
        val nextMidnightWallMs = startOfNextLocalDayWallMsForZone(nowWallMs, localZoneId)
        return (nextMidnightWallMs - nowWallMs).coerceAtLeast(0L)
    }
    val retentionWindowMs = clamped.toLong() * millisPerHour
    var nextExpiryWallMs = Long.MAX_VALUE
    for (hotspot in hotspots) {
        val expiryWallMs = hotspot.updatedAtWallMs + retentionWindowMs + 1L
        if (expiryWallMs < nextExpiryWallMs) {
            nextExpiryWallMs = expiryWallMs
        }
    }
    if (nextExpiryWallMs == Long.MAX_VALUE) return null
    return (nextExpiryWallMs - nowWallMs).coerceAtLeast(0L)
}
