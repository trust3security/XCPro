package com.trust3.xcpro.variometer.bluetooth.lxnav.runtime

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.common.units.UnitsConverter
import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.external.ExternalFlightSettingsReadPort
import com.trust3.xcpro.external.ExternalFlightSettingsSnapshot
import com.trust3.xcpro.external.ExternalInstrumentFlightSnapshot
import com.trust3.xcpro.external.ExternalInstrumentReadPort
import com.trust3.xcpro.external.TimedExternalValue
import com.trust3.xcpro.weather.wind.data.ExternalAirspeedWritePort
import com.trust3.xcpro.weather.wind.model.AirspeedSample
import com.trust3.xcpro.bluetooth.BluetoothConnectionState
import com.trust3.xcpro.bluetooth.BluetoothReadChunk
import com.trust3.xcpro.bluetooth.BluetoothTransport
import com.trust3.xcpro.bluetooth.BondedBluetoothDevice
import com.trust3.xcpro.variometer.bluetooth.lxnav.LxDeviceInfo
import com.trust3.xcpro.variometer.bluetooth.lxnav.LxParseOutcome
import com.trust3.xcpro.variometer.bluetooth.lxnav.LxSentenceSession
import com.trust3.xcpro.variometer.bluetooth.lxnav.LxWp0Sentence
import com.trust3.xcpro.variometer.bluetooth.lxnav.LxWp1Sentence
import com.trust3.xcpro.variometer.bluetooth.lxnav.LxWp2Sentence
import com.trust3.xcpro.variometer.bluetooth.lxnav.LxWp3Sentence
import com.trust3.xcpro.variometer.bluetooth.lxnav.PlxVfSentence
import com.trust3.xcpro.variometer.bluetooth.lxnav.PlxVsSentence
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class LxExternalRuntimeRepository @Inject constructor(
    private val transport: BluetoothTransport,
    private val clock: Clock,
    private val externalAirspeedWritePort: ExternalAirspeedWritePort,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ExternalInstrumentReadPort, ExternalFlightSettingsReadPort {

    companion object {
        private const val PLXVF_AIRSPEED_PREFERENCE_WINDOW_MS = 3_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val stateMutex = Mutex()
    private val mutableRuntimeSnapshot = MutableStateFlow(LxExternalRuntimeSnapshot())
    private val mutableExternalFlightSnapshot = MutableStateFlow(ExternalInstrumentFlightSnapshot())
    private val mutableExternalFlightSettingsSnapshot = MutableStateFlow(ExternalFlightSettingsSnapshot())

    private var activeTransportJob: Job? = null
    private var activeSentenceSession: LxSentenceSession = LxSentenceSession()
    private var diagnosticsAccumulator: LxSentenceDiagnosticsAccumulator =
        LxSentenceDiagnosticsAccumulator()

    internal val runtimeSnapshot: StateFlow<LxExternalRuntimeSnapshot> =
        mutableRuntimeSnapshot.asStateFlow()

    override val externalFlightSnapshot: StateFlow<ExternalInstrumentFlightSnapshot> =
        mutableExternalFlightSnapshot.asStateFlow()

    override val externalFlightSettingsSnapshot: StateFlow<ExternalFlightSettingsSnapshot> =
        mutableExternalFlightSettingsSnapshot.asStateFlow()

    init {
        scope.launch {
            transport.connectionState.collect { connectionState ->
                stateMutex.withLock {
                    val updatedDiagnostics = when (connectionState) {
                        is BluetoothConnectionState.Error -> diagnosticsAccumulator.withTransportError(
                            mutableRuntimeSnapshot.value.diagnostics,
                            connectionState.error
                        )

                        is BluetoothConnectionState.Connected -> diagnosticsAccumulator.clearTransportError(
                            mutableRuntimeSnapshot.value.diagnostics
                        )

                        BluetoothConnectionState.Disconnected,
                        is BluetoothConnectionState.Connecting -> mutableRuntimeSnapshot.value.diagnostics
                    }
                    publishLocked(
                        mutableRuntimeSnapshot.value.copy(
                            connectionState = connectionState,
                            diagnostics = updatedDiagnostics
                        )
                    )
                }
            }
        }
    }

    suspend fun connect(device: BondedBluetoothDevice) {
        disconnect()

        val nextSession = stateMutex.withLock {
            val sessionOrdinal = mutableRuntimeSnapshot.value.sessionOrdinal + 1L
            val sessionStartMonoMs = clock.nowMonoMs()
            activeSentenceSession = LxSentenceSession()
            diagnosticsAccumulator = LxSentenceDiagnosticsAccumulator()
            publishLocked(
                LxExternalRuntimeSnapshot(
                    activeDeviceAddress = device.address,
                    activeDeviceName = device.displayName,
                    sessionOrdinal = sessionOrdinal,
                    connectionState = transport.connectionState.value,
                    diagnostics = diagnosticsAccumulator.reset(sessionStartMonoMs)
                )
            )
            sessionOrdinal
        }

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                transport.open(device).collect { chunk ->
                    handleChunk(nextSession, chunk)
                }
            } finally {
                onSessionTerminated(nextSession)
            }
        }

        stateMutex.withLock {
            activeTransportJob = job
        }
        job.start()
    }

    suspend fun disconnect() {
        val job = stateMutex.withLock {
            activeTransportJob.also { activeTransportJob = null }
        }

        transport.close()
        job?.cancelAndJoin()

        stateMutex.withLock {
            activeSentenceSession = LxSentenceSession()
            clearRuntimeStateLocked(transport.connectionState.value)
        }
    }

    private suspend fun handleChunk(
        sessionOrdinal: Long,
        chunk: BluetoothReadChunk
    ) {
        val outcomes = stateMutex.withLock {
            if (mutableRuntimeSnapshot.value.sessionOrdinal != sessionOrdinal) {
                emptyList()
            } else {
                publishLocked(
                    mutableRuntimeSnapshot.value.copy(
                        diagnostics = diagnosticsAccumulator.onChunkReceived(
                            mutableRuntimeSnapshot.value.diagnostics,
                            chunk.receivedMonoMs
                        )
                    )
                )
                activeSentenceSession.onChunk(chunk)
            }
        }

        if (outcomes.isEmpty()) return

        stateMutex.withLock {
            if (mutableRuntimeSnapshot.value.sessionOrdinal != sessionOrdinal) return

            var snapshot = mutableRuntimeSnapshot.value
            outcomes.forEach { outcome ->
                snapshot = applyOutcome(snapshot, outcome)
            }
            snapshot = snapshot.copy(
                diagnostics = diagnosticsAccumulator.onOutcomes(
                    snapshot.diagnostics,
                    outcomes
                )
            )
            publishLocked(snapshot)
        }
    }

    private fun applyOutcome(
        current: LxExternalRuntimeSnapshot,
        outcome: LxParseOutcome
    ): LxExternalRuntimeSnapshot =
        when (outcome) {
            is LxParseOutcome.Accepted -> when (val sentence = outcome.sentence) {
                is LxWp0Sentence -> current.copy(
                    pressureAltitudeM = sentence.pressureAltitudeM?.let {
                        LxTimedValue(it, outcome.receivedMonoMs)
                    } ?: current.pressureAltitudeM,
                    totalEnergyVarioMps = sentence.totalEnergyVarioMps?.let {
                        LxTimedValue(it, outcome.receivedMonoMs)
                    } ?: current.totalEnergyVarioMps,
                    airspeedKph = resolvePreferredAirspeedAfterLxWp0(
                        current = current,
                        lxwp0AirspeedKph = sentence.airspeedKph,
                        receivedMonoMs = outcome.receivedMonoMs
                    ),
                    lastAcceptedMonoMs = outcome.receivedMonoMs
                )

                is LxWp1Sentence -> current.copy(
                    deviceInfo = mergeDeviceInfo(current.deviceInfo, sentence.deviceInfo),
                    lastAcceptedMonoMs = outcome.receivedMonoMs
                )

                is LxWp2Sentence -> current.copy(
                    liveSettingsOverrides = current.liveSettingsOverrides.copy(
                        macCreadyMps = sentence.macCreadyMps?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.liveSettingsOverrides.macCreadyMps,
                        bugsPercent = sentence.bugsPercent?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.liveSettingsOverrides.bugsPercent,
                        ballastOverloadFactor = sentence.ballastOverloadFactor?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.liveSettingsOverrides.ballastOverloadFactor
                    ),
                    deviceConfiguration = current.deviceConfiguration.copy(
                        polarA = sentence.polarA?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.polarA,
                        polarB = sentence.polarB?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.polarB,
                        polarC = sentence.polarC?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.polarC,
                        audioVolume = sentence.audioVolume?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.audioVolume
                    ),
                    lastAcceptedMonoMs = outcome.receivedMonoMs
                )

                is LxWp3Sentence -> current.copy(
                    liveSettingsOverrides = current.liveSettingsOverrides.copy(
                        qnhHpa = sentence.qnhHpa?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.liveSettingsOverrides.qnhHpa
                    ),
                    deviceConfiguration = current.deviceConfiguration.copy(
                        altitudeOffsetFeet = sentence.altitudeOffsetFeet?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.altitudeOffsetFeet,
                        scMode = sentence.scMode?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.scMode,
                        varioFilter = sentence.varioFilter?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.varioFilter,
                        teFilter = sentence.teFilter?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.teFilter,
                        teLevel = sentence.teLevel?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.teLevel,
                        varioAverage = sentence.varioAverage?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.varioAverage,
                        varioRange = sentence.varioRange?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.varioRange,
                        scTab = sentence.scTab?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.scTab,
                        scLow = sentence.scLow?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.scLow,
                        scSpeed = sentence.scSpeed?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.scSpeed,
                        smartDiff = sentence.smartDiff?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.smartDiff,
                        gliderName = sentence.gliderName?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.gliderName,
                        timeOffsetMinutes = sentence.timeOffsetMinutes?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.deviceConfiguration.timeOffsetMinutes
                    ),
                    lastAcceptedMonoMs = outcome.receivedMonoMs
                )

                is PlxVfSentence -> current.copy(
                    pressureAltitudeM = sentence.pressureAltitudeM?.let {
                        LxTimedValue(it, outcome.receivedMonoMs)
                    } ?: current.pressureAltitudeM,
                    externalVarioMps = sentence.provisionalVarioMps?.let {
                        LxTimedValue(it, outcome.receivedMonoMs)
                    } ?: current.externalVarioMps,
                    airspeedKph = sentence.indicatedAirspeedKph?.let {
                        LxTimedValue(it, outcome.receivedMonoMs)
                    } ?: current.airspeedKph,
                    plxvfIasKph = sentence.indicatedAirspeedKph?.let {
                        LxTimedValue(it, outcome.receivedMonoMs)
                    } ?: current.plxvfIasKph,
                    lastAcceptedMonoMs = outcome.receivedMonoMs
                )

                is PlxVsSentence -> current.copy(
                    environmentStatus = current.environmentStatus.copy(
                        outsideAirTemperatureC = sentence.outsideAirTemperatureC?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.environmentStatus.outsideAirTemperatureC,
                        mode = sentence.mode?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.environmentStatus.mode,
                        voltageV = sentence.voltageV?.let {
                            LxTimedValue(it, outcome.receivedMonoMs)
                        } ?: current.environmentStatus.voltageV
                    ),
                    lastAcceptedMonoMs = outcome.receivedMonoMs
                )
            }

            is LxParseOutcome.KnownUnsupported,
            is LxParseOutcome.Rejected,
            is LxParseOutcome.UnknownSentence -> current
        }

    private fun resolvePreferredAirspeedAfterLxWp0(
        current: LxExternalRuntimeSnapshot,
        lxwp0AirspeedKph: Double?,
        receivedMonoMs: Long
    ): LxTimedValue<Double>? {
        val preferredPlxvf = current.plxvfIasKph
            ?.takeIf { receivedMonoMs - it.receivedMonoMs in 0L..PLXVF_AIRSPEED_PREFERENCE_WINDOW_MS }
        if (preferredPlxvf != null) return preferredPlxvf
        return lxwp0AirspeedKph?.let { LxTimedValue(it, receivedMonoMs) } ?: current.airspeedKph
    }

    private fun mergeDeviceInfo(
        current: LxDeviceInfo?,
        update: LxDeviceInfo
    ): LxDeviceInfo? {
        if (current == null && update.isEmpty()) return null
        return LxDeviceInfo(
            product = update.product ?: current?.product,
            serial = update.serial ?: current?.serial,
            softwareVersion = update.softwareVersion ?: current?.softwareVersion,
            hardwareVersion = update.hardwareVersion ?: current?.hardwareVersion
        ).takeUnless { it.isEmpty() }
    }

    private suspend fun onSessionTerminated(sessionOrdinal: Long) {
        stateMutex.withLock {
            if (mutableRuntimeSnapshot.value.sessionOrdinal != sessionOrdinal) return
            activeTransportJob = null
            activeSentenceSession = LxSentenceSession()
            clearRuntimeStateLocked(transport.connectionState.value)
        }
    }

    private fun clearRuntimeStateLocked(
        connectionState: BluetoothConnectionState
    ) {
        val preservedTransportError = mutableRuntimeSnapshot.value.diagnostics.lastTransportError
        publishLocked(
            mutableRuntimeSnapshot.value.copy(
                activeDeviceAddress = null,
                activeDeviceName = null,
                connectionState = connectionState,
                pressureAltitudeM = null,
                totalEnergyVarioMps = null,
                externalVarioMps = null,
                airspeedKph = null,
                plxvfIasKph = null,
                deviceInfo = null,
                liveSettingsOverrides = LxLiveSettingsOverrides(),
                environmentStatus = LxEnvironmentStatus(),
                deviceConfiguration = LxDeviceConfigurationStatus(),
                lastAcceptedMonoMs = null,
                diagnostics = diagnosticsAccumulator.clearSession(preservedTransportError)
            )
        )
    }

    private fun publishLocked(snapshot: LxExternalRuntimeSnapshot) {
        mutableRuntimeSnapshot.value = snapshot
        mutableExternalFlightSnapshot.value = ExternalInstrumentFlightSnapshot(
            pressureAltitudeM = snapshot.pressureAltitudeM?.let {
                TimedExternalValue(value = it.value, receivedMonoMs = it.receivedMonoMs)
            },
            totalEnergyVarioMps = snapshot.totalEnergyVarioMps?.let {
                TimedExternalValue(value = it.value, receivedMonoMs = it.receivedMonoMs)
            },
            externalVarioMps = snapshot.externalVarioMps?.let {
                TimedExternalValue(value = it.value, receivedMonoMs = it.receivedMonoMs)
            }
        )
        mutableExternalFlightSettingsSnapshot.value = ExternalFlightSettingsSnapshot(
            macCreadyMps = snapshot.liveSettingsOverrides.macCreadyMps?.value,
            bugsPercent = snapshot.liveSettingsOverrides.bugsPercent?.value,
            ballastOverloadFactor = snapshot.liveSettingsOverrides.ballastOverloadFactor?.value,
            qnhHpa = snapshot.liveSettingsOverrides.qnhHpa?.value,
            outsideAirTemperatureC = snapshot.environmentStatus.outsideAirTemperatureC?.value
        )
        snapshot.toExternalAirspeedSample()
            ?.let(externalAirspeedWritePort::updateAirspeed)
            ?: externalAirspeedWritePort.clear()
    }

    private fun LxExternalRuntimeSnapshot.toExternalAirspeedSample(): AirspeedSample? {
        val airspeed = airspeedKph ?: return null
        val iasMs = UnitsConverter.kmhToMs(airspeed.value)
        if (!iasMs.isFinite() || iasMs <= 0.0) return null
        return AirspeedSample.iasOnly(
            indicatedMs = iasMs,
            clockMillis = airspeed.receivedMonoMs
        )
    }
}


