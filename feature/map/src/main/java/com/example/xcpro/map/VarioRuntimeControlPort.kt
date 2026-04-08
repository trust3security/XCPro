package com.example.xcpro.map

interface VarioRuntimeControlPort {
    fun ensureRunningIfPermitted(): Boolean
    fun requestStop()
}
