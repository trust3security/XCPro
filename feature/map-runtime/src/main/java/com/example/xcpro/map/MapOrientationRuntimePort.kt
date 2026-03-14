package com.example.xcpro.map

/**
 * Narrow runtime-facing bridge for orientation lifecycle control.
 */
interface MapOrientationRuntimePort {
    fun start()

    fun stop()
}
