package com.trust3.xcpro.livesource

import com.trust3.xcpro.simulator.CondorConnectionState
import com.trust3.xcpro.simulator.CondorLiveDegradedReason
import com.trust3.xcpro.simulator.CondorLiveState
import com.trust3.xcpro.simulator.CondorLiveStatePort
import com.trust3.xcpro.simulator.CondorSessionState
import com.trust3.xcpro.simulator.CondorStreamFreshness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveSourceResolverTest {

    @Test
    fun phoneOnly_mode_uses_phone_contract_when_capable() = runTest {
        val desiredModePort = FakeDesiredLiveModePort(DesiredLiveMode.PHONE_ONLY)
        val phoneCapabilityPort = FakePhoneLiveCapabilityPort(PhoneLiveCapability.Ready)
        val condorLiveStatePort = FakeCondorLiveStatePort()
        val resolver = LiveSourceResolver(
            desiredLiveModePort = desiredModePort,
            phoneLiveCapabilityPort = phoneCapabilityPort,
            condorLiveStatePort = condorLiveStatePort,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        advanceUntilIdle()

        assertResolvedState(
            resolver.state.value,
            desiredMode = DesiredLiveMode.PHONE_ONLY,
            effectiveSource = EffectiveLiveSource.PHONE,
            selectedSensorDataSource = SelectedLiveSensorDataSource.PHONE_SENSORS,
            selectedAirspeedSource = SelectedLiveAirspeedSource.PHONE_OR_NONE,
            selectedExternalInstrumentSource =
                SelectedLiveExternalInstrumentSource.DEFAULT_LIVE_EXTERNAL_INSTRUMENT,
            startupRequirement = LiveStartupRequirement.NONE,
            status = LiveSourceStatus.PhoneReady,
            kind = LiveSourceKind.PHONE
        )
    }

    @Test
    fun phoneOnly_permissionMissing_requires_permission_and_keeps_phone_selection() = runTest {
        val resolver = LiveSourceResolver(
            desiredLiveModePort = FakeDesiredLiveModePort(DesiredLiveMode.PHONE_ONLY),
            phoneLiveCapabilityPort = FakePhoneLiveCapabilityPort(
                PhoneLiveCapability.Unavailable(PhoneLiveCapabilityReason.LOCATION_PERMISSION_MISSING)
            ),
            condorLiveStatePort = FakeCondorLiveStatePort(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        advanceUntilIdle()

        assertResolvedState(
            resolver.state.value,
            desiredMode = DesiredLiveMode.PHONE_ONLY,
            effectiveSource = EffectiveLiveSource.PHONE,
            selectedSensorDataSource = SelectedLiveSensorDataSource.PHONE_SENSORS,
            selectedAirspeedSource = SelectedLiveAirspeedSource.PHONE_OR_NONE,
            selectedExternalInstrumentSource =
                SelectedLiveExternalInstrumentSource.DEFAULT_LIVE_EXTERNAL_INSTRUMENT,
            startupRequirement = LiveStartupRequirement.ANDROID_FINE_LOCATION_PERMISSION,
            status = LiveSourceStatus.PhoneDegraded(
                PhoneLiveDegradedReason.LOCATION_PERMISSION_MISSING
            ),
            kind = LiveSourceKind.PHONE
        )
    }

    @Test
    fun phoneOnly_providerDisabled_degrades_without_permission_prompt() = runTest {
        val resolver = LiveSourceResolver(
            desiredLiveModePort = FakeDesiredLiveModePort(DesiredLiveMode.PHONE_ONLY),
            phoneLiveCapabilityPort = FakePhoneLiveCapabilityPort(
                PhoneLiveCapability.Unavailable(PhoneLiveCapabilityReason.LOCATION_PROVIDER_DISABLED)
            ),
            condorLiveStatePort = FakeCondorLiveStatePort(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        advanceUntilIdle()

        assertResolvedState(
            resolver.state.value,
            desiredMode = DesiredLiveMode.PHONE_ONLY,
            effectiveSource = EffectiveLiveSource.PHONE,
            selectedSensorDataSource = SelectedLiveSensorDataSource.PHONE_SENSORS,
            selectedAirspeedSource = SelectedLiveAirspeedSource.PHONE_OR_NONE,
            selectedExternalInstrumentSource =
                SelectedLiveExternalInstrumentSource.DEFAULT_LIVE_EXTERNAL_INSTRUMENT,
            startupRequirement = LiveStartupRequirement.NONE,
            status = LiveSourceStatus.PhoneDegraded(
                PhoneLiveDegradedReason.LOCATION_PROVIDER_DISABLED
            ),
            kind = LiveSourceKind.PHONE
        )
    }

    @Test
    fun desiredMode_change_updates_full_resolved_contract() = runTest {
        val desiredModePort = FakeDesiredLiveModePort(DesiredLiveMode.PHONE_ONLY)
        val phoneCapabilityPort = FakePhoneLiveCapabilityPort(
            PhoneLiveCapability.Unavailable(PhoneLiveCapabilityReason.LOCATION_PERMISSION_MISSING)
        )
        val condorLiveStatePort = FakeCondorLiveStatePort(
            CondorLiveState(
                session = CondorSessionState(
                    connection = CondorConnectionState.CONNECTED,
                    freshness = CondorStreamFreshness.HEALTHY
                )
            )
        )
        val resolver = LiveSourceResolver(
            desiredLiveModePort = desiredModePort,
            phoneLiveCapabilityPort = phoneCapabilityPort,
            condorLiveStatePort = condorLiveStatePort,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        desiredModePort.setMode(DesiredLiveMode.CONDOR2_FULL)
        advanceUntilIdle()

        assertResolvedState(
            resolver.state.value,
            desiredMode = DesiredLiveMode.CONDOR2_FULL,
            effectiveSource = EffectiveLiveSource.CONDOR2,
            selectedSensorDataSource = SelectedLiveSensorDataSource.CONDOR_SIMULATOR,
            selectedAirspeedSource = SelectedLiveAirspeedSource.CONDOR_SIMULATOR,
            selectedExternalInstrumentSource =
                SelectedLiveExternalInstrumentSource.CONDOR_SIMULATOR,
            startupRequirement = LiveStartupRequirement.NONE,
            status = LiveSourceStatus.CondorReady,
            kind = LiveSourceKind.SIMULATOR_CONDOR2
        )
    }

    @Test
    fun condorMode_reports_degraded_status_without_switching_back_to_phone() = runTest {
        val resolver = LiveSourceResolver(
            desiredLiveModePort = FakeDesiredLiveModePort(DesiredLiveMode.CONDOR2_FULL),
            phoneLiveCapabilityPort = FakePhoneLiveCapabilityPort(PhoneLiveCapability.Ready),
            condorLiveStatePort = FakeCondorLiveStatePort(
                CondorLiveState(
                    session = CondorSessionState(
                        connection = CondorConnectionState.CONNECTED,
                        freshness = CondorStreamFreshness.STALE
                    )
                )
            ),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        advanceUntilIdle()

        assertResolvedState(
            resolver.state.value,
            desiredMode = DesiredLiveMode.CONDOR2_FULL,
            effectiveSource = EffectiveLiveSource.CONDOR2,
            selectedSensorDataSource = SelectedLiveSensorDataSource.CONDOR_SIMULATOR,
            selectedAirspeedSource = SelectedLiveAirspeedSource.CONDOR_SIMULATOR,
            selectedExternalInstrumentSource =
                SelectedLiveExternalInstrumentSource.CONDOR_SIMULATOR,
            startupRequirement = LiveStartupRequirement.NONE,
            status = LiveSourceStatus.CondorDegraded(CondorLiveDegradedReason.STALE_STREAM),
            kind = LiveSourceKind.SIMULATOR_CONDOR2
        )
    }

    @Test
    fun condorMode_ignores_phone_capability_changes_for_selection_and_startup_contract() = runTest {
        val phoneCapabilityPort = FakePhoneLiveCapabilityPort(PhoneLiveCapability.Ready)
        val condorLiveStatePort = FakeCondorLiveStatePort(
            CondorLiveState(
                session = CondorSessionState(
                    connection = CondorConnectionState.CONNECTED,
                    freshness = CondorStreamFreshness.HEALTHY
                )
            )
        )
        val resolver = LiveSourceResolver(
            desiredLiveModePort = FakeDesiredLiveModePort(DesiredLiveMode.CONDOR2_FULL),
            phoneLiveCapabilityPort = phoneCapabilityPort,
            condorLiveStatePort = condorLiveStatePort,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        phoneCapabilityPort.setCapability(
            PhoneLiveCapability.Unavailable(PhoneLiveCapabilityReason.LOCATION_PERMISSION_MISSING)
        )
        advanceUntilIdle()

        assertResolvedState(
            resolver.state.value,
            desiredMode = DesiredLiveMode.CONDOR2_FULL,
            effectiveSource = EffectiveLiveSource.CONDOR2,
            selectedSensorDataSource = SelectedLiveSensorDataSource.CONDOR_SIMULATOR,
            selectedAirspeedSource = SelectedLiveAirspeedSource.CONDOR_SIMULATOR,
            selectedExternalInstrumentSource =
                SelectedLiveExternalInstrumentSource.CONDOR_SIMULATOR,
            startupRequirement = LiveStartupRequirement.NONE,
            status = LiveSourceStatus.CondorReady,
            kind = LiveSourceKind.SIMULATOR_CONDOR2
        )
    }

    @Test
    fun refreshAndGetState_reloads_phone_capability_for_phone_mode() = runTest {
        val phoneCapabilityPort = FakePhoneLiveCapabilityPort(PhoneLiveCapability.Ready)
        phoneCapabilityPort.setRefreshCapability(
            PhoneLiveCapability.Unavailable(PhoneLiveCapabilityReason.LOCATION_PERMISSION_MISSING)
        )
        val resolver = LiveSourceResolver(
            desiredLiveModePort = FakeDesiredLiveModePort(DesiredLiveMode.PHONE_ONLY),
            phoneLiveCapabilityPort = phoneCapabilityPort,
            condorLiveStatePort = FakeCondorLiveStatePort(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        advanceUntilIdle()

        val refreshedState = resolver.refreshAndGetState()

        assertResolvedState(
            refreshedState,
            desiredMode = DesiredLiveMode.PHONE_ONLY,
            effectiveSource = EffectiveLiveSource.PHONE,
            selectedSensorDataSource = SelectedLiveSensorDataSource.PHONE_SENSORS,
            selectedAirspeedSource = SelectedLiveAirspeedSource.PHONE_OR_NONE,
            selectedExternalInstrumentSource =
                SelectedLiveExternalInstrumentSource.DEFAULT_LIVE_EXTERNAL_INSTRUMENT,
            startupRequirement = LiveStartupRequirement.ANDROID_FINE_LOCATION_PERMISSION,
            status = LiveSourceStatus.PhoneDegraded(
                PhoneLiveDegradedReason.LOCATION_PERMISSION_MISSING
            ),
            kind = LiveSourceKind.PHONE
        )
        assertEquals(refreshedState, resolver.state.value)
    }

    private fun assertResolvedState(
        state: ResolvedLiveSourceState,
        desiredMode: DesiredLiveMode,
        effectiveSource: EffectiveLiveSource,
        selectedSensorDataSource: SelectedLiveSensorDataSource,
        selectedAirspeedSource: SelectedLiveAirspeedSource,
        selectedExternalInstrumentSource: SelectedLiveExternalInstrumentSource,
        startupRequirement: LiveStartupRequirement,
        status: LiveSourceStatus,
        kind: LiveSourceKind
    ) {
        assertEquals(desiredMode, state.desiredMode)
        assertEquals(effectiveSource, state.effectiveSource)
        assertEquals(selectedSensorDataSource, state.selectedSensorDataSource)
        assertEquals(selectedAirspeedSource, state.selectedAirspeedSource)
        assertEquals(selectedExternalInstrumentSource, state.selectedExternalInstrumentSource)
        assertEquals(startupRequirement, state.startupRequirement)
        assertEquals(status, state.status)
        assertEquals(kind, state.kind)
    }

    private class FakeDesiredLiveModePort(
        initialMode: DesiredLiveMode
    ) : DesiredLiveModePort {
        private val mutableState = MutableStateFlow(initialMode)

        override val desiredLiveMode: StateFlow<DesiredLiveMode> = mutableState.asStateFlow()

        fun setMode(mode: DesiredLiveMode) {
            mutableState.value = mode
        }
    }

    private class FakePhoneLiveCapabilityPort(
        initialCapability: PhoneLiveCapability
    ) : PhoneLiveCapabilityPort {
        private val mutableState = MutableStateFlow(initialCapability)
        private var refreshedCapability: PhoneLiveCapability? = null

        override val capability: StateFlow<PhoneLiveCapability> = mutableState.asStateFlow()

        override fun refreshAndGetCapability(): PhoneLiveCapability {
            val refreshed = refreshedCapability ?: capability.value
            mutableState.value = refreshed
            refreshedCapability = null
            return refreshed
        }

        fun setCapability(capability: PhoneLiveCapability) {
            mutableState.value = capability
        }

        fun setRefreshCapability(capability: PhoneLiveCapability) {
            refreshedCapability = capability
        }
    }

    private class FakeCondorLiveStatePort(
        initialState: CondorLiveState = CondorLiveState()
    ) : CondorLiveStatePort {
        private val mutableState = MutableStateFlow(initialState)

        override val state: StateFlow<CondorLiveState> = mutableState.asStateFlow()
    }
}
