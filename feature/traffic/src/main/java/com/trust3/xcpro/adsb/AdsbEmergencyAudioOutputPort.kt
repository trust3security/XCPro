package com.trust3.xcpro.adsb

/**
 * Boundary for ADS-B EMERGENCY alert audio side effects.
 *
 * Business policy remains repository/FSM owned. Implementations play audio only when invoked.
 */
interface AdsbEmergencyAudioOutputPort {
    fun playEmergencyAlert(triggerMonoMs: Long, emergencyTargetId: String?)
}

object NoOpAdsbEmergencyAudioOutputPort : AdsbEmergencyAudioOutputPort {
    override fun playEmergencyAlert(triggerMonoMs: Long, emergencyTargetId: String?) = Unit
}

