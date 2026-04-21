package com.trust3.xcpro.igc.usecase

import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.flightdata.FlightDataRepository
import com.trust3.xcpro.igc.IgcRecordingActionSink
import com.trust3.xcpro.igc.NoopIgcRecordingActionSink
import com.trust3.xcpro.igc.data.IgcFlightLogRepository
import com.trust3.xcpro.igc.data.IgcSessionStateSnapshotStore
import com.trust3.xcpro.igc.data.NoopIgcFlightLogRepository
import com.trust3.xcpro.igc.domain.IgcRecoveryDiagnosticsReporter
import com.trust3.xcpro.igc.domain.IgcSessionStateMachine
import com.trust3.xcpro.igc.domain.NoOpIgcRecoveryDiagnosticsReporter
import com.trust3.xcpro.livesource.LiveSourceKind
import com.trust3.xcpro.livesource.LiveSourceStatePort
import com.trust3.xcpro.livesource.ResolvedLiveSourceState
import com.trust3.xcpro.sensors.FlightStateSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

internal fun buildIgcRecordingUseCase(
    flightStateSource: FlightStateSource,
    flightDataRepository: FlightDataRepository,
    clock: Clock,
    snapshotStore: IgcSessionStateSnapshotStore,
    defaultDispatcher: CoroutineDispatcher,
    config: IgcSessionStateMachine.Config,
    liveSourceStatePort: LiveSourceStatePort = fixedLiveSourceStatePort(),
    recordingActionSink: IgcRecordingActionSink = NoopIgcRecordingActionSink,
    flightLogRepository: IgcFlightLogRepository = NoopIgcFlightLogRepository,
    diagnosticsReporter: IgcRecoveryDiagnosticsReporter = NoOpIgcRecoveryDiagnosticsReporter
): IgcRecordingUseCase = IgcRecordingUseCase(
    flightStateSource = flightStateSource,
    flightDataRepository = flightDataRepository,
    liveSourceStatePort = liveSourceStatePort,
    clock = clock,
    snapshotStore = snapshotStore,
    scope = CoroutineScope(SupervisorJob() + defaultDispatcher),
    config = config,
    recordingActionSink = recordingActionSink,
    flightLogRepository = flightLogRepository,
    recoveryBootstrapUseCase = IgcRecoveryBootstrapUseCase(
        flightLogRepository = flightLogRepository,
        diagnosticsReporter = diagnosticsReporter
    )
)

internal fun fixedLiveSourceStatePort(
    kind: LiveSourceKind = LiveSourceKind.PHONE
): LiveSourceStatePort {
    return object : LiveSourceStatePort {
        override val state = MutableStateFlow(
            ResolvedLiveSourceState(kind = kind)
        )

        override fun refreshAndGetState(): ResolvedLiveSourceState = state.value
    }
}
