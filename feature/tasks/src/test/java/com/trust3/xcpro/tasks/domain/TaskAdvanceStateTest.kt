package com.trust3.xcpro.tasks.domain

import com.trust3.xcpro.tasks.domain.logic.TaskAdvanceState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskAdvanceStateTest {

    @Test
    fun `manual mode never auto advances`() {
        val advance = TaskAdvanceState()
        advance.setMode(TaskAdvanceState.Mode.MANUAL)
        advance.setArmed(true)

        val result = advance.shouldAdvance(hasEntered = true, closeToTarget = true)

        assertFalse(result)
    }

    @Test
    fun `auto mode advances when armed and inside oz`() {
        val advance = TaskAdvanceState()
        advance.setMode(TaskAdvanceState.Mode.AUTO)
        advance.setArmed(true)

        val result = advance.shouldAdvance(hasEntered = true, closeToTarget = false)

        assertTrue(result)
    }

    @Test
    fun `auto mode does not advance when disarmed`() {
        val advance = TaskAdvanceState()
        advance.setMode(TaskAdvanceState.Mode.AUTO)
        advance.setArmed(false)

        val result = advance.shouldAdvance(hasEntered = true, closeToTarget = true)

        assertFalse(result)
    }
}
