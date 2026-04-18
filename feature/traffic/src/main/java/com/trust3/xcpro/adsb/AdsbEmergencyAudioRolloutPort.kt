package com.trust3.xcpro.adsb

import kotlinx.coroutines.flow.Flow

interface AdsbEmergencyAudioRolloutPort {
    val emergencyAudioMasterEnabledFlow: Flow<Boolean>
    val emergencyAudioShadowModeFlow: Flow<Boolean>
    val emergencyAudioRollbackLatchedFlow: Flow<Boolean>
    val emergencyAudioRollbackReasonFlow: Flow<String?>

    suspend fun latchEmergencyAudioRollback(reason: String)
    suspend fun clearEmergencyAudioRollback()
}
