package com.trust3.xcpro.simulator.condor

internal data class CondorReadChunk(
    val bytes: ByteArray,
    val receivedMonoMs: Long
)
