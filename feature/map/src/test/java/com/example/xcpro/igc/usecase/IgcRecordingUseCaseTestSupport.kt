package com.example.xcpro.igc.usecase

import com.example.xcpro.core.time.Clock
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.igc.IgcRecordingActionSink
import com.example.xcpro.igc.NoopIgcRecordingActionSink
import com.example.xcpro.igc.data.IgcFlightLogRepository
import com.example.xcpro.igc.data.IgcSessionStateSnapshotStore
import com.example.xcpro.igc.data.NoopIgcFlightLogRepository
import com.example.xcpro.igc.domain.IgcRecoveryDiagnosticsReporter
import com.example.xcpro.igc.domain.IgcSessionStateMachine
import com.example.xcpro.igc.domain.NoOpIgcRecoveryDiagnosticsReporter
import com.example.xcpro.sensors.FlightStateSource
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
    recordingActionSink: IgcRecordingActionSink = NoopIgcRecordingActionSink,
    flightLogRepository: IgcFlightLogRepository = NoopIgcFlightLogRepository,
    diagnosticsReporter: IgcRecoveryDiagnosticsReporter = NoOpIgcRecoveryDiagnosticsReporter
): IgcRecordingUseCase = IgcRecordingUseCase(
    flightStateSource = flightStateSource,
    flightDataRepository = flightDataRepository,
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
