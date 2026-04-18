package com.trust3.xcpro.map

import com.trust3.xcpro.common.documents.DocumentRef
import com.trust3.xcpro.map.OgnGliderTrailSegment
import com.trust3.xcpro.replay.Selection
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.replay.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapscreenPkgG1PerfEvidenceTest {

    @Test
    fun emitPkgG1PerfEvidence() = runTest {
        val scrubStats = measureReplayScrubLatencyAndNoopRecompute(testScope = this)
        val denseTrailP95Ms = measureDenseTrailRenderP95Ms()

        val payload = linkedMapOf<String, Any?>(
            "package_id" to "pkg-g1",
            "generated_at" to MapscreenPerfEvidenceSupport.nowIsoString(),
            "slo_metrics" to linkedMapOf(
                "MS-UX-05" to linkedMapOf(
                    "scrub_latency_p95_ms" to scrubStats.first,
                    "scrub_latency_p99_ms" to scrubStats.second,
                    "noop_recompute_count" to scrubStats.third
                ),
                "MS-ENG-05" to linkedMapOf(
                    "scia_dense_trail_render_p95_ms" to denseTrailP95Ms
                )
            )
        )
        MapscreenPerfEvidenceSupport.writePackageEvidence(
            packageId = "pkg-g1",
            payload = payload
        )

        assertTrue(scrubStats.first >= 0.0)
        assertTrue(scrubStats.second >= 0.0)
        assertTrue(scrubStats.third >= 0)
        assertTrue(denseTrailP95Ms >= 0.0)
    }

    private suspend fun measureReplayScrubLatencyAndNoopRecompute(
        testScope: TestScope
    ): Triple<Double, Double, Int> {
        val source = MutableSharedFlow<SessionState>(extraBufferCapacity = 32)
        val pendingStartNs = ArrayDeque<Long>()
        val latenciesNs = mutableListOf<Long>()
        var noopRecomputeCount = 0
        var lastEmission: Boolean? = null
        val selection = Selection(
            DocumentRef(
                uri = "content://replay/perf.igc",
                displayName = "perf.igc"
            )
        )

        val collectorJob = testScope.launch {
            source.mapReplaySelectionActive().collect { active ->
                val startedAtNs = pendingStartNs.removeFirstOrNull()
                if (startedAtNs != null) {
                    latenciesNs += (System.nanoTime() - startedAtNs).coerceAtLeast(0L)
                }
                if (lastEmission == active) {
                    noopRecomputeCount += 1
                }
                lastEmission = active
            }
        }
        testScope.runCurrent()

        repeat(80) { runIndex ->
            emitAndTrackLatency(
                testScope = testScope,
                source = source,
                pendingStartNs = pendingStartNs,
                state = SessionState(
                    selection = null,
                    status = SessionStatus.IDLE,
                    currentTimestampMillis = runIndex.toLong() * 10L
                )
            )
            // Presence unchanged: should not emit another distinct value.
            source.emit(
                SessionState(
                    selection = null,
                    status = SessionStatus.PLAYING,
                    currentTimestampMillis = runIndex.toLong() * 10L + 1L
                )
            )
            testScope.runCurrent()

            emitAndTrackLatency(
                testScope = testScope,
                source = source,
                pendingStartNs = pendingStartNs,
                state = SessionState(
                    selection = selection,
                    status = SessionStatus.PAUSED,
                    currentTimestampMillis = runIndex.toLong() * 10L + 2L
                )
            )
            // Presence unchanged while replay progresses: should remain no-op for this projection.
            source.emit(
                SessionState(
                    selection = selection,
                    status = SessionStatus.PLAYING,
                    currentTimestampMillis = runIndex.toLong() * 10L + 3L
                )
            )
            testScope.runCurrent()
        }

        collectorJob.cancel()
        assertTrue("Expected measured scrub latencies", latenciesNs.isNotEmpty())

        val p95Ms = MapscreenPerfEvidenceSupport.percentileMs(latenciesNs, 95)
        val p99Ms = MapscreenPerfEvidenceSupport.percentileMs(latenciesNs, 99)
        return Triple(p95Ms, p99Ms, noopRecomputeCount)
    }

    private suspend fun emitAndTrackLatency(
        testScope: TestScope,
        source: MutableSharedFlow<SessionState>,
        pendingStartNs: ArrayDeque<Long>,
        state: SessionState
    ) {
        pendingStartNs.addLast(System.nanoTime())
        source.emit(state)
        testScope.runCurrent()
    }

    private fun measureDenseTrailRenderP95Ms(): Double {
        val warmups = 10
        val measuredRuns = 36
        val runs = List(warmups + measuredRuns) { runIndex ->
            denseSegments(
                startId = runIndex * 20_000,
                count = 12_000
            )
        }
        val samples = mutableListOf<Long>()
        var previous: List<OgnGliderTrailSegment> = emptyList()

        runs.forEachIndexed { index, segments ->
            val startNs = System.nanoTime()
            val trimmed = OgnGliderTrailOverlay.trimSegmentsForRender(
                segments = segments,
                maxSegments = 12_000
            )
            OgnGliderTrailOverlay.sameSegmentsByIdentity(previous, trimmed)
            previous = trimmed
            val elapsedNs = System.nanoTime() - startNs
            if (index >= warmups) {
                samples += elapsedNs
            }
        }

        return MapscreenPerfEvidenceSupport.percentileMs(samples, 95)
    }

    private fun denseSegments(startId: Int, count: Int): List<OgnGliderTrailSegment> {
        return List(count) { offset ->
            val index = startId + offset
            OgnGliderTrailSegment(
                id = "trail-$index",
                sourceTargetId = "target-${index % 120}",
                sourceLabel = "T${index % 120}",
                startLatitude = -33.80 + (offset * 0.00001),
                startLongitude = 151.10 + (offset * 0.00001),
                endLatitude = -33.79 + (offset * 0.00001),
                endLongitude = 151.11 + (offset * 0.00001),
                colorIndex = offset % 9,
                widthPx = 2.0f + ((offset % 3) * 0.5f),
                timestampMonoMs = index.toLong()
            )
        }
    }
}
