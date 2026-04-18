package com.trust3.xcpro.map

/**
 * Narrow runtime-facing bridge for orientation lifecycle control.
 */
interface MapOrientationRuntimePort {
    fun start()

    fun stop()
}
