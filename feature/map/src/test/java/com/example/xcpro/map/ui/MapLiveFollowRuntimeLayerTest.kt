package com.example.xcpro.map.ui

import com.example.xcpro.livefollow.watch.LiveFollowMapRenderState
import com.example.xcpro.livefollow.watch.LiveFollowTaskRenderPolicy
import com.example.xcpro.map.TaskRenderSnapshot
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MapLiveFollowRuntimeLayerTest {

    @Test
    fun blockedAmbiguousPolicy_doesNotConsumeTaskSnapshotOrAttachTask() {
        var providerCalls = 0

        val taskAttachmentState = resolveMapLiveFollowTaskAttachmentState(
            mapRenderState = LiveFollowMapRenderState(
                taskRenderPolicy = LiveFollowTaskRenderPolicy.BLOCKED_AMBIGUOUS
            ),
            taskRenderSnapshotProvider = {
                providerCalls += 1
                sampleTaskSnapshot()
            }
        )

        assertFalse(taskAttachmentState.attachTask)
        assertEquals(0, providerCalls)
    }

    @Test
    fun readOnlyUnavailablePolicy_consumesTaskSnapshotWithoutAttachingTask() {
        var providerCalls = 0

        val taskAttachmentState = resolveMapLiveFollowTaskAttachmentState(
            mapRenderState = LiveFollowMapRenderState(
                taskRenderPolicy = LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE
            ),
            taskRenderSnapshotProvider = {
                providerCalls += 1
                sampleTaskSnapshot()
            }
        )

        assertFalse(taskAttachmentState.attachTask)
        assertEquals(1, providerCalls)
        assertEquals(
            "Watched task metadata is unavailable in this transport-limited build.",
            taskAttachmentState.message
        )
    }

    private fun sampleTaskSnapshot(): TaskRenderSnapshot {
        return TaskRenderSnapshot(
            task = Task(id = "task-1"),
            taskType = TaskType.RACING,
            aatEditWaypointIndex = null
        )
    }
}
