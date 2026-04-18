package com.trust3.xcpro.map

import androidx.lifecycle.Lifecycle

interface MapLifecycleRuntimePort {
    fun handleLifecycleEvent(event: Lifecycle.Event)

    fun syncCurrentOwnerState(state: Lifecycle.State)

    fun cleanup()
}
