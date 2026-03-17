package com.example.xcpro.tasks

/**
 * One-shot task sheet viewport requests collected by the map shell.
 */
sealed interface TaskSheetViewportEffect {
    object RequestFitCurrentTask : TaskSheetViewportEffect
}
