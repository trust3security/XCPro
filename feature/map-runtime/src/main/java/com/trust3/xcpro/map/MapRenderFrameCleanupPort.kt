package com.trust3.xcpro.map

/**
 * Runtime-facing cleanup-only bridge for the shell-owned render-frame binder.
 */
interface MapRenderFrameCleanupPort {
    fun unbindRenderFrameListener() = Unit
}
