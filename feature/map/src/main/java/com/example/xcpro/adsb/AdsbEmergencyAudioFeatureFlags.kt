package com.example.xcpro.adsb

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdsbEmergencyAudioFeatureFlags @Inject constructor() {
    // Master rollout gate for emergency audio policy.
    @Volatile
    var emergencyAudioEnabled: Boolean = false

    // Shadow mode keeps policy/telemetry active while side effects remain disabled.
    @Volatile
    var emergencyAudioShadowMode: Boolean = false
}
