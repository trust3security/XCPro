package com.trust3.xcpro.livesource

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.di.CondorLiveAirspeedSource
import com.trust3.xcpro.di.PhoneLiveAirspeedSource
import com.trust3.xcpro.weather.wind.data.AirspeedDataSource
import com.trust3.xcpro.weather.wind.model.AirspeedSample
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ResolverSelectedAirspeedDataSource @Inject constructor(
    private val liveSourceStatePort: LiveSourceStatePort,
    @PhoneLiveAirspeedSource phoneSource: AirspeedDataSource,
    @CondorLiveAirspeedSource condorSource: AirspeedDataSource,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : AirspeedDataSource {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val selectedAirspeedSource = liveSourceStatePort.state
        .map { it.selectedAirspeedSource }
        .distinctUntilChanged()

    override val airspeedFlow: StateFlow<AirspeedSample?> = selectedAirspeedSource
        .flatMapLatest { selected ->
            when (selected) {
                SelectedLiveAirspeedSource.PHONE_OR_NONE -> phoneSource.airspeedFlow
                SelectedLiveAirspeedSource.CONDOR_SIMULATOR -> condorSource.airspeedFlow
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = when (liveSourceStatePort.state.value.selectedAirspeedSource) {
                SelectedLiveAirspeedSource.PHONE_OR_NONE -> phoneSource.airspeedFlow.value
                SelectedLiveAirspeedSource.CONDOR_SIMULATOR -> condorSource.airspeedFlow.value
            }
        )
}
