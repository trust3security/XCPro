package com.trust3.xcpro.map

import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.mockito.kotlin.mock

class TaskRenderSyncCoordinatorPerformanceTest {

    @Test
    fun onTaskMutation_dispatchesWithinLatencyBudget() {
        val map: MapLibreMap = mock()
        val coordinator = TaskRenderSyncCoordinator(
            snapshotProvider = ::sampleSnapshot,
            mapProvider = { map },
            renderSync = { _, _ -> },
            renderClear = { _ -> }
        )
        val iterations = 300
        val budgetNs = 100_000_000L // 100 ms
        var maxNs = 0L

        // Warm-up to reduce one-time JIT noise.
        repeat(30) { coordinator.onTaskMutation() }

        repeat(iterations) {
            val start = System.nanoTime()
            coordinator.onTaskMutation()
            val elapsed = System.nanoTime() - start
            if (elapsed > maxNs) {
                maxNs = elapsed
            }
        }

        assertTrue(
            "Task render dispatch exceeded 100 ms budget. maxNs=$maxNs",
            maxNs <= budgetNs
        )
    }

    private fun sampleSnapshot(): TaskRenderSnapshot {
        return TaskRenderSnapshot(
            task = Task(id = "perf-test", waypoints = emptyList()),
            taskType = TaskType.RACING,
            aatEditWaypointIndex = null
        )
    }
}
