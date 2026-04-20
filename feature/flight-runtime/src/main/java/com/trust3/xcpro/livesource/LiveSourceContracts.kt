package com.trust3.xcpro.livesource

import com.trust3.xcpro.simulator.CondorLiveDegradedReason
import kotlinx.coroutines.flow.StateFlow

enum class DesiredLiveMode {
    PHONE_ONLY,
    CONDOR2_FULL
}

interface DesiredLiveModePort {
    val desiredLiveMode: StateFlow<DesiredLiveMode>
}

sealed interface PhoneLiveCapability {
    data object Ready : PhoneLiveCapability
    data class Unavailable(val reason: PhoneLiveCapabilityReason) : PhoneLiveCapability
}

enum class PhoneLiveCapabilityReason {
    LOCATION_PERMISSION_MISSING,
    LOCATION_PROVIDER_DISABLED,
    PLATFORM_RUNTIME_UNAVAILABLE
}

interface PhoneLiveCapabilityPort {
    val capability: StateFlow<PhoneLiveCapability>
    fun refreshAndGetCapability(): PhoneLiveCapability
}

enum class EffectiveLiveSource {
    PHONE,
    CONDOR2
}

enum class SelectedLiveSensorDataSource {
    PHONE_SENSORS,
    CONDOR_SIMULATOR
}

enum class SelectedLiveAirspeedSource {
    PHONE_OR_NONE,
    CONDOR_SIMULATOR
}

enum class LiveStartupRequirement {
    NONE,
    ANDROID_FINE_LOCATION_PERMISSION
}

enum class PhoneLiveDegradedReason {
    LOCATION_PERMISSION_MISSING,
    LOCATION_PROVIDER_DISABLED,
    PLATFORM_RUNTIME_UNAVAILABLE
}

sealed interface LiveSourceStatus {
    data object PhoneReady : LiveSourceStatus
    data class PhoneDegraded(val reason: PhoneLiveDegradedReason) : LiveSourceStatus
    data object CondorReady : LiveSourceStatus
    data class CondorDegraded(val reason: CondorLiveDegradedReason) : LiveSourceStatus
}

enum class LiveSourceKind {
    PHONE,
    SIMULATOR_CONDOR2
}

data class ResolvedLiveSourceState(
    val desiredMode: DesiredLiveMode = DesiredLiveMode.PHONE_ONLY,
    val effectiveSource: EffectiveLiveSource = EffectiveLiveSource.PHONE,
    val selectedSensorDataSource: SelectedLiveSensorDataSource =
        SelectedLiveSensorDataSource.PHONE_SENSORS,
    val selectedAirspeedSource: SelectedLiveAirspeedSource =
        SelectedLiveAirspeedSource.PHONE_OR_NONE,
    val startupRequirement: LiveStartupRequirement = LiveStartupRequirement.NONE,
    val status: LiveSourceStatus = LiveSourceStatus.PhoneReady,
    val kind: LiveSourceKind = LiveSourceKind.PHONE
)

interface LiveSourceStatePort {
    val state: StateFlow<ResolvedLiveSourceState>
    fun refreshAndGetState(): ResolvedLiveSourceState
}
