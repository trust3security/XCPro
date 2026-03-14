package com.example.xcpro.map

/**
 * Shell-owned bridge for map-view lifecycle dispatch and map-state cleanup.
 *
 * The runtime lifecycle owner uses this port so it does not directly own
 * `MapScreenState`, `MapView`, or `MapLibreMap`.
 */
interface MapLifecycleSurfacePort {
    fun currentHostToken(): Any?

    fun dispatchCreateIfPresent(): Boolean

    fun dispatchStartIfPresent(): Boolean

    fun dispatchResumeIfPresent(): Boolean

    fun dispatchPauseIfPresent(): Boolean

    fun dispatchStopIfPresent(): Boolean

    fun dispatchDestroyIfPresent(): Boolean

    fun captureCameraSnapshot()

    fun clearRuntimeOverlays()

    fun clearMapSurfaceReferences()

    fun isMapViewReady(): Boolean

    fun isMapLibreReady(): Boolean
}
