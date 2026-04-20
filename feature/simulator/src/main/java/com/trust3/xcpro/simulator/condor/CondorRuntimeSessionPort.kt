package com.trust3.xcpro.simulator.condor

interface CondorRuntimeSessionPort {
    fun requestConnect()

    fun requestDisconnect()
}
