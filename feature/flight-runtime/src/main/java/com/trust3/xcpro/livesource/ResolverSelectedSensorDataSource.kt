package com.trust3.xcpro.livesource

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.di.CondorLiveSensorSource
import com.trust3.xcpro.di.PhoneLiveSensorSource
import com.trust3.xcpro.sensors.AccelData
import com.trust3.xcpro.sensors.AttitudeData
import com.trust3.xcpro.sensors.BaroData
import com.trust3.xcpro.sensors.CompassData
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.sensors.RawAccelData
import com.trust3.xcpro.sensors.SensorDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ResolverSelectedSensorDataSource @Inject constructor(
    private val liveSourceStatePort: LiveSourceStatePort,
    @PhoneLiveSensorSource private val phoneSource: SensorDataSource,
    @CondorLiveSensorSource private val condorSource: SensorDataSource,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : SensorDataSource {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val selectedSourceFlow: Flow<SelectedLiveSensorDataSource> = liveSourceStatePort.state
        .map { it.selectedSensorDataSource }
        .distinctUntilChanged()

    override val gpsFlow: StateFlow<GPSData?> = selectedStateFlow(
        phoneFlow = phoneSource.gpsFlow,
        condorFlow = condorSource.gpsFlow
    )
    override val baroFlow: StateFlow<BaroData?> = selectedStateFlow(
        phoneFlow = phoneSource.baroFlow,
        condorFlow = condorSource.baroFlow
    )
    override val compassFlow: StateFlow<CompassData?> = selectedStateFlow(
        phoneFlow = phoneSource.compassFlow,
        condorFlow = condorSource.compassFlow
    )
    override val rawAccelFlow: StateFlow<RawAccelData?> = selectedStateFlow(
        phoneFlow = phoneSource.rawAccelFlow,
        condorFlow = condorSource.rawAccelFlow
    )
    override val accelFlow: StateFlow<AccelData?> = selectedStateFlow(
        phoneFlow = phoneSource.accelFlow,
        condorFlow = condorSource.accelFlow
    )
    override val attitudeFlow: StateFlow<AttitudeData?> = selectedStateFlow(
        phoneFlow = phoneSource.attitudeFlow,
        condorFlow = condorSource.attitudeFlow
    )

    private fun <T> selectedStateFlow(
        phoneFlow: StateFlow<T>,
        condorFlow: StateFlow<T>
    ): StateFlow<T> =
        selectedSourceFlow
            .flatMapLatest { selected ->
                when (selected) {
                    SelectedLiveSensorDataSource.PHONE_SENSORS -> phoneFlow
                    SelectedLiveSensorDataSource.CONDOR_SIMULATOR -> condorFlow
                }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = initialValue(phoneFlow, condorFlow)
            )

    private fun <T> initialValue(
        phoneFlow: StateFlow<T>,
        condorFlow: StateFlow<T>
    ): T =
        when (liveSourceStatePort.state.value.selectedSensorDataSource) {
            SelectedLiveSensorDataSource.PHONE_SENSORS -> phoneFlow.value
            SelectedLiveSensorDataSource.CONDOR_SIMULATOR -> condorFlow.value
        }
}
