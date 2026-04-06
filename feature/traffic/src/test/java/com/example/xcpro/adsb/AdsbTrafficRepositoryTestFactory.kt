package com.example.xcpro.adsb

import com.example.xcpro.adsb.domain.AdsbNetworkAvailabilityPort
import com.example.xcpro.core.time.Clock
import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun createAdsbTrafficRepository(
    providerClient: AdsbProviderClient,
    tokenRepository: OpenSkyTokenRepository = FakeTokenRepository(),
    clock: Clock = FakeClock(monoMs = 0L, wallMs = 0L),
    dispatcher: CoroutineDispatcher,
    networkAvailabilityPort: AdsbNetworkAvailabilityPort = AlwaysOnlineNetworkAvailabilityPort,
    emergencyAudioSettingsPort: AdsbEmergencyAudioSettingsPort = DisabledEmergencyAudioSettingsPort(),
    emergencyAudioRolloutPort: AdsbEmergencyAudioRolloutPort? = null,
    emergencyAudioOutputPort: AdsbEmergencyAudioOutputPort = NoOpAdsbEmergencyAudioOutputPort,
    emergencyAudioFeatureFlags: AdsbEmergencyAudioFeatureFlags =
        AdsbEmergencyAudioFeatureFlags.bootstrap()
): AdsbTrafficRepositoryImpl = AdsbTrafficRepositoryImpl(
    providerClient = providerClient,
    tokenRepository = tokenRepository,
    clock = clock,
    dispatcher = dispatcher,
    networkAvailabilityPort = networkAvailabilityPort,
    emergencyAudioSettingsPort = emergencyAudioSettingsPort,
    emergencyAudioRolloutPort = emergencyAudioRolloutPort,
    emergencyAudioOutputPort = emergencyAudioOutputPort,
    emergencyAudioFeatureFlags = emergencyAudioFeatureFlags
)

private object AlwaysOnlineNetworkAvailabilityPort : AdsbNetworkAvailabilityPort {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
}

private class DisabledEmergencyAudioSettingsPort : AdsbEmergencyAudioSettingsPort {
    override val emergencyAudioEnabledFlow: StateFlow<Boolean> =
        MutableStateFlow(false).asStateFlow()
    override val emergencyAudioCooldownMsFlow: StateFlow<Long> =
        MutableStateFlow(ADSB_EMERGENCY_AUDIO_DEFAULT_COOLDOWN_MS).asStateFlow()
}
