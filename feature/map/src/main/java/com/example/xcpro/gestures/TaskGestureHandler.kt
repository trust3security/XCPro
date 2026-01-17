package com.example.xcpro.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import org.maplibre.android.maps.MapLibreMap

enum class TaskGestureConsume {
    Consume,
    PassThrough
}

data class TaskGestureContext(
    val mapLibreMap: MapLibreMap?,
    val gestureStartPosition: Offset,
    val activePointers: List<PointerInputChange>,
    val gestureStartTimeMs: Long,
    val currentTimeMs: Long
)

data class TaskGestureCallbacks(
    val onEnterEditMode: (index: Int, lat: Double, lon: Double, radiusKm: Double) -> Unit = { _, _, _, _ -> },
    val onExitEditMode: () -> Unit = {},
    val onDragTarget: (index: Int, lat: Double, lon: Double) -> Unit = { _, _, _ -> }
)

interface TaskGestureHandler {
    fun onGestureStart(context: TaskGestureContext): TaskGestureConsume = TaskGestureConsume.PassThrough
    fun onGestureMove(context: TaskGestureContext): TaskGestureConsume = TaskGestureConsume.PassThrough
    fun onGestureEnd(context: TaskGestureContext): TaskGestureConsume = TaskGestureConsume.PassThrough
    fun onExternalEditModeChanged(isEditMode: Boolean) {}
}
