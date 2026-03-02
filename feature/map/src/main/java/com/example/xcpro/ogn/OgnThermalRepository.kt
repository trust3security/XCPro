package com.example.xcpro.ogn

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.core.time.Clock
import com.example.xcpro.di.OgnHotspotsDisplayPercentFlow
import com.example.xcpro.di.OgnThermalRetentionHoursFlow
import com.example.xcpro.di.OgnThermalZoneId
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    @OgnThermalRetentionHoursFlow private val thermalRetentionHoursFlow: Flow<Int>,
    @OgnHotspotsDisplayPercentFlow private val hotspotsDisplayPercentFlow: Flow<Int> =
        flowOf(OGN_HOTSPOTS_DISPLAY_PERCENT_DEFAULT),
    @OgnThermalZoneId private val localZoneId: ZoneId,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : OgnThermalRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val processingMutex = Mutex()

    private val trackerByTargetId = LinkedHashMap<String, ThermalTracker>()
    private val hotspotById = LinkedHashMap<String, OgnThermalHotspot>()
    private val segmentIndexByTargetId = HashMap<String, Int>()
    private val processedSourceStateByTargetId = HashMap<String, ProcessedSourceState>()

    private var latestTargetsSnapshot: List<OgnTrafficTarget> = emptyList()
    private var latestStreamingEnabled: Boolean = false
    private var latestRetentionHours: Int = OGN_THERMAL_RETENTION_DEFAULT_HOURS
    private var latestDisplayPercent: Int = OGN_HOTSPOTS_DISPLAY_PERCENT_DEFAULT
    private var latestSuppressedTargetKeys: Set<String> = emptySet()
    private var housekeepingJob: Job? = null
    private var lastHousekeepingMonoMs: Long? = null
    private var lastHousekeepingWallMs: Long? = null

    private val _hotspots = MutableStateFlow<List<OgnThermalHotspot>>(emptyList())
    override val hotspots: StateFlow<List<OgnThermalHotspot>> = _hotspots.asStateFlow()

    init {
        scope.launch {
            combine(
                ognTrafficRepository.targets,
                ognTrafficRepository.isEnabled,
                ognTrafficRepository.suppressedTargetIds,
                thermalRetentionHoursFlow,
                hotspotsDisplayPercentFlow
            ) { targets, streamingEnabled, suppressedTargetKeys, retentionHours, displayPercent ->
                    ThermalInput(
                        targets = targets,
                        streamingEnabled = streamingEnabled,
                        suppressedTargetKeys = suppressedTargetKeys,
                        retentionHours = retentionHours,
                        displayPercent = displayPercent
                    )
            }.collect { input ->
                processInput(
                    targets = input.targets,
                    streamingEnabled = input.streamingEnabled,
                    suppressedTargetKeys = input.suppressedTargetKeys,
                    retentionHours = input.retentionHours,
                    displayPercent = input.displayPercent
                )
            }
        }
    }

    private suspend fun processInput(
        targets: List<OgnTrafficTarget>,
        streamingEnabled: Boolean,
        suppressedTargetKeys: Set<String>,
        retentionHours: Int,
        displayPercent: Int
    ) {
        processingMutex.withLock {
            val normalizedRetentionHours = clampOgnThermalRetentionHours(retentionHours)
            val normalizedDisplayPercent = clampOgnHotspotsDisplayPercent(displayPercent)
            val suppressionChanged = latestSuppressedTargetKeys != suppressedTargetKeys
            val retentionChanged = latestRetentionHours != normalizedRetentionHours
            val displayChanged = latestDisplayPercent != normalizedDisplayPercent
            val hasFreshSourceSamples = hasFreshSourceSamples(targets)

            latestTargetsSnapshot = targets
            latestStreamingEnabled = streamingEnabled
            latestRetentionHours = normalizedRetentionHours
            latestDisplayPercent = normalizedDisplayPercent
            latestSuppressedTargetKeys = suppressedTargetKeys
            val nowMonoMs = clock.nowMonoMs()
            val nowWallMs = clock.nowWallMs()
            if (
                streamingEnabled &&
                !hasFreshSourceSamples &&
                !suppressionChanged &&
                !retentionChanged &&
                !displayChanged
            ) {
                scheduleHousekeepingLocked(nowMonoMs, nowWallMs)
                return@withLock
            }
            processTargets(
                targets = targets,
                streamingEnabled = streamingEnabled,
                suppressedTargetKeys = suppressedTargetKeys,
                nowMonoMs = nowMonoMs,
                nowWallMs = nowWallMs,
                retentionHours = latestRetentionHours,
                displayPercent = latestDisplayPercent
            )
            scheduleHousekeepingLocked(nowMonoMs, nowWallMs)
        }
    }

    private fun hasFreshSourceSamples(targets: List<OgnTrafficTarget>): Boolean {
        if (targets.isEmpty()) return false
        for (target in targets) {
            val targetKey = normalizeOgnAircraftKeyOrNull(target.canonicalKey) ?: continue
            val sourceSeenMonoMs = target.lastSeenMillis
            val previousSourceState = processedSourceStateByTargetId[targetKey]
            val hasFreshSourceSample = previousSourceState == null ||
                sourceSeenMonoMs > previousSourceState.lastSourceSeenMonoMs ||
                sourceSeenMonoMs + SOURCE_SEEN_MONO_RESET_THRESHOLD_MS <
                previousSourceState.lastSourceSeenMonoMs
            if (hasFreshSourceSample) {
                return true
            }
        }
        return false
    }

    private fun processTargets(
        targets: List<OgnTrafficTarget>,
        streamingEnabled: Boolean,
        suppressedTargetKeys: Set<String>,
        nowMonoMs: Long,
        nowWallMs: Long,
        retentionHours: Int,
        displayPercent: Int
    ) {
        purgeSuppressedArtifacts(suppressedTargetKeys)

        if (!streamingEnabled) {
            finalizeAllTrackers(nowMonoMs, nowWallMs)
            pruneHotspotsForRetention(nowWallMs, retentionHours)
            pruneSegmentIndexCache()
            publishHotspots(
                displayPercent = displayPercent,
                nowMonoMs = nowMonoMs
            )
            return
        }

        for (target in targets) {
            updateTracker(target, nowMonoMs, nowWallMs)
        }

        finalizeInactiveTrackers(nowMonoMs, nowWallMs)
        pruneProcessedSourceSeenCache(targets, nowMonoMs)
        pruneHotspotsForRetention(nowWallMs, retentionHours)
        pruneSegmentIndexCache()
        publishHotspots(
            displayPercent = displayPercent,
            nowMonoMs = nowMonoMs
        )
    }

    private fun updateTracker(target: OgnTrafficTarget, nowMonoMs: Long, nowWallMs: Long): Boolean {
        if (!isValidThermalCoordinate(target.latitude, target.longitude)) return false
        val targetKey = normalizeOgnAircraftKeyOrNull(target.canonicalKey) ?: return false

        val climbRateMps = target.verticalSpeedMps?.takeIf { it.isFinite() }
        val altitudeMeters = target.altitudeMeters?.takeIf { it.isFinite() }
        val sourceSeenMonoMs = target.lastSeenMillis
        val previousSourceState = processedSourceStateByTargetId[targetKey]
        val hasFreshSourceSample = previousSourceState == null ||
            sourceSeenMonoMs > previousSourceState.lastSourceSeenMonoMs ||
            sourceSeenMonoMs + SOURCE_SEEN_MONO_RESET_THRESHOLD_MS <
            previousSourceState.lastSourceSeenMonoMs

        var tracker = trackerByTargetId[targetKey]
        if (tracker == null) {
            if (!hasFreshSourceSample) return false
            if (!isEntrySample(climbRateMps)) return false
            val entryClimbRateMps = climbRateMps ?: return false
            tracker = ThermalTracker.create(
                sourceTargetId = targetKey,
                target = target,
                nowMonoMs = nowMonoMs,
                nowWallMs = nowWallMs,
                climbRateMps = entryClimbRateMps,
                altitudeMeters = altitudeMeters
            )
            trackerByTargetId[targetKey] = tracker
            processedSourceStateByTargetId[targetKey] = ProcessedSourceState(
                lastSourceSeenMonoMs = sourceSeenMonoMs,
                lastProcessedMonoMs = nowMonoMs
            )
            return false
        }

        if (!hasFreshSourceSample) return false
        processedSourceStateByTargetId[targetKey] = ProcessedSourceState(
            lastSourceSeenMonoMs = sourceSeenMonoMs,
            lastProcessedMonoMs = nowMonoMs
        )

        tracker.lastSeenMonoMs = nowMonoMs
        tracker.sourceLabel = target.displayLabel.ifBlank { target.callsign }
        tracker.addPositionSample(target.latitude, target.longitude)
        tracker.addTrackSample(target.trackDegrees)

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
            return finalizeTracker(targetId = targetKey, nowMonoMs = nowMonoMs, nowWallMs = nowWallMs)
        }

        if (!tracker.confirmed && qualifiesAsThermal(tracker, nowMonoMs)) {
            tracker.confirmed = true
            tracker.hotspotId = nextHotspotId(targetKey)
        }

        if (!tracker.confirmed) return false

        val hotspot = tracker.toHotspot(
            stableId = ensureTrackerHotspotId(tracker),
            nowMonoMs = nowMonoMs,
            nowWallMs = nowWallMs,
            state = OgnThermalHotspotState.ACTIVE
        )
        val previous = hotspotById[hotspot.id]
        if (previous == hotspot) return false
        hotspotById[hotspot.id] = hotspot
        return true
    }

    private fun finalizeInactiveTrackers(nowMonoMs: Long, nowWallMs: Long): Boolean {
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
                stableId = ensureTrackerHotspotId(tracker),
                nowMonoMs = nowMonoMs,
                nowWallMs = nowWallMs,
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
            if (processedSourceStateByTargetId.remove(targetKey) != null) {
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

    private fun finalizeAllTrackers(nowMonoMs: Long, nowWallMs: Long): Boolean {
        if (trackerByTargetId.isEmpty()) return false
        var changed = false
        for ((_, tracker) in trackerByTargetId) {
            if (!tracker.confirmed) continue
            val hotspot = tracker.toHotspot(
                stableId = ensureTrackerHotspotId(tracker),
                nowMonoMs = nowMonoMs,
                nowWallMs = nowWallMs,
                state = OgnThermalHotspotState.FINALIZED
            )
            val previous = hotspotById[hotspot.id]
            if (previous != hotspot) {
                hotspotById[hotspot.id] = hotspot
                changed = true
            }
        }
        trackerByTargetId.clear()
        processedSourceStateByTargetId.clear()
        return changed
    }

    private fun finalizeTracker(targetId: String, nowMonoMs: Long, nowWallMs: Long): Boolean {
        val tracker = trackerByTargetId.remove(targetId) ?: return false
        if (!tracker.confirmed) return false

        val hotspot = tracker.toHotspot(
            stableId = ensureTrackerHotspotId(tracker),
            nowMonoMs = nowMonoMs,
            nowWallMs = nowWallMs,
            state = OgnThermalHotspotState.FINALIZED
        )
        val previous = hotspotById[hotspot.id]
        if (previous == hotspot) return false
        hotspotById[hotspot.id] = hotspot
        return true
    }

    private fun pruneProcessedSourceSeenCache(targets: List<OgnTrafficTarget>, nowMonoMs: Long) {
        if (processedSourceStateByTargetId.isEmpty()) return

        val activeTargetIds = HashSet<String>(targets.size)
        for (target in targets) {
            val key = normalizeOgnAircraftKeyOrNull(target.canonicalKey) ?: continue
            activeTargetIds += key
        }

        val iterator = processedSourceStateByTargetId.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (activeTargetIds.contains(entry.key)) continue
            val staleCacheEntry =
                nowMonoMs - entry.value.lastProcessedMonoMs > PROCESSED_SOURCE_SEEN_RETENTION_MS
            if (staleCacheEntry) {
                iterator.remove()
            }
        }
    }

    private fun pruneHotspotsForRetention(nowWallMs: Long, retentionHours: Int): Boolean {
        if (hotspotById.isEmpty()) return false
        val cutoffWallMs = retentionCutoffWallMs(
            nowWallMs = nowWallMs,
            retentionHours = retentionHours
        )
        var changed = false
        val iterator = hotspotById.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.updatedAtWallMs < cutoffWallMs) {
                iterator.remove()
                changed = true
            }
        }
        return changed
    }

    private fun retentionCutoffWallMs(nowWallMs: Long, retentionHours: Int): Long {
        val clamped = clampOgnThermalRetentionHours(retentionHours)
        return if (isOgnThermalRetentionAllDay(clamped)) {
            startOfLocalDayWallMs(nowWallMs)
        } else {
            nowWallMs - clamped.toLong() * MILLIS_PER_HOUR
        }
    }

    private fun scheduleHousekeepingLocked(nowMonoMs: Long, nowWallMs: Long) {
        val delayMs = nextHousekeepingDelayMs(nowMonoMs, nowWallMs, latestRetentionHours)
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
                val nowMono = clock.nowMonoMs()
                val nowWall = clock.nowWallMs()
                val previousHousekeepingMono = lastHousekeepingMonoMs
                val previousHousekeepingWall = lastHousekeepingWallMs
                if (previousHousekeepingMono != null &&
                    previousHousekeepingWall != null &&
                    nowMono <= previousHousekeepingMono &&
                    nowWall <= previousHousekeepingWall
                ) {
                    // Guard against virtual-delay spin when injected clock time does not advance.
                    return@withLock
                }
                lastHousekeepingMonoMs = nowMono
                lastHousekeepingWallMs = nowWall
                processTargets(
                    targets = latestTargetsSnapshot,
                    streamingEnabled = latestStreamingEnabled,
                    suppressedTargetKeys = ognTrafficRepository.suppressedTargetIds.value,
                    nowMonoMs = nowMono,
                    nowWallMs = nowWall,
                    retentionHours = latestRetentionHours,
                    displayPercent = latestDisplayPercent
                )
                scheduleHousekeepingLocked(nowMono, nowWall)
            }
        }
    }

    private fun nextHousekeepingDelayMs(
        nowMonoMs: Long,
        nowWallMs: Long,
        retentionHours: Int
    ): Long? {
        val trackerDelayMs = nextTrackerHousekeepingDelayMs(nowMonoMs)
        val retentionDelayMs = nextRetentionHousekeepingDelayMs(nowWallMs, retentionHours)
        return when {
            trackerDelayMs == null -> retentionDelayMs
            retentionDelayMs == null -> trackerDelayMs
            else -> min(trackerDelayMs, retentionDelayMs)
        }
    }

    private fun nextTrackerHousekeepingDelayMs(nowMonoMs: Long): Long? {
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

    private fun nextRetentionHousekeepingDelayMs(nowWallMs: Long, retentionHours: Int): Long? {
        if (hotspotById.isEmpty()) return null
        val clamped = clampOgnThermalRetentionHours(retentionHours)
        if (isOgnThermalRetentionAllDay(clamped)) {
            val nextMidnightWallMs = startOfNextLocalDayWallMs(nowWallMs)
            return (nextMidnightWallMs - nowWallMs).coerceAtLeast(0L)
        }
        val retentionWindowMs = clamped.toLong() * MILLIS_PER_HOUR
        var nextExpiryWallMs = Long.MAX_VALUE
        for (hotspot in hotspotById.values) {
            val expiryWallMs = hotspot.updatedAtWallMs + retentionWindowMs + 1L
            if (expiryWallMs < nextExpiryWallMs) {
                nextExpiryWallMs = expiryWallMs
            }
        }
        if (nextExpiryWallMs == Long.MAX_VALUE) return null
        return (nextExpiryWallMs - nowWallMs).coerceAtLeast(0L)
    }

    private fun qualifiesAsThermal(tracker: ThermalTracker, nowMonoMs: Long): Boolean {
        val durationMs = nowMonoMs - tracker.startedAtMonoMs
        if (durationMs < MIN_CONFIRM_DURATION_MS) return false
        if (tracker.maxClimbRateMps < MIN_CONFIRM_PEAK_CLIMB_MPS) return false
        if (tracker.accumulatedTurnDegrees <= MIN_CONFIRM_CUMULATIVE_TURN_DEGREES) return false

        val startAltitudeMeters = tracker.startAltitudeMeters
        val maxAltitudeMeters = tracker.maxAltitudeMeters
        if (startAltitudeMeters != null && maxAltitudeMeters != null) {
            if (maxAltitudeMeters - startAltitudeMeters < MIN_CONFIRM_ALTITUDE_GAIN_METERS) {
                return false
            }
        }

        return tracker.sampleCount >= MIN_CONFIRM_SAMPLE_COUNT
    }

    private fun publishHotspots(displayPercent: Int, nowMonoMs: Long) {
        val hotspotsByPriority = hotspotById.values.sortedWith { first, second ->
            val candidateComparison = compareAreaWinnerPriority(
                candidate = first,
                current = second,
                nowMonoMs = nowMonoMs
            )
            when {
                candidateComparison > 0 -> -1
                candidateComparison < 0 -> 1
                else -> 0
            }
        }
        val dedupedHotspots = ArrayList<OgnThermalHotspot>(hotspotsByPriority.size)
        for (hotspot in hotspotsByPriority) {
            val overlapsExistingArea = dedupedHotspots.any { existing ->
                distanceMeters(
                    latitudeA = hotspot.latitude,
                    longitudeA = hotspot.longitude,
                    latitudeB = existing.latitude,
                    longitudeB = existing.longitude
                ) < AREA_DEDUP_RADIUS_METERS
            }
            if (!overlapsExistingArea) {
                dedupedHotspots += hotspot
            }
        }
        val clampedPercent = clampOgnHotspotsDisplayPercent(displayPercent)
        val keepCount = if (dedupedHotspots.isEmpty()) {
            0
        } else {
            max(1, ceil(dedupedHotspots.size * (clampedPercent / 100.0)).toInt())
        }
        val nextHotspots = dedupedHotspots
            .take(keepCount)
            .sortedByDescending { it.updatedAtMonoMs }
        if (_hotspots.value != nextHotspots) {
            _hotspots.value = nextHotspots
        }
    }

    private fun compareAreaWinnerPriority(
        candidate: OgnThermalHotspot,
        current: OgnThermalHotspot,
        nowMonoMs: Long
    ): Int {
        val candidateActive = candidate.state == OgnThermalHotspotState.ACTIVE
        val currentActive = current.state == OgnThermalHotspotState.ACTIVE
        if (candidateActive != currentActive) {
            return if (candidateActive) 1 else -1
        }

        val candidateRecent = isRecentAreaWinnerCandidate(candidate, nowMonoMs)
        val currentRecent = isRecentAreaWinnerCandidate(current, nowMonoMs)
        if (candidateRecent != currentRecent) {
            return if (candidateRecent) 1 else -1
        }

        val strengthComparison = compareHotspotStrength(candidate, current)
        if (strengthComparison != 0) return strengthComparison

        return current.id.compareTo(candidate.id)
    }

    private fun isRecentAreaWinnerCandidate(hotspot: OgnThermalHotspot, nowMonoMs: Long): Boolean {
        val ageMs = (nowMonoMs - hotspot.updatedAtMonoMs).coerceAtLeast(0L)
        return ageMs <= AREA_WINNER_RECENT_WINDOW_MS
    }

    private fun isEntrySample(climbRateMps: Double?): Boolean {
        return climbRateMps != null && climbRateMps >= ENTRY_CLIMB_THRESHOLD_MPS
    }

    private fun nextHotspotId(targetId: String): String {
        val nextSegmentIndex = (segmentIndexByTargetId[targetId] ?: 0) + 1
        segmentIndexByTargetId[targetId] = nextSegmentIndex
        return "${targetId}-thermal-$nextSegmentIndex"
    }

    private fun ensureTrackerHotspotId(tracker: ThermalTracker): String {
        val existing = tracker.hotspotId
        if (!existing.isNullOrBlank()) return existing
        val recovered = nextHotspotId(tracker.sourceTargetId)
        tracker.hotspotId = recovered
        return recovered
    }

    private fun compareHotspotStrength(
        candidate: OgnThermalHotspot,
        current: OgnThermalHotspot
    ): Int {
        val maxClimbComparison = candidate.maxClimbRateMps.compareTo(current.maxClimbRateMps)
        if (maxClimbComparison != 0) return maxClimbComparison
        val candidateBottomToTop = candidate.averageBottomToTopClimbRateMps ?: Double.NEGATIVE_INFINITY
        val currentBottomToTop = current.averageBottomToTopClimbRateMps ?: Double.NEGATIVE_INFINITY
        val bottomToTopComparison = candidateBottomToTop.compareTo(currentBottomToTop)
        if (bottomToTopComparison != 0) return bottomToTopComparison
        return candidate.updatedAtMonoMs.compareTo(current.updatedAtMonoMs)
    }

    private fun distanceMeters(
        latitudeA: Double,
        longitudeA: Double,
        latitudeB: Double,
        longitudeB: Double
    ): Double {
        val latitudeARad = Math.toRadians(latitudeA)
        val latitudeBRad = Math.toRadians(latitudeB)
        val deltaLatitude = latitudeBRad - latitudeARad
        val deltaLongitude = Math.toRadians(longitudeB - longitudeA)
        val x = deltaLongitude * cos((latitudeARad + latitudeBRad) / 2.0)
        val y = deltaLatitude
        return EARTH_RADIUS_METERS * sqrt(x * x + y * y)
    }

    private fun startOfLocalDayWallMs(nowWallMs: Long): Long {
        val localDate = Instant.ofEpochMilli(nowWallMs)
            .atZone(localZoneId)
            .toLocalDate()
        return localDate.atStartOfDay(localZoneId).toInstant().toEpochMilli()
    }

    private fun startOfNextLocalDayWallMs(nowWallMs: Long): Long {
        val localDate = Instant.ofEpochMilli(nowWallMs)
            .atZone(localZoneId)
            .toLocalDate()
            .plusDays(1)
        return localDate.atStartOfDay(localZoneId).toInstant().toEpochMilli()
    }

    private fun pruneSegmentIndexCache() {
        if (segmentIndexByTargetId.isEmpty()) return
        val retainedTargetIds = HashSet<String>(trackerByTargetId.size + hotspotById.size)
        retainedTargetIds.addAll(trackerByTargetId.keys)
        for (hotspot in hotspotById.values) {
            retainedTargetIds += hotspot.sourceTargetId
        }
        val iterator = segmentIndexByTargetId.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!retainedTargetIds.contains(entry.key)) {
                iterator.remove()
            }
        }
    }

    private data class ThermalInput(
        val targets: List<OgnTrafficTarget>,
        val streamingEnabled: Boolean,
        val suppressedTargetKeys: Set<String>,
        val retentionHours: Int,
        val displayPercent: Int
    )

    private data class ProcessedSourceState(
        val lastSourceSeenMonoMs: Long,
        val lastProcessedMonoMs: Long
    )

    private data class ThermalTracker(
        val sourceTargetId: String,
        var sourceLabel: String,
        val startedAtMonoMs: Long,
        val startedAtWallMs: Long,
        var lastSeenMonoMs: Long,
        var lastStrongClimbMonoMs: Long,
        var startAltitudeMeters: Double?,
        var maxAltitudeMeters: Double?,
        var maxAltitudeAtMonoMs: Long?,
        var sampleCount: Int,
        var climbSumMps: Double,
        var maxClimbRateMps: Double,
        var previousTrackDegrees: Double?,
        var accumulatedTurnDegrees: Double,
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

        fun addTrackSample(trackDegrees: Double?) {
            val normalizedTrackDegrees = normalizeTrackDegreesOrNullInternal(trackDegrees) ?: return
            val previousTrack = previousTrackDegrees
            if (previousTrack != null) {
                accumulatedTurnDegrees += shortestTurnDeltaDegreesInternal(previousTrack, normalizedTrackDegrees)
            }
            previousTrackDegrees = normalizedTrackDegrees
        }

        fun toHotspot(
            stableId: String,
            nowMonoMs: Long,
            nowWallMs: Long,
            state: OgnThermalHotspotState
        ): OgnThermalHotspot {
            return OgnThermalHotspot(
                id = stableId,
                sourceTargetId = sourceTargetId,
                sourceLabel = sourceLabel,
                latitude = centroidLatitude,
                longitude = centroidLongitude,
                startedAtMonoMs = startedAtMonoMs,
                startedAtWallMs = startedAtWallMs,
                updatedAtMonoMs = nowMonoMs,
                updatedAtWallMs = nowWallMs,
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
                nowWallMs: Long,
                climbRateMps: Double,
                altitudeMeters: Double?
            ): ThermalTracker {
                return ThermalTracker(
                    sourceTargetId = sourceTargetId,
                    sourceLabel = target.displayLabel.ifBlank { target.callsign },
                    startedAtMonoMs = nowMonoMs,
                    startedAtWallMs = nowWallMs,
                    lastSeenMonoMs = nowMonoMs,
                    lastStrongClimbMonoMs = nowMonoMs,
                    startAltitudeMeters = altitudeMeters,
                    maxAltitudeMeters = altitudeMeters,
                    maxAltitudeAtMonoMs = altitudeMeters?.let { nowMonoMs },
                    sampleCount = 1,
                    climbSumMps = climbRateMps,
                    maxClimbRateMps = climbRateMps,
                    previousTrackDegrees = normalizeTrackDegreesOrNullInternal(target.trackDegrees),
                    accumulatedTurnDegrees = 0.0,
                    centroidLatitudeSum = target.latitude,
                    centroidLongitudeSum = target.longitude,
                    centroidSampleCount = 1,
                    confirmed = false,
                    hotspotId = null
                )
            }

            private fun normalizeTrackDegreesOrNullInternal(trackDegrees: Double?): Double? {
                val finite = trackDegrees?.takeIf { it.isFinite() } ?: return null
                return ((finite % 360.0) + 360.0) % 360.0
            }

            private fun shortestTurnDeltaDegreesInternal(from: Double, to: Double): Double {
                val delta = ((to - from + 540.0) % 360.0) - 180.0
                return abs(delta)
            }
        }
    }

    private companion object {
        private const val ENTRY_CLIMB_THRESHOLD_MPS = 0.3
        private const val CONTINUE_CLIMB_THRESHOLD_MPS = 0.15
        private const val MIN_CONFIRM_PEAK_CLIMB_MPS = 0.5
        private const val MIN_CONFIRM_CUMULATIVE_TURN_DEGREES = 730.0
        private const val MIN_CONFIRM_DURATION_MS = 25_000L
        private const val MIN_CONFIRM_SAMPLE_COUNT = 4
        private const val MIN_CONFIRM_ALTITUDE_GAIN_METERS = 35.0
        private const val THERMAL_CONTINUITY_GRACE_MS = 20_000L
        private const val TARGET_MISSING_TIMEOUT_MS = 45_000L
        private const val PROCESSED_SOURCE_SEEN_RETENTION_MS = TARGET_MISSING_TIMEOUT_MS
        private const val SOURCE_SEEN_MONO_RESET_THRESHOLD_MS = 60_000L
        private const val MILLIS_PER_HOUR = 3_600_000L
        private const val AREA_WINNER_RECENT_WINDOW_MS = 300_000L
        private const val AREA_DEDUP_RADIUS_METERS = 700.0
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
