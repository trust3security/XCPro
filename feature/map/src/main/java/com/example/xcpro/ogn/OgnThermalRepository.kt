package com.example.xcpro.ogn

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.core.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface OgnThermalRepository {
    val hotspots: StateFlow<List<OgnThermalHotspot>>
}

@Singleton
class OgnThermalRepositoryImpl @Inject constructor(
    private val ognTrafficRepository: OgnTrafficRepository,
    private val clock: Clock,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : OgnThermalRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val processingMutex = Mutex()

    private val trackerByTargetId = LinkedHashMap<String, ThermalTracker>()
    private val hotspotById = LinkedHashMap<String, OgnThermalHotspot>()
    private val segmentIndexByTargetId = HashMap<String, Int>()
    private val processedSourceSeenMonoByTargetId = HashMap<String, Long>()

    private var latestTargetsSnapshot: List<OgnTrafficTarget> = emptyList()
    private var latestStreamingEnabled: Boolean = false
    private var housekeepingJob: Job? = null

    private val _hotspots = MutableStateFlow<List<OgnThermalHotspot>>(emptyList())
    override val hotspots: StateFlow<List<OgnThermalHotspot>> = _hotspots.asStateFlow()

    init {
        scope.launch {
            combine(
                ognTrafficRepository.targets,
                ognTrafficRepository.isEnabled,
                ognTrafficRepository.suppressedTargetIds
            ) { targets, streamingEnabled, suppressedTargetKeys ->
                    ThermalInput(
                        targets = targets,
                        streamingEnabled = streamingEnabled,
                        suppressedTargetKeys = suppressedTargetKeys
                    )
            }.collect { input ->
                processInput(
                    targets = input.targets,
                    streamingEnabled = input.streamingEnabled,
                    suppressedTargetKeys = input.suppressedTargetKeys
                )
            }
        }
    }

    private suspend fun processInput(
        targets: List<OgnTrafficTarget>,
        streamingEnabled: Boolean,
        suppressedTargetKeys: Set<String>
    ) {
        processingMutex.withLock {
            latestTargetsSnapshot = targets
            latestStreamingEnabled = streamingEnabled
            val nowMonoMs = clock.nowMonoMs()
            processTargets(
                targets = targets,
                streamingEnabled = streamingEnabled,
                suppressedTargetKeys = suppressedTargetKeys,
                nowMonoMs = nowMonoMs
            )
            scheduleHousekeepingLocked(nowMonoMs)
        }
    }

    private fun processTargets(
        targets: List<OgnTrafficTarget>,
        streamingEnabled: Boolean,
        suppressedTargetKeys: Set<String>,
        nowMonoMs: Long
    ) {
        var changed = false
        changed = purgeSuppressedArtifacts(suppressedTargetKeys) || changed

        if (!streamingEnabled) {
            changed = finalizeAllTrackers(nowMonoMs) || changed
            if (changed) {
                publishHotspots()
            }
            return
        }

        for (target in targets) {
            changed = updateTracker(target, nowMonoMs) || changed
        }

        changed = finalizeInactiveTrackers(nowMonoMs) || changed
        pruneProcessedSourceSeenCache(targets, nowMonoMs)

        if (changed) {
            publishHotspots()
        }
    }

    private fun updateTracker(target: OgnTrafficTarget, nowMonoMs: Long): Boolean {
        if (!isValidThermalCoordinate(target.latitude, target.longitude)) return false
        val targetKey = normalizeOgnAircraftKeyOrNull(target.canonicalKey) ?: return false

        val climbRateMps = target.verticalSpeedMps?.takeIf { it.isFinite() }
        val altitudeMeters = target.altitudeMeters?.takeIf { it.isFinite() }
        val sourceSeenMonoMs = target.lastSeenMillis
        val previousSourceSeenMonoMs = processedSourceSeenMonoByTargetId[targetKey]
        val hasFreshSourceSample = previousSourceSeenMonoMs == null ||
            sourceSeenMonoMs > previousSourceSeenMonoMs

        var tracker = trackerByTargetId[targetKey]
        if (tracker == null) {
            if (!hasFreshSourceSample) return false
            if (!isEntrySample(climbRateMps)) return false
            val entryClimbRateMps = climbRateMps ?: return false
            tracker = ThermalTracker.create(
                sourceTargetId = targetKey,
                target = target,
                nowMonoMs = nowMonoMs,
                climbRateMps = entryClimbRateMps,
                altitudeMeters = altitudeMeters
            )
            trackerByTargetId[targetKey] = tracker
            processedSourceSeenMonoByTargetId[targetKey] = sourceSeenMonoMs
            return false
        }

        if (!hasFreshSourceSample) return false
        processedSourceSeenMonoByTargetId[targetKey] = sourceSeenMonoMs

        tracker.lastSeenMonoMs = nowMonoMs
        tracker.sourceLabel = target.displayLabel.ifBlank { target.callsign }
        tracker.addPositionSample(target.latitude, target.longitude)

        val hasClimbSample = climbRateMps != null
        if (hasClimbSample) {
            tracker.sampleCount += 1
            tracker.climbSumMps += climbRateMps
            tracker.maxClimbRateMps = max(tracker.maxClimbRateMps, climbRateMps)
            if (climbRateMps >= CONTINUE_CLIMB_THRESHOLD_MPS) {
                tracker.lastStrongClimbMonoMs = nowMonoMs
            }
        }

        if (altitudeMeters != null) {
            if (tracker.startAltitudeMeters == null) {
                tracker.startAltitudeMeters = altitudeMeters
            }
            if (tracker.maxAltitudeMeters == null || altitudeMeters > tracker.maxAltitudeMeters!!) {
                tracker.maxAltitudeMeters = altitudeMeters
                tracker.maxAltitudeAtMonoMs = nowMonoMs
            }
        }

        val outsideContinuityWindow =
            nowMonoMs - tracker.lastStrongClimbMonoMs >= THERMAL_CONTINUITY_GRACE_MS
        if (outsideContinuityWindow) {
            return finalizeTracker(targetId = targetKey, nowMonoMs = nowMonoMs)
        }

        if (!tracker.confirmed && qualifiesAsThermal(tracker, nowMonoMs)) {
            tracker.confirmed = true
            tracker.hotspotId = nextHotspotId(targetKey)
        }

        if (!tracker.confirmed) return false

        val hotspot = tracker.toHotspot(
            nowMonoMs = nowMonoMs,
            state = OgnThermalHotspotState.ACTIVE
        )
        val previous = hotspotById[hotspot.id]
        if (previous == hotspot) return false
        hotspotById[hotspot.id] = hotspot
        return true
    }

    private fun finalizeInactiveTrackers(nowMonoMs: Long): Boolean {
        var changed = false
        val iterator = trackerByTargetId.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val tracker = entry.value
            val continuityExpired =
                nowMonoMs - tracker.lastStrongClimbMonoMs >= THERMAL_CONTINUITY_GRACE_MS
            val missingExpired =
                nowMonoMs - tracker.lastSeenMonoMs >= TARGET_MISSING_TIMEOUT_MS
            if (!continuityExpired && !missingExpired) continue
            iterator.remove()
            if (!tracker.confirmed) continue
            val hotspot = tracker.toHotspot(
                nowMonoMs = nowMonoMs,
                state = OgnThermalHotspotState.FINALIZED
            )
            val previous = hotspotById[hotspot.id]
            if (previous != hotspot) {
                hotspotById[hotspot.id] = hotspot
                changed = true
            }
        }
        return changed
    }

    private fun purgeSuppressedArtifacts(suppressedTargetKeys: Set<String>): Boolean {
        if (suppressedTargetKeys.isEmpty()) return false
        var changed = false

        for (targetKey in suppressedTargetKeys) {
            if (trackerByTargetId.remove(targetKey) != null) {
                changed = true
            }
            if (processedSourceSeenMonoByTargetId.remove(targetKey) != null) {
                changed = true
            }
            if (segmentIndexByTargetId.remove(targetKey) != null) {
                changed = true
            }
        }

        val hotspotIterator = hotspotById.entries.iterator()
        while (hotspotIterator.hasNext()) {
            val entry = hotspotIterator.next()
            if (!suppressedTargetKeys.contains(entry.value.sourceTargetId)) continue
            hotspotIterator.remove()
            changed = true
        }

        return changed
    }

    private fun finalizeAllTrackers(nowMonoMs: Long): Boolean {
        if (trackerByTargetId.isEmpty()) return false
        var changed = false
        for ((_, tracker) in trackerByTargetId) {
            if (!tracker.confirmed) continue
            val hotspot = tracker.toHotspot(
                nowMonoMs = nowMonoMs,
                state = OgnThermalHotspotState.FINALIZED
            )
            val previous = hotspotById[hotspot.id]
            if (previous != hotspot) {
                hotspotById[hotspot.id] = hotspot
                changed = true
            }
        }
        trackerByTargetId.clear()
        processedSourceSeenMonoByTargetId.clear()
        return changed
    }

    private fun finalizeTracker(targetId: String, nowMonoMs: Long): Boolean {
        val tracker = trackerByTargetId.remove(targetId) ?: return false
        if (!tracker.confirmed) return false

        val hotspot = tracker.toHotspot(
            nowMonoMs = nowMonoMs,
            state = OgnThermalHotspotState.FINALIZED
        )
        val previous = hotspotById[hotspot.id]
        if (previous == hotspot) return false
        hotspotById[hotspot.id] = hotspot
        return true
    }

    private fun pruneProcessedSourceSeenCache(targets: List<OgnTrafficTarget>, nowMonoMs: Long) {
        if (processedSourceSeenMonoByTargetId.isEmpty()) return

        val activeTargetIds = HashSet<String>(targets.size)
        for (target in targets) {
            val key = normalizeOgnAircraftKeyOrNull(target.canonicalKey) ?: continue
            activeTargetIds += key
        }

        val iterator = processedSourceSeenMonoByTargetId.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (activeTargetIds.contains(entry.key)) continue
            val staleCacheEntry =
                nowMonoMs - entry.value > PROCESSED_SOURCE_SEEN_RETENTION_MS
            if (staleCacheEntry) {
                iterator.remove()
            }
        }
    }

    private fun scheduleHousekeepingLocked(nowMonoMs: Long) {
        val delayMs = nextHousekeepingDelayMs(nowMonoMs)
        if (delayMs == null) {
            housekeepingJob?.cancel()
            housekeepingJob = null
            return
        }

        housekeepingJob?.cancel()
        housekeepingJob = scope.launch {
            delay(delayMs)
            processingMutex.withLock {
                housekeepingJob = null
                val now = clock.nowMonoMs()
                processTargets(
                    targets = latestTargetsSnapshot,
                    streamingEnabled = latestStreamingEnabled,
                    suppressedTargetKeys = ognTrafficRepository.suppressedTargetIds.value,
                    nowMonoMs = now
                )
                scheduleHousekeepingLocked(now)
            }
        }
    }

    private fun nextHousekeepingDelayMs(nowMonoMs: Long): Long? {
        if (trackerByTargetId.isEmpty()) return null

        var nextDeadlineMonoMs = Long.MAX_VALUE
        for (tracker in trackerByTargetId.values) {
            val continuityDeadlineMonoMs = tracker.lastStrongClimbMonoMs + THERMAL_CONTINUITY_GRACE_MS
            val missingDeadlineMonoMs = tracker.lastSeenMonoMs + TARGET_MISSING_TIMEOUT_MS
            val trackerDeadlineMonoMs = min(continuityDeadlineMonoMs, missingDeadlineMonoMs)
            if (trackerDeadlineMonoMs < nextDeadlineMonoMs) {
                nextDeadlineMonoMs = trackerDeadlineMonoMs
            }
        }
        if (nextDeadlineMonoMs == Long.MAX_VALUE) return null
        return (nextDeadlineMonoMs - nowMonoMs).coerceAtLeast(0L)
    }

    private fun qualifiesAsThermal(tracker: ThermalTracker, nowMonoMs: Long): Boolean {
        val durationMs = nowMonoMs - tracker.startedAtMonoMs
        if (durationMs < MIN_CONFIRM_DURATION_MS) return false
        if (tracker.maxClimbRateMps < MIN_CONFIRM_PEAK_CLIMB_MPS) return false

        val startAltitudeMeters = tracker.startAltitudeMeters
        val maxAltitudeMeters = tracker.maxAltitudeMeters
        if (startAltitudeMeters != null && maxAltitudeMeters != null) {
            if (maxAltitudeMeters - startAltitudeMeters < MIN_CONFIRM_ALTITUDE_GAIN_METERS) {
                return false
            }
        }

        return tracker.sampleCount >= MIN_CONFIRM_SAMPLE_COUNT
    }

    private fun publishHotspots() {
        _hotspots.value = hotspotById.values
            .sortedByDescending { it.updatedAtMonoMs }
    }

    private fun isEntrySample(climbRateMps: Double?): Boolean {
        return climbRateMps != null && climbRateMps >= ENTRY_CLIMB_THRESHOLD_MPS
    }

    private fun nextHotspotId(targetId: String): String {
        val nextSegmentIndex = (segmentIndexByTargetId[targetId] ?: 0) + 1
        segmentIndexByTargetId[targetId] = nextSegmentIndex
        return "${targetId}-thermal-$nextSegmentIndex"
    }

    private data class ThermalInput(
        val targets: List<OgnTrafficTarget>,
        val streamingEnabled: Boolean,
        val suppressedTargetKeys: Set<String>
    )

    private data class ThermalTracker(
        val sourceTargetId: String,
        var sourceLabel: String,
        val startedAtMonoMs: Long,
        var lastSeenMonoMs: Long,
        var lastStrongClimbMonoMs: Long,
        var startAltitudeMeters: Double?,
        var maxAltitudeMeters: Double?,
        var maxAltitudeAtMonoMs: Long?,
        var sampleCount: Int,
        var climbSumMps: Double,
        var maxClimbRateMps: Double,
        var centroidLatitudeSum: Double,
        var centroidLongitudeSum: Double,
        var centroidSampleCount: Int,
        var confirmed: Boolean,
        var hotspotId: String?
    ) {
        val centroidLatitude: Double
            get() = centroidLatitudeSum / centroidSampleCount.toDouble()

        val centroidLongitude: Double
            get() = centroidLongitudeSum / centroidSampleCount.toDouble()

        val averageClimbRateMps: Double?
            get() = if (sampleCount > 0) climbSumMps / sampleCount.toDouble() else null

        val averageBottomToTopClimbRateMps: Double?
            get() {
                val startAltitude = startAltitudeMeters ?: return null
                val maxAltitude = maxAltitudeMeters ?: return null
                val maxAltitudeMs = maxAltitudeAtMonoMs ?: return null
                val elapsedSeconds = (maxAltitudeMs - startedAtMonoMs) / 1000.0
                if (!elapsedSeconds.isFinite() || elapsedSeconds <= 0.0) return null
                return (maxAltitude - startAltitude) / elapsedSeconds
            }

        fun addPositionSample(latitude: Double, longitude: Double) {
            centroidLatitudeSum += latitude
            centroidLongitudeSum += longitude
            centroidSampleCount += 1
        }

        fun toHotspot(
            nowMonoMs: Long,
            state: OgnThermalHotspotState
        ): OgnThermalHotspot {
            val stableId = hotspotId ?: error("Tracker hotspotId missing for confirmed thermal")
            return OgnThermalHotspot(
                id = stableId,
                sourceTargetId = sourceTargetId,
                sourceLabel = sourceLabel,
                latitude = centroidLatitude,
                longitude = centroidLongitude,
                startedAtMonoMs = startedAtMonoMs,
                updatedAtMonoMs = nowMonoMs,
                startAltitudeMeters = startAltitudeMeters,
                maxAltitudeMeters = maxAltitudeMeters,
                maxAltitudeAtMonoMs = maxAltitudeAtMonoMs,
                maxClimbRateMps = maxClimbRateMps,
                averageClimbRateMps = averageClimbRateMps,
                averageBottomToTopClimbRateMps = averageBottomToTopClimbRateMps,
                snailColorIndex = climbRateToSnailColorIndex(maxClimbRateMps),
                state = state
            )
        }

        companion object {
            fun create(
                sourceTargetId: String,
                target: OgnTrafficTarget,
                nowMonoMs: Long,
                climbRateMps: Double,
                altitudeMeters: Double?
            ): ThermalTracker {
                return ThermalTracker(
                    sourceTargetId = sourceTargetId,
                    sourceLabel = target.displayLabel.ifBlank { target.callsign },
                    startedAtMonoMs = nowMonoMs,
                    lastSeenMonoMs = nowMonoMs,
                    lastStrongClimbMonoMs = nowMonoMs,
                    startAltitudeMeters = altitudeMeters,
                    maxAltitudeMeters = altitudeMeters,
                    maxAltitudeAtMonoMs = altitudeMeters?.let { nowMonoMs },
                    sampleCount = 1,
                    climbSumMps = climbRateMps,
                    maxClimbRateMps = climbRateMps,
                    centroidLatitudeSum = target.latitude,
                    centroidLongitudeSum = target.longitude,
                    centroidSampleCount = 1,
                    confirmed = false,
                    hotspotId = null
                )
            }
        }
    }

    private companion object {
        private const val ENTRY_CLIMB_THRESHOLD_MPS = 0.3
        private const val CONTINUE_CLIMB_THRESHOLD_MPS = 0.15
        private const val MIN_CONFIRM_PEAK_CLIMB_MPS = 0.5
        private const val MIN_CONFIRM_DURATION_MS = 25_000L
        private const val MIN_CONFIRM_SAMPLE_COUNT = 4
        private const val MIN_CONFIRM_ALTITUDE_GAIN_METERS = 35.0
        private const val THERMAL_CONTINUITY_GRACE_MS = 20_000L
        private const val TARGET_MISSING_TIMEOUT_MS = 45_000L
        private const val PROCESSED_SOURCE_SEEN_RETENTION_MS = TARGET_MISSING_TIMEOUT_MS
    }
}
