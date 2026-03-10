package com.example.xcpro.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class MapOverlayRuntimeInteractionDelegate(
    private val coroutineScope: CoroutineScope,
    private val applyMapInteractionState: (Boolean) -> Unit
) {
    companion object {
        private const val MAP_INTERACTION_DEACTIVATE_DELAY_MS = 500L
    }

    private var mapInteractionActive: Boolean = false
    private var pendingInteractionDeactivateJob: Job? = null

    val isMapInteractionActive: Boolean
        get() = mapInteractionActive

    fun setMapInteractionActive(active: Boolean) {
        if (active) {
            pendingInteractionDeactivateJob?.cancel()
            pendingInteractionDeactivateJob = null
            applyMapInteractionStateIfNeeded(true)
            return
        }

        val deactivateDelayMs = resolveMapInteractionDeactivateDelayMs(
            interactionWasActive = mapInteractionActive,
            requestedActive = false
        )
        if (deactivateDelayMs <= 0L) {
            pendingInteractionDeactivateJob?.cancel()
            pendingInteractionDeactivateJob = null
            applyMapInteractionStateIfNeeded(false)
            return
        }

        if (pendingInteractionDeactivateJob != null) {
            return
        }

        pendingInteractionDeactivateJob = coroutineScope.launch {
            delay(deactivateDelayMs)
            pendingInteractionDeactivateJob = null
            applyMapInteractionStateIfNeeded(false)
        }
    }

    fun onMapDetached() {
        pendingInteractionDeactivateJob?.cancel()
        pendingInteractionDeactivateJob = null
        applyMapInteractionStateIfNeeded(false)
    }

    private fun resolveMapInteractionDeactivateDelayMs(
        interactionWasActive: Boolean,
        requestedActive: Boolean
    ): Long {
        if (!requestedActive && interactionWasActive) {
            return MAP_INTERACTION_DEACTIVATE_DELAY_MS
        }
        return 0L
    }

    private fun applyMapInteractionStateIfNeeded(active: Boolean) {
        if (mapInteractionActive == active) return
        mapInteractionActive = active
        applyMapInteractionState(active)
    }
}
