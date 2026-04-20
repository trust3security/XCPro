package com.trust3.xcpro.livesource

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.di.CondorLiveExternalInstrumentSource
import com.trust3.xcpro.di.DefaultLiveExternalInstrumentSource
import com.trust3.xcpro.external.ExternalInstrumentFlightSnapshot
import com.trust3.xcpro.external.ExternalInstrumentReadPort
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
class ResolverSelectedExternalInstrumentReadPort @Inject constructor(
    private val liveSourceStatePort: LiveSourceStatePort,
    @DefaultLiveExternalInstrumentSource private val defaultSource: ExternalInstrumentReadPort,
    @CondorLiveExternalInstrumentSource private val condorSource: ExternalInstrumentReadPort,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ExternalInstrumentReadPort {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val selectedSource = liveSourceStatePort.state
        .map { it.selectedExternalInstrumentSource }
        .distinctUntilChanged()

    override val externalFlightSnapshot: StateFlow<ExternalInstrumentFlightSnapshot> =
        selectedSource
            .flatMapLatest { selected ->
                when (selected) {
                    SelectedLiveExternalInstrumentSource.DEFAULT_LIVE_EXTERNAL_INSTRUMENT ->
                        defaultSource.externalFlightSnapshot

                    SelectedLiveExternalInstrumentSource.CONDOR_SIMULATOR ->
                        condorSource.externalFlightSnapshot
                }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = initialValue()
            )

    private fun initialValue(): ExternalInstrumentFlightSnapshot =
        when (liveSourceStatePort.state.value.selectedExternalInstrumentSource) {
            SelectedLiveExternalInstrumentSource.DEFAULT_LIVE_EXTERNAL_INSTRUMENT ->
                defaultSource.externalFlightSnapshot.value

            SelectedLiveExternalInstrumentSource.CONDOR_SIMULATOR ->
                condorSource.externalFlightSnapshot.value
        }
}
