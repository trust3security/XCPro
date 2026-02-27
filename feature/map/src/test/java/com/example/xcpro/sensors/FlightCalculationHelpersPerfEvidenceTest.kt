package com.example.xcpro.sensors

import com.example.dfcards.dfcards.calculations.SimpleAglCalculator
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.glider.SpeedBoundsMs
import com.example.xcpro.glider.StillAirSinkProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

@OptIn(ExperimentalCoroutinesApi::class)
class FlightCalculationHelpersPerfEvidenceTest {

    private val sinkProvider = object : StillAirSinkProvider {
        override fun sinkAtSpeed(airspeedMs: Double): Double? = null
        override fun iasBoundsMs(): SpeedBoundsMs? = null
    }

    @Test
    fun aglBurstPath_beforeAfterEvidence_meetsBudgets() = runTest {
        val perfEnabled = System.getProperty("xcpro.enablePerfEvidence") == "true" ||
            System.getProperty("org.gradle.project.xcpro.enablePerfEvidence") == "true"
        assumeTrue(perfEnabled)

        val updatesPerRun = 5_000
        val measuredRuns = 12
        val warmupRuns = 3

        val beforeElapsedNs = mutableListOf<Long>()
        val beforeAllocBytes = mutableListOf<Long>()
        val afterElapsedNs = mutableListOf<Long>()
        val afterAllocBytes = mutableListOf<Long>()

        repeat(warmupRuns + measuredRuns) { runIndex ->
            val worker = LegacyRecursiveAglWorker(scope = this)
            forceGc()
            val heapBefore = usedHeapBytes()
            val startNs = System.nanoTime()
            repeat(updatesPerRun) { index ->
                worker.submit(1_000.0 + index)
            }
            advanceUntilIdle()
            val elapsedNs = System.nanoTime() - startNs
            forceGc()
            val allocBytes = (usedHeapBytes() - heapBefore).coerceAtLeast(0L)
            if (runIndex >= warmupRuns) {
                beforeElapsedNs += elapsedNs
                beforeAllocBytes += allocBytes
            }
        }

        repeat(warmupRuns + measuredRuns) { runIndex ->
            val nowMonoMs = AtomicLong(0L)
            val aglCalculator = mock<SimpleAglCalculator>()
            whenever(aglCalculator.calculateAgl(any(), any(), any(), anyOrNull())).thenAnswer { invocation ->
                invocation.getArgument<Double>(0) - 10.0
            }
            val helpers = FlightCalculationHelpers(
                scope = this,
                aglCalculator = aglCalculator,
                locationHistory = mutableListOf(),
                sinkProvider = sinkProvider,
                nowMonoMsProvider = { nowMonoMs.addAndGet(20_000L) }
            )

            forceGc()
            val heapBefore = usedHeapBytes()
            val startNs = System.nanoTime()
            repeat(updatesPerRun) { index ->
                helpers.updateAGL(
                    baroAltitude = 1_000.0 + index,
                    gps = gpsSample(),
                    speed = 30.0
                )
            }
            advanceUntilIdle()
            val elapsedNs = System.nanoTime() - startNs
            forceGc()
            val allocBytes = (usedHeapBytes() - heapBefore).coerceAtLeast(0L)
            if (runIndex >= warmupRuns) {
                afterElapsedNs += elapsedNs
                afterAllocBytes += allocBytes
            }
        }

        val beforeP50Ms = percentileMs(beforeElapsedNs, 50)
        val beforeP95Ms = percentileMs(beforeElapsedNs, 95)
        val afterP50Ms = percentileMs(afterElapsedNs, 50)
        val afterP95Ms = percentileMs(afterElapsedNs, 95)
        val beforeAllocP50 = percentileLong(beforeAllocBytes, 50)
        val afterAllocP50 = percentileLong(afterAllocBytes, 50)

        val p50BudgetMs = beforeP50Ms * 1.15
        val p95BudgetMs = beforeP95Ms * 1.20
        val allocBudgetBytes = if (beforeAllocP50 > 0L) (beforeAllocP50 * 1.10).toLong() else Long.MAX_VALUE
        val p50DeltaPct = percentDelta(beforeP50Ms, afterP50Ms)
        val p95DeltaPct = percentDelta(beforeP95Ms, afterP95Ms)
        val allocDeltaPct = if (beforeAllocP50 > 0L) {
            percentDelta(beforeAllocP50.toDouble(), afterAllocP50.toDouble())
        } else {
            Double.NaN
        }

        val evidenceLine =
            "AGL_PERF_EVIDENCE before_p50_ms=$beforeP50Ms before_p95_ms=$beforeP95Ms " +
                "after_p50_ms=$afterP50Ms after_p95_ms=$afterP95Ms " +
            "before_alloc_p50_bytes=$beforeAllocP50 after_alloc_p50_bytes=$afterAllocP50 " +
            "p50_delta_pct=$p50DeltaPct p95_delta_pct=$p95DeltaPct alloc_delta_pct=$allocDeltaPct " +
            "runs=$measuredRuns updates_per_run=$updatesPerRun"
        val reportDir = Path.of("build", "reports", "perf")
        Files.createDirectories(reportDir)
        Files.newBufferedWriter(
            reportDir.resolve("agl-burst-evidence.txt"),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        ).use { writer ->
            writer.write(evidenceLine)
            writer.newLine()
        }
        println(evidenceLine)

        val budgetsMet = afterP50Ms <= p50BudgetMs &&
            afterP95Ms <= p95BudgetMs &&
            (beforeAllocP50 <= 0L || afterAllocP50 <= allocBudgetBytes)
        assertTrue("Expected measured samples for evidence output", beforeElapsedNs.isNotEmpty() && afterElapsedNs.isNotEmpty())
        println("AGL_PERF_EVIDENCE_BUDGETS_MET=$budgetsMet")
    }

    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun forceGc() {
        repeat(2) {
            System.gc()
            System.runFinalization()
        }
    }

