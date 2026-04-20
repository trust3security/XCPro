package com.trust3.xcpro.livesource

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.simulator.CondorConnectionState
import com.trust3.xcpro.simulator.CondorLiveState
import com.trust3.xcpro.simulator.CondorLiveStatePort
import com.trust3.xcpro.simulator.CondorStreamFreshness
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Singleton
class LiveSourceResolver @Inject constructor(
    private val desiredLiveModePort: DesiredLiveModePort,
    private val phoneLiveCapabilityPort: PhoneLiveCapabilityPort,
    private val condorLiveStatePort: CondorLiveStatePort,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : LiveSourceStatePort {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val stateFlow = MutableStateFlow(currentResolvedState())

    override val state: StateFlow<ResolvedLiveSourceState> = stateFlow.asStateFlow()

    init {
        combine(
            desiredLiveModePort.desiredLiveMode,
            phoneLiveCapabilityPort.capability,
            condorLiveStatePort.state
        ) { desiredMode, phoneCapability, condorState ->
            resolveState(
                desiredMode = desiredMode,
                phoneCapability = phoneCapability,
                condorState = condorState
            )
        }.onEach { resolvedState ->
            stateFlow.value = resolvedState
        }.launchIn(scope)
    }

    override fun refreshAndGetState(): ResolvedLiveSourceState {
        val refreshedState = currentResolvedState(
            phoneCapability = phoneLiveCapabilityPort.refreshAndGetCapability()
        )
        stateFlow.value = refreshedState
        return refreshedState
    }

    private fun currentResolvedState(
        phoneCapability: PhoneLiveCapability = phoneLiveCapabilityPort.capability.value
    ): ResolvedLiveSourceState {
        return resolveState(
            desiredMode = desiredLiveModePort.desiredLiveMode.value,
            phoneCapability = phoneCapability,
            condorState = condorLiveStatePort.state.value
        )
    }

    private fun resolveState(
        desiredMode: DesiredLiveMode,
        phoneCapability: PhoneLiveCapability,
        condorState: CondorLiveState
    ): ResolvedLiveSourceState =
        when (desiredMode) {
            DesiredLiveMode.PHONE_ONLY -> resolvePhoneState(desiredMode, phoneCapability)
            DesiredLiveMode.CONDOR2_FULL -> resolveCondorState(desiredMode, condorState)
        }

    private fun resolvePhoneState(
        desiredMode: DesiredLiveMode,
        phoneCapability: PhoneLiveCapability
    ): ResolvedLiveSourceState {
        val status = when (phoneCapability) {
            PhoneLiveCapability.Ready -> LiveSourceStatus.PhoneReady
            is PhoneLiveCapability.Unavailable -> LiveSourceStatus.PhoneDegraded(
                phoneCapability.reason.toDegradedReason()
            )
        }
        val startupRequirement = when (phoneCapability) {
            is PhoneLiveCapability.Unavailable ->
                if (phoneCapability.reason == PhoneLiveCapabilityReason.LOCATION_PERMISSION_MISSING) {
                    LiveStartupRequirement.ANDROID_FINE_LOCATION_PERMISSION
                } else {
                    LiveStartupRequirement.NONE
                }

            PhoneLiveCapability.Ready -> LiveStartupRequirement.NONE
        }
        return ResolvedLiveSourceState(
            desiredMode = desiredMode,
            effectiveSource = EffectiveLiveSource.PHONE,
            selectedSensorDataSource = SelectedLiveSensorDataSource.PHONE_SENSORS,
            selectedAirspeedSource = SelectedLiveAirspeedSource.PHONE_OR_NONE,
            selectedExternalInstrumentSource =
                SelectedLiveExternalInstrumentSource.DEFAULT_LIVE_EXTERNAL_INSTRUMENT,
            startupRequirement = startupRequirement,
            status = status,
            kind = LiveSourceKind.PHONE
        )
    }

    private fun resolveCondorState(
        desiredMode: DesiredLiveMode,
        condorState: CondorLiveState
    ): ResolvedLiveSourceState {
        return ResolvedLiveSourceState(
            desiredMode = desiredMode,
            effectiveSource = EffectiveLiveSource.CONDOR2,
            selectedSensorDataSource = SelectedLiveSensorDataSource.CONDOR_SIMULATOR,
            selectedAirspeedSource = SelectedLiveAirspeedSource.CONDOR_SIMULATOR,
            selectedExternalInstrumentSource =
                SelectedLiveExternalInstrumentSource.CONDOR_SIMULATOR,
            startupRequirement = LiveStartupRequirement.NONE,
            status = condorState.toStatus(),
            kind = LiveSourceKind.SIMULATOR_CONDOR2
        )
    }

    private fun CondorLiveState.toStatus(): LiveSourceStatus {
        lastFailure?.let { return LiveSourceStatus.CondorDegraded(it) }
        if (session.connection == CondorConnectionState.CONNECTED &&
            session.freshness == CondorStreamFreshness.HEALTHY
        ) {
            return LiveSourceStatus.CondorReady
        }
        if (session.freshness == CondorStreamFreshness.STALE) {
            return LiveSourceStatus.CondorDegraded(
                com.trust3.xcpro.simulator.CondorLiveDegradedReason.STALE_STREAM
            )
        }
        return LiveSourceStatus.CondorDegraded(
            com.trust3.xcpro.simulator.CondorLiveDegradedReason.DISCONNECTED
        )
    }

    private fun PhoneLiveCapabilityReason.toDegradedReason(): PhoneLiveDegradedReason =
        when (this) {
            PhoneLiveCapabilityReason.LOCATION_PERMISSION_MISSING ->
                PhoneLiveDegradedReason.LOCATION_PERMISSION_MISSING

            PhoneLiveCapabilityReason.LOCATION_PROVIDER_DISABLED ->
                PhoneLiveDegradedReason.LOCATION_PROVIDER_DISABLED

            PhoneLiveCapabilityReason.PLATFORM_RUNTIME_UNAVAILABLE ->
                PhoneLiveDegradedReason.PLATFORM_RUNTIME_UNAVAILABLE
        }
}
