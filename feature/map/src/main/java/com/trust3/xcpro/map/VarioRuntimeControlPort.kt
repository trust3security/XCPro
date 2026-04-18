package com.trust3.xcpro.map

interface VarioRuntimeControlPort {
    fun ensureRunningIfPermitted(): Boolean
    fun requestStop()
}
