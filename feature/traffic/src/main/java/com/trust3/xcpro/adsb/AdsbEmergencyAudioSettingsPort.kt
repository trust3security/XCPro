package com.trust3.xcpro.adsb

import kotlinx.coroutines.flow.Flow

interface AdsbEmergencyAudioSettingsPort {
    val emergencyAudioEnabledFlow: Flow<Boolean>
    val emergencyAudioCooldownMsFlow: Flow<Long>
}
