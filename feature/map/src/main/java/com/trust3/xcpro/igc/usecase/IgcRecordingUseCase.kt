package com.trust3.xcpro.igc.usecase

import com.trust3.xcpro.di.IgcRuntimeScope
import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.flightdata.FlightDataRepository
import com.trust3.xcpro.igc.IgcRecordingActionSink
import com.trust3.xcpro.igc.data.IgcFlightLogRepository
import com.trust3.xcpro.igc.data.IgcSessionStateSnapshotStore
import com.trust3.xcpro.igc.domain.IgcBRecordCadencePolicy
import com.trust3.xcpro.igc.domain.IgcLiveSample
import com.trust3.xcpro.igc.domain.IgcRecordFormatter
import com.trust3.xcpro.igc.domain.IgcSampleToBRecordMapper
import com.trust3.xcpro.igc.domain.IgcSamplingState
import com.trust3.xcpro.igc.domain.IgcSessionStateMachine
import com.trust3.xcpro.livesource.LiveSourceKind
import com.trust3.xcpro.livesource.LiveSourceStatePort
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.sensors.FlightStateSource
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runtime bridge from flight-state SSOT to IGC session state transitions.
 *
 * The use case is intentionally thin: business transition rules stay inside
 * [IgcSessionStateMachine], while this class handles flow wiring only.
 */