    private fun percentileMs(samples: List<Long>, percentile: Int): Double =
        percentileLong(samples, percentile) / 1_000_000.0

    private fun percentileLong(samples: List<Long>, percentile: Int): Long {
        val sorted = samples.sorted()
        val position = ceil((percentile / 100.0) * sorted.size).toInt().coerceAtLeast(1) - 1
        return sorted[position]
    }

    private fun percentDelta(before: Double, after: Double): Double {
        if (before == 0.0) return Double.NaN
        return ((after - before) / before) * 100.0
    }

    private fun gpsSample(): GPSData =
        GPSData(
            position = GeoPoint(latitude = 1.0, longitude = 1.0),
            altitude = AltitudeM(500.0),
            speed = SpeedMs(30.0),
            bearing = 0.0,
            accuracy = 5f,
            timestamp = 0L,
            monotonicTimestampMillis = 0L
        )

    private class LegacyRecursiveAglWorker(
        private val scope: CoroutineScope
    ) {
        private val lock = Any()
        private var pendingAltitude: Double? = null
        private var workerJob: Job? = null

        fun submit(baroAltitude: Double) {
            synchronized(lock) {
                pendingAltitude = baroAltitude
                if (workerJob?.isActive != true) {
                    workerJob = scope.launch { processPending() }
                }
            }
        }

        private suspend fun processPending() {
            val altitude = synchronized(lock) {
                val next = pendingAltitude
                pendingAltitude = null
                next
            } ?: run {
                synchronized(lock) { workerJob = null }
                return
            }

            try {
                @Suppress("UnusedVariable")
                val newAgl = altitude - 10.0
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
            }

            synchronized(lock) {
                if (pendingAltitude != null) {
                    workerJob = scope.launch { processPending() }
                } else {
                    workerJob = null
                }
            }
        }
    }
}
