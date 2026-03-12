package com.example.xcpro.tasks.aat.gestures

import androidx.compose.ui.geometry.Offset
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureContext
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import org.junit.Assert.assertEquals
import org.junit.Test

class AatGestureHandlerCommitDedupeTest {

    @Test
    fun dragCommit_skipsWhenTargetDidNotMove() {
        var commitCount = 0
        val handler = AatGestureHandler(
            waypointsProvider = { listOf(sampleTurnpoint()) },
            callbacks = TaskGestureCallbacks(
                onDragTargetCommit = { _, _, _ -> commitCount += 1 }
            )
        )

        setPrivateField(handler, "editModeIndex", 0)
        setPrivateField(handler, "isDragging", true)
        setPrivateField(handler, "hasDragPreview", true)
        setPrivateField(handler, "lastDragLatitude", 0.0)
        setPrivateField(handler, "lastDragLongitude", 0.0)
        setPrivateField(handler, "dragStartTargetLatitude", 0.0)
        setPrivateField(handler, "dragStartTargetLongitude", 0.0)

        handler.onGestureEnd(quickGestureEndContext())

        assertEquals(0, commitCount)
    }

    @Test
    fun dragCommit_firesWhenTargetMovedBeyondThreshold() {
        var commitCount = 0
        val handler = AatGestureHandler(
            waypointsProvider = { listOf(sampleTurnpoint()) },
            callbacks = TaskGestureCallbacks(
                onDragTargetCommit = { _, _, _ -> commitCount += 1 }
            )
        )

        setPrivateField(handler, "editModeIndex", 0)
        setPrivateField(handler, "isDragging", true)
        setPrivateField(handler, "hasDragPreview", true)
        setPrivateField(handler, "lastDragLatitude", 0.001)
        setPrivateField(handler, "lastDragLongitude", 0.001)
        setPrivateField(handler, "dragStartTargetLatitude", 0.0)
        setPrivateField(handler, "dragStartTargetLongitude", 0.0)

        handler.onGestureEnd(quickGestureEndContext())

        assertEquals(1, commitCount)
    }

    private fun sampleTurnpoint(): TaskWaypoint = TaskWaypoint(
        id = "tp-1",
        title = "Turnpoint",
        subtitle = "",
        lat = 0.0,
        lon = 0.0,
        role = WaypointRole.TURNPOINT
    )

    private fun quickGestureEndContext(): TaskGestureContext = TaskGestureContext(
        mapLibreMap = null,
        gestureStartPosition = Offset.Zero,
        activePointers = emptyList(),
        gestureStartTimeMs = 0L,
        currentTimeMs = 100L
    )

    private fun setPrivateField(target: Any, fieldName: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}
