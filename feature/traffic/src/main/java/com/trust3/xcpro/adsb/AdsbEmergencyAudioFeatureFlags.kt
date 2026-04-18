package com.trust3.xcpro.adsb

import javax.inject.Singleton

/**
 * Immutable bootstrap config for ADS-B emergency audio defaults.
 *
 * Live rollout state is owned by [AdsbTrafficRepositoryRuntime] and may change at runtime through
 * rollout ports. This object must not become a mutable process-wide authority.
 */
@Singleton
class AdsbEmergencyAudioFeatureFlags private constructor(
    val emergencyAudioEnabled: Boolean,
    val emergencyAudioShadowMode: Boolean
) {
    companion object {
        fun bootstrap(
            emergencyAudioEnabled: Boolean = false,
            emergencyAudioShadowMode: Boolean = false
        ): AdsbEmergencyAudioFeatureFlags =
            AdsbEmergencyAudioFeatureFlags(
                emergencyAudioEnabled = emergencyAudioEnabled,
                emergencyAudioShadowMode = emergencyAudioShadowMode
            )
    }
}
