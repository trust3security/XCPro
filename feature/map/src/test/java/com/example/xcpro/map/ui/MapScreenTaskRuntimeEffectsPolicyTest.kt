package com.example.xcpro.map.ui

import com.example.xcpro.tasks.core.TaskType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapScreenTaskRuntimeEffectsPolicyTest {

    @Test
    fun shouldExitAatEditMode_returnsTrue_whenTaskLeavesAatWhileEditModeActive() {
        assertTrue(
            shouldExitAatEditMode(
                taskType = TaskType.RACING,
                isAATEditMode = true
            )
        )
    }

    @Test
    fun shouldExitAatEditMode_returnsFalse_whenTaskRemainsAat() {
        assertFalse(
            shouldExitAatEditMode(
                taskType = TaskType.AAT,
                isAATEditMode = true
            )
        )
    }

    @Test
    fun shouldExitAatEditMode_returnsFalse_whenEditModeAlreadyDisabled() {
        assertFalse(
            shouldExitAatEditMode(
                taskType = TaskType.RACING,
                isAATEditMode = false
            )
        )
    }

    @Test
    fun resolveTaskDrawerRuntimePolicy_blocksAndCloses_whenAatEditModeHasOpenDrawer() {
        val policy = resolveTaskDrawerRuntimePolicy(
            taskType = TaskType.AAT,
            isAATEditMode = true,
            isDrawerOpen = true
        )

        assertTrue(policy.shouldBlockDrawerGestures)
        assertTrue(policy.shouldCloseOpenDrawer)
    }

    @Test
    fun resolveTaskDrawerRuntimePolicy_blocksWithoutClose_whenAatEditModeHasClosedDrawer() {
        val policy = resolveTaskDrawerRuntimePolicy(
            taskType = TaskType.AAT,
            isAATEditMode = true,
            isDrawerOpen = false
        )

        assertTrue(policy.shouldBlockDrawerGestures)
        assertFalse(policy.shouldCloseOpenDrawer)
    }

    @Test
    fun resolveTaskDrawerRuntimePolicy_allowsDrawer_whenAatEditModeIsOff() {
        val policy = resolveTaskDrawerRuntimePolicy(
            taskType = TaskType.AAT,
            isAATEditMode = false,
            isDrawerOpen = true
        )

        assertFalse(policy.shouldBlockDrawerGestures)
        assertFalse(policy.shouldCloseOpenDrawer)
    }

    @Test
    fun resolveTaskDrawerRuntimePolicy_allowsDrawer_forRacingTasks() {
        val policy = resolveTaskDrawerRuntimePolicy(
            taskType = TaskType.RACING,
            isAATEditMode = true,
            isDrawerOpen = true
        )

        assertFalse(policy.shouldBlockDrawerGestures)
        assertFalse(policy.shouldCloseOpenDrawer)
    }
}
