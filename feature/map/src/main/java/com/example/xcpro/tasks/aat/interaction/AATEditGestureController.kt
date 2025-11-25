package com.example.xcpro.tasks.aat.interaction

import com.example.xcpro.tasks.aat.models.AATLatLng

/**
 * Translates raw gesture events into high-level edit intents.
 * Keeps gesture parsing separate from map/state logic for easier testing.
 */
class AATEditGestureController {

    sealed interface Intent {
        data class Drag(val candidate: AATLatLng) : Intent
        data class LongPress(val at: AATLatLng) : Intent
        object Cancel : Intent
    }

    fun onDrag(candidate: AATLatLng): Intent = Intent.Drag(candidate)

    fun onLongPress(at: AATLatLng): Intent = Intent.LongPress(at)

    fun onCancel(): Intent = Intent.Cancel
}
