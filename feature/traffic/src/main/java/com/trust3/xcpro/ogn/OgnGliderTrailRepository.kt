package com.trust3.xcpro.ogn

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.core.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface OgnGliderTrailRepository {
    val segments: StateFlow<List<OgnGliderTrailSegment>>
}

@Singleton
class OgnGliderTrailRepositoryImpl @Inject constructor(
    private val ognTrafficRepository: OgnTrafficRepository,
    private val clock: Clock,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : OgnGliderTrailRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val processingMutex = Mutex()

    private val lastSampleByTargetId = LinkedHashMap<String, TrailSample>()
    private val segmentById = LinkedHashMap<String, OgnGliderTrailSegment>()
    private var latestTargetsSnapshot: List<OgnTrafficTarget> = emptyList()
    private var latestStreamingEnabled: Boolean = false
    private var housekeepingJob: Job? = null

    private val _segments = MutableStateFlow<List<OgnGliderTrailSegment>>(emptyList())
    override val segments: StateFlow<List<OgnGliderTrailSegment>> = _segments.asStateFlow()

    init {
        scope.launch {
            combine(
                ognTrafficRepository.targets,
                ognTrafficRepository.isEnabled,
                ognTrafficRepository.suppressedTargetIds
            ) { targets, streamingEnabled, suppressedTargetKeys ->
                TrailInput(
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
            val changed = processTargets(
                targets = targets,
                streamingEnabled = streamingEnabled,
                suppressedTargetKeys = suppressedTargetKeys,
                nowMonoMs = nowMonoMs
            )
            if (changed) {
                publishSegments()
            }
            scheduleHousekeepingLocked(nowMonoMs)
        }
    }

    private fun processTargets(
        targets: List<OgnTrafficTarget>,
        streamingEnabled: Boolean,
        suppressedTargetKeys: Set<String>,
        nowMonoMs: Long
    ): Boolean {
        var changed = pruneExpiredSegments(nowMonoMs)
        changed = purgeSuppressedArtifacts(suppressedTargetKeys) || changed

        if (!streamingEnabled) {
            if (lastSampleByTargetId.isNotEmpty()) {
                lastSampleByTargetId.clear()
            }
            return changed
        }

        val freshTargets = collectFreshTargets(targets)
        if (freshTargets.isEmpty()) {
            pruneStaleSamples(nowMonoMs)
            return changed
        }

        for (target in freshTargets.sortedBy { it.id }) {
            changed = updateTrailForTarget(target, nowMonoMs) || changed
        }
        pruneStaleSamples(nowMonoMs)
        return changed
    }

    private fun collectFreshTargets(targets: List<OgnTrafficTarget>): List<OgnTrafficTarget> {
        if (targets.isEmpty()) return emptyList()
        val freshTargets = ArrayList<OgnTrafficTarget>(targets.size)
        for (target in targets) {
            val normalizedTargetId = normalizeOgnAircraftKeyOrNull(target.canonicalKey) ?: continue
            val lastSeenMonoMs = lastSampleByTargetId[normalizedTargetId]?.lastSeenMonoMs
            if (lastSeenMonoMs == null || target.lastSeenMillis > lastSeenMonoMs) {
                freshTargets += target
            }
        }
        return freshTargets
    }

    private fun updateTrailForTarget(target: OgnTrafficTarget, nowMonoMs: Long): Boolean {
        val normalizedTargetId = normalizeOgnAircraftKeyOrNull(target.canonicalKey) ?: return false
        if (!isValidThermalCoordinate(target.latitude, target.longitude)) return false
        val sourceSeenMonoMs = target.lastSeenMillis
        val previous = lastSampleByTargetId[normalizedTargetId]
        if (previous == null) {
            lastSampleByTargetId[normalizedTargetId] = TrailSample(
                anchorLatitude = target.latitude,
                anchorLongitude = target.longitude,
                lastSeenMonoMs = sourceSeenMonoMs
            )
            return false
        }

        if (sourceSeenMonoMs <= previous.lastSeenMonoMs) {
            return false
        }

        if (sourceSeenMonoMs - previous.lastSeenMonoMs > MAX_SAMPLE_GAP_MS) {
            lastSampleByTargetId[normalizedTargetId] = TrailSample(
                anchorLatitude = target.latitude,
                anchorLongitude = target.longitude,
                lastSeenMonoMs = sourceSeenMonoMs
            )
            return false
        }

        val distanceMeters = OgnSubscriptionPolicy.haversineMeters(
            lat1 = previous.anchorLatitude,
            lon1 = previous.anchorLongitude,
            lat2 = target.latitude,
            lon2 = target.longitude
        )
        if (!distanceMeters.isFinite()) {
            return false
        }

        if (distanceMeters < MIN_SEGMENT_DISTANCE_METERS) {
            lastSampleByTargetId[normalizedTargetId] = previous.copy(lastSeenMonoMs = sourceSeenMonoMs)
            return false
        }

        if (distanceMeters > MAX_SEGMENT_DISTANCE_METERS) {
            lastSampleByTargetId[normalizedTargetId] = TrailSample(
                anchorLatitude = target.latitude,
                anchorLongitude = target.longitude,
                lastSeenMonoMs = sourceSeenMonoMs
            )
            return false
        }

        val varioMps = target.verticalSpeedMps?.takeIf { it.isFinite() } ?: FALLBACK_VARIO_MPS
        val segmentId = "$normalizedTargetId:$sourceSeenMonoMs"
        var changed = false
        if (!segmentById.containsKey(segmentId)) {
            segmentById[segmentId] = OgnGliderTrailSegment(
                id = segmentId,
                sourceTargetId = normalizedTargetId,
                sourceLabel = target.displayLabel.ifBlank { target.callsign },
                startLatitude = previous.anchorLatitude,
                startLongitude = previous.anchorLongitude,
                endLatitude = target.latitude,
                endLongitude = target.longitude,
                colorIndex = ognTrailColorIndex(varioMps),
                widthPx = ognTrailWidthPx(varioMps),
                timestampMonoMs = nowMonoMs
            )
            trimSegmentOverflow()
            changed = true
        }

        lastSampleByTargetId[normalizedTargetId] = TrailSample(
            anchorLatitude = target.latitude,
            anchorLongitude = target.longitude,
            lastSeenMonoMs = sourceSeenMonoMs
        )
        return changed
    }

    private fun purgeSuppressedArtifacts(suppressedTargetKeys: Set<String>): Boolean {
        if (suppressedTargetKeys.isEmpty()) return false
        var changed = false

        val sampleIterator = lastSampleByTargetId.entries.iterator()
        while (sampleIterator.hasNext()) {
            val entry = sampleIterator.next()
            if (!suppressedTargetKeys.contains(entry.key)) continue
            sampleIterator.remove()
            changed = true
        }

        val segmentIterator = segmentById.entries.iterator()
        while (segmentIterator.hasNext()) {
            val entry = segmentIterator.next()
            if (!suppressedTargetKeys.contains(entry.value.sourceTargetId)) continue
            segmentIterator.remove()
            changed = true
        }

        return changed
    }

    private fun pruneExpiredSegments(nowMonoMs: Long): Boolean {
        if (segmentById.isEmpty()) return false
        var changed = false
        val iterator = segmentById.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMonoMs - entry.value.timestampMonoMs > TRAIL_HISTORY_WINDOW_MS) {
                iterator.remove()
                changed = true
            }
        }
        return changed
    }

    private fun trimSegmentOverflow() {
        if (segmentById.size <= MAX_SEGMENTS) return
        val iterator = segmentById.entries.iterator()
        while (segmentById.size > MAX_SEGMENTS && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    private fun pruneStaleSamples(nowMonoMs: Long) {
        if (lastSampleByTargetId.isEmpty()) return
        val iterator = lastSampleByTargetId.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMonoMs - entry.value.lastSeenMonoMs > SAMPLE_RETENTION_MS) {
                iterator.remove()
            }
        }
    }

    private fun publishSegments() {
        _segments.value = segmentById.values.toList()
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
                val changed = processTargets(
                    targets = latestTargetsSnapshot,
                    streamingEnabled = latestStreamingEnabled,
                    suppressedTargetKeys = ognTrafficRepository.suppressedTargetIds.value,
                    nowMonoMs = now
                )
                if (changed) {
                    publishSegments()
                }
                scheduleHousekeepingLocked(now)
            }
        }
    }

    private fun nextHousekeepingDelayMs(nowMonoMs: Long): Long? {
        var nextDeadlineMonoMs = Long.MAX_VALUE

        for (segment in segmentById.values) {
            val deadline = segment.timestampMonoMs + TRAIL_HISTORY_WINDOW_MS
            if (deadline < nextDeadlineMonoMs) {
                nextDeadlineMonoMs = deadline
            }
        }

        for (sample in lastSampleByTargetId.values) {
            val deadline = sample.lastSeenMonoMs + SAMPLE_RETENTION_MS
            if (deadline < nextDeadlineMonoMs) {
                nextDeadlineMonoMs = deadline
            }
        }

        if (nextDeadlineMonoMs == Long.MAX_VALUE) return null
        return (nextDeadlineMonoMs - nowMonoMs).coerceAtLeast(0L)
    }

    private data class TrailInput(
        val targets: List<OgnTrafficTarget>,
        val streamingEnabled: Boolean,
        val suppressedTargetKeys: Set<String>
    )

    private data class TrailSample(
        val anchorLatitude: Double,
        val anchorLongitude: Double,
        val lastSeenMonoMs: Long
    )

    private companion object {
        private const val TRAIL_HISTORY_WINDOW_MS = 20L * 60L * 1000L
        private const val MAX_SEGMENTS = 24_000
        private const val MIN_SEGMENT_DISTANCE_METERS = 15.0
        private const val MAX_SEGMENT_DISTANCE_METERS = 25_000.0
        private const val MAX_SAMPLE_GAP_MS = 120_000L
        private const val SAMPLE_RETENTION_MS = 180_000L
        private const val FALLBACK_VARIO_MPS = 0.0
    }
}
