package com.example.xcpro.hawk

data class HawkOutput(
    val vRawMps: Double?,
    val vAudioMps: Double?,
    val accelVariance: Double?,
    val baroInnovationMps: Double?,
    val baroHz: Double?,
    val lastUpdateMonoMs: Long?,
    val lastBaroSampleMonoMs: Long?,
    val lastAccelSampleMonoMs: Long?,
    val accelReliable: Boolean,
    val baroSampleAccepted: Boolean,
    val baroRejectionRate: Double,
    val lastBaroVarioMps: Double?
)