class IgcRecordingUseCase private constructor(
    private val flightStateSource: FlightStateSource,
    private val flightDataRepository: FlightDataRepository,
    private val liveSourceStatePort: LiveSourceStatePort,
    private val clock: Clock,
    private val snapshotStore: IgcSessionStateSnapshotStore,
    private val scope: CoroutineScope,
    config: IgcSessionStateMachine.Config,
    private val bRecordCadencePolicy: IgcBRecordCadencePolicy,
    private val bRecordMapper: IgcSampleToBRecordMapper,
    private val formatter: IgcRecordFormatter,
    private val recordingActionSink: IgcRecordingActionSink,
    private val flightLogRepository: IgcFlightLogRepository,
    private val recoveryBootstrapUseCase: IgcRecoveryBootstrapUseCase
) {

    @Inject
    constructor(
        flightStateSource: FlightStateSource,
        flightDataRepository: FlightDataRepository,
        liveSourceStatePort: LiveSourceStatePort,
        clock: Clock,
        snapshotStore: IgcSessionStateSnapshotStore,
        recordingActionSink: IgcRecordingActionSink,
        flightLogRepository: IgcFlightLogRepository,
        recoveryBootstrapUseCase: IgcRecoveryBootstrapUseCase,
        @IgcRuntimeScope scope: CoroutineScope
    ) : this(
        flightStateSource = flightStateSource,
        flightDataRepository = flightDataRepository,
        liveSourceStatePort = liveSourceStatePort,
        clock = clock,
        snapshotStore = snapshotStore,
        scope = scope,
        config = IgcSessionStateMachine.Config(),
        bRecordCadencePolicy = IgcBRecordCadencePolicy(),
        bRecordMapper = IgcSampleToBRecordMapper(),
        formatter = IgcRecordFormatter(),
        recordingActionSink = recordingActionSink,
        flightLogRepository = flightLogRepository,
        recoveryBootstrapUseCase = recoveryBootstrapUseCase
    )

    internal constructor(
        flightStateSource: FlightStateSource,
        flightDataRepository: FlightDataRepository,
        liveSourceStatePort: LiveSourceStatePort,
        clock: Clock,
        snapshotStore: IgcSessionStateSnapshotStore,
        scope: CoroutineScope,
        config: IgcSessionStateMachine.Config,
        recordingActionSink: IgcRecordingActionSink,
        flightLogRepository: IgcFlightLogRepository,
        recoveryBootstrapUseCase: IgcRecoveryBootstrapUseCase
    ) : this(
        flightStateSource = flightStateSource,
        flightDataRepository = flightDataRepository,
        liveSourceStatePort = liveSourceStatePort,
        clock = clock,
        snapshotStore = snapshotStore,
        scope = scope,
        config = config,
        bRecordCadencePolicy = IgcBRecordCadencePolicy(),
        bRecordMapper = IgcSampleToBRecordMapper(),
        formatter = IgcRecordFormatter(),
        recordingActionSink = recordingActionSink,
        flightLogRepository = flightLogRepository,
        recoveryBootstrapUseCase = recoveryBootstrapUseCase
    )

    // Singleton runtime owner; lifetime is provided explicitly via DI.
    private val stateMachine: IgcSessionStateMachine = restoreOrCreateStateMachine(config)

    private val _state = MutableStateFlow(stateMachine.currentState())
    val state: StateFlow<IgcSessionStateMachine.State> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<IgcSessionStateMachine.Action>(
        extraBufferCapacity = 32
    )
    val actions: SharedFlow<IgcSessionStateMachine.Action> = _actions.asSharedFlow()
    private val _bRecordLines = MutableSharedFlow<String>(
        extraBufferCapacity = 256
    )
    val bRecordLines: SharedFlow<String> = _bRecordLines.asSharedFlow()

    private val samplingStateRef = AtomicReference(IgcSamplingState())

    init {
        scope.launch {
            flightStateSource.flightState.collect { flightState ->
                if (!allowsIgcLiveInput()) return@collect
                val signal = IgcSessionStateMachine.FlightSignal(
                    monoTimeMs = clock.nowMonoMs(),
                    isFlying = flightState.isFlying,
                    onGround = flightState.onGround,
                    hasFix = flightState.isFlying || flightState.onGround
                )
                applyTransition(stateMachine.onFlightSignal(signal))
            }
        }
        scope.launch {
            flightDataRepository.flightData.collect { sample ->
                if (sample == null) return@collect
                if (!allowsIgcLiveInput()) return@collect
                if (!isSessionRecording()) return@collect
                processFlightSample(sample)
            }
        }
    }

    private fun restoreOrCreateStateMachine(
        config: IgcSessionStateMachine.Config
    ): IgcSessionStateMachine {
        val snapshot = runCatching { snapshotStore.loadSnapshot() }.getOrNull()
        return if (snapshot == null) {
            IgcSessionStateMachine(config = config)
        } else if (snapshot.state.phase == IgcSessionStateMachine.Phase.Completed ||
            snapshot.state.phase == IgcSessionStateMachine.Phase.Failed
        ) {
            snapshotStore.clearSnapshot()
            IgcSessionStateMachine(
                config = config,
                initialSessionId = snapshot.nextSessionId
            )
        } else if (snapshot.state.phase == IgcSessionStateMachine.Phase.Recording ||
            snapshot.state.phase == IgcSessionStateMachine.Phase.Finalizing
        ) {
            when (recoveryBootstrapUseCase.bootstrap(snapshot)) {
                is IgcRecoveryBootstrapUseCase.BootstrapResult.Recovered,
                is IgcRecoveryBootstrapUseCase.BootstrapResult.TerminalFailure -> {
                    snapshotStore.clearSnapshot()
                    IgcSessionStateMachine(
                        config = config,
                        initialSessionId = snapshot.nextSessionId
                    )
                }
                is IgcRecoveryBootstrapUseCase.BootstrapResult.ResumeExisting,
                is IgcRecoveryBootstrapUseCase.BootstrapResult.Unsupported -> {
                    IgcSessionStateMachine.fromSnapshot(snapshot, config)
                }
            }
        } else {
            IgcSessionStateMachine.fromSnapshot(snapshot, config)
        }
    }

    private fun persistState() {
        val currentState = stateMachine.currentState()
        if (currentState.phase == IgcSessionStateMachine.Phase.Completed ||
            currentState.phase == IgcSessionStateMachine.Phase.Failed
        ) {
            snapshotStore.clearSnapshot()
            return
        }
        runCatching {
            snapshotStore.saveSnapshot(stateMachine.snapshot())
        }
    }

    fun onFinalizeSucceeded() {
        val sessionId = activeFinalizingSessionIdOrNull()
        applyTransition(stateMachine.onFinalizeSucceeded())
        sessionId?.let(::cleanupRecoveryArtifacts)
    }

    fun onFinalizeFailed(reason: String) {
        val sessionId = activeFinalizingSessionIdOrNull()
        applyTransition(stateMachine.onFinalizeFailed(reason))
        sessionId?.let(::cleanupRecoveryArtifacts)
    }

    fun snapshot(): IgcSessionStateMachine.Snapshot = stateMachine.snapshot()

    private fun applyTransition(transition: IgcSessionStateMachine.Transition) {
        if (transition.state == _state.value && transition.actions.isEmpty()) {
            return
        }
        _state.value = transition.state
        transition.actions.forEach { action ->
            _actions.tryEmit(action)
            when (action) {
                is IgcSessionStateMachine.Action.StartRecording -> {
                    samplingStateRef.set(IgcSamplingState())
                }
                is IgcSessionStateMachine.Action.MarkCompleted,
                is IgcSessionStateMachine.Action.MarkFailed -> {
                    samplingStateRef.set(IgcSamplingState())
                }
                else -> Unit
            }
        }
        persistState()
    }

    private fun processFlightSample(sample: CompleteFlightData) {
        val liveSample = sample.toIgcLiveSample()
        val currentSamplingState = samplingStateRef.get()
        if (!bRecordCadencePolicy.shouldEmit(
                sampleWallTimeMs = liveSample.sampleWallTimeMs,
                lastEmissionWallTimeMs = currentSamplingState.lastEmissionWallTimeMs
            )
        ) {
            return
        }

        val mapped = bRecordMapper.map(
            sample = liveSample,
            state = currentSamplingState
        )
        val bRecord = mapped.bRecord ?: return
        val line = formatter.formatB(
            record = bRecord,
            definitions = IgcRecordFormatter.IAS_TAS_EXTENSIONS
        )
        samplingStateRef.set(
            mapped.nextState.copy(lastEmissionWallTimeMs = liveSample.sampleWallTimeMs)
        )
        _bRecordLines.tryEmit(line)
        _state.value.activeSessionId?.let { sessionId ->
            recordingActionSink.onBRecord(
                sessionId = sessionId,
                line = line,
                sampleWallTimeMs = liveSample.sampleWallTimeMs
            )
        }
    }

    private fun isSessionRecording(): Boolean {
        return when (_state.value.phase) {
            IgcSessionStateMachine.Phase.Recording,
            IgcSessionStateMachine.Phase.Finalizing -> true
            else -> false
        }
    }

    private fun allowsIgcLiveInput(): Boolean {
        return flightDataRepository.activeSource.value == FlightDataRepository.Source.LIVE &&
            liveSourceStatePort.state.value.kind == LiveSourceKind.PHONE
    }

    private fun activeFinalizingSessionIdOrNull(): Long? {
        val currentState = stateMachine.currentState()
        return if (currentState.phase == IgcSessionStateMachine.Phase.Finalizing) {
            currentState.activeSessionId
        } else {
            null
        }
    }

    private fun cleanupRecoveryArtifacts(sessionId: Long) {
        runCatching { flightLogRepository.deleteRecoveryArtifacts(sessionId) }
    }

    private fun CompleteFlightData.toIgcLiveSample(): IgcLiveSample {
        return IgcLiveSample(
            sampleWallTimeMs = timestamp,
            gpsWallTimeMs = gps?.timestamp,
            baroWallTimeMs = baro?.timestamp,
            latitudeDegrees = gps?.latitude,
            longitudeDegrees = gps?.longitude,
            horizontalAccuracyMeters = gps?.accuracy?.toDouble(),
            pressureAltitudeMeters = pressureAltitude.value,
            gnssAltitudeMeters = gps?.altitude?.value,
            indicatedAirspeedMs = indicatedAirspeed.value.takeIf { it.isFinite() },
            trueAirspeedMs = trueAirspeed.value.takeIf { it.isFinite() }
        )
    }
}
