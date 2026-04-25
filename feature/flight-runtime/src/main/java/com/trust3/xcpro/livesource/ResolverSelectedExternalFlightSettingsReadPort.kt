package com.trust3.xcpro.livesource

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.di.CondorLiveExternalFlightSettingsSource
import com.trust3.xcpro.di.DefaultLiveExternalFlightSettingsSource
import com.trust3.xcpro.external.ExternalFlightSettingsReadPort
import com.trust3.xcpro.external.ExternalFlightSettingsSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ResolverSelectedExternalFlightSettingsReadPort @Inject constructor(
    private val liveSourceStatePort: LiveSourceStatePort,
    @DefaultLiveExternalFlightSettingsSource
    private val defaultSource: ExternalFlightSettingsReadPort,
    @CondorLiveExternalFlightSettingsSource
    private val condorSource: ExternalFlightSettingsReadPort,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ExternalFlightSettingsReadPort {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val selectedSource = liveSourceStatePort.state
        .map { it.selectedExternalInstrumentSource }
        .distinctUntilChanged()

    override val externalFlightSettingsSnapshot: StateFlow<ExternalFlightSettingsSnapshot> =
        selectedSource
            .flatMapLatest { selected ->
                when (selected) {
                    SelectedLiveExternalInstrumentSource.DEFAULT_LIVE_EXTERNAL_INSTRUMENT ->
                        defaultSource.externalFlightSettingsSnapshot

                    SelectedLiveExternalInstrumentSource.CONDOR_SIMULATOR ->
                        condorSource.externalFlightSettingsSnapshot
                }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = initialValue()
            )

    private fun initialValue(): ExternalFlightSettingsSnapshot =
        when (liveSourceStatePort.state.value.selectedExternalInstrumentSource) {
            SelectedLiveExternalInstrumentSource.DEFAULT_LIVE_EXTERNAL_INSTRUMENT ->
                defaultSource.externalFlightSettingsSnapshot.value

            SelectedLiveExternalInstrumentSource.CONDOR_SIMULATOR ->
                condorSource.externalFlightSettingsSnapshot.value
        }
}
