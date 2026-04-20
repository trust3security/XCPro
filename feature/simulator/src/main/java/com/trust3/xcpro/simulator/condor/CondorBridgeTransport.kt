package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.bluetooth.BluetoothConnectionError
import com.trust3.xcpro.bluetooth.BluetoothConnectionState
import com.trust3.xcpro.bluetooth.BluetoothTransport
import com.trust3.xcpro.bluetooth.BondedBluetoothDevice
import com.trust3.xcpro.bluetooth.NmeaLine
import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.simulator.CondorBridgeRef
import com.trust3.xcpro.simulator.CondorConnectionState
import com.trust3.xcpro.simulator.CondorFramingState
import com.trust3.xcpro.simulator.CondorLiveDegradedReason
import com.trust3.xcpro.simulator.CondorReconnectState
import com.trust3.xcpro.simulator.CondorSessionState
import com.trust3.xcpro.simulator.CondorStreamFreshness
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class CondorTransportState(
    val activeBridge: CondorBridgeRef? = null,
    val session: CondorSessionState = CondorSessionState(),
    val reconnect: CondorReconnectState = CondorReconnectState.IDLE,
    val lastFailure: CondorLiveDegradedReason? = null
)

@Singleton
internal class CondorBridgeTransport @Inject constructor(
    private val bluetoothTransport: BluetoothTransport,
    private val clock: Clock,
    private val liveSampleRepository: CondorLiveSampleRepository,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val commandMutex = Mutex()
    private val framer = CondorSentenceFramer()
    private val mutableState = MutableStateFlow(CondorTransportState())

    private var currentTargetBridge: CondorBridgeRef? = null
    private var connectedOnceInTarget: Boolean = false
    private var suppressNextDisconnect: Boolean = false
    private var manualDisconnect: Boolean = false
    private var reconnectAttemptCount: Int = 0
    private var sessionJob: Job? = null
    private var reconnectJob: Job? = null
    private var freshnessJob: Job? = null

    val state: StateFlow<CondorTransportState> = mutableState.asStateFlow()

    init {
        scope.launch {
            bluetoothTransport.connectionState.collect { connectionState ->
                commandMutex.withLock {
                    handleConnectionStateChangedLocked(connectionState)
                }
            }
        }
    }

    suspend fun connect(bridge: CondorBridgeRef) {
        commandMutex.withLock {
            prepareFreshSessionLocked(bridge)
            launchSessionLocked(bridge)
        }
    }

    suspend fun disconnect() {
        commandMutex.withLock {
            manualDisconnect = true
            currentTargetBridge = null
            connectedOnceInTarget = false
            reconnectAttemptCount = 0
            cancelReconnectLocked()
            stopFreshnessTickerLocked()
            cancelSessionLocked()
            suppressDisconnectHandlingLocked()
            bluetoothTransport.close()
            framer.reset()
            liveSampleRepository.clear()
            mutableState.value = CondorTransportState()
        }
    }

    suspend fun forgetCurrentTarget() {
        commandMutex.withLock {
            forgetCurrentTargetLocked()
        }
    }

    private suspend fun prepareFreshSessionLocked(bridge: CondorBridgeRef) {
        manualDisconnect = false
        currentTargetBridge = bridge
        connectedOnceInTarget = false
        reconnectAttemptCount = 0
        cancelReconnectLocked()
        stopFreshnessTickerLocked()
        cancelSessionLocked()
        framer.reset()
        liveSampleRepository.clear()
        mutableState.value = CondorTransportState(
            activeBridge = bridge
        )
        if (bluetoothTransport.connectionState.value != BluetoothConnectionState.Disconnected) {
            suppressDisconnectHandlingLocked()
            bluetoothTransport.close()
        }
    }

    private suspend fun launchSessionLocked(bridge: CondorBridgeRef) {
        sessionJob = scope.launch {
            bluetoothTransport.open(bridge.toBondedDevice()).collect { chunk ->
                onChunk(
                    CondorReadChunk(
                        bytes = chunk.bytes,
                        receivedMonoMs = chunk.receivedMonoMs
                    )
                )
            }
        }
    }

    private suspend fun onChunk(chunk: CondorReadChunk) {
        commandMutex.withLock {
            val completedLines = framer.append(chunk)
            liveSampleRepository.onLines(completedLines)
            val sawFixPayload = completedLines.any(::isFixPayloadLine)
            val framingState = when {
                completedLines.isNotEmpty() -> CondorFramingState.FLOWING
                else -> CondorFramingState.WAITING_FOR_FIRST_LINE
            }
            val currentState = mutableState.value
            mutableState.value = currentState.copy(
                session = currentState.session.copy(
                    connection = CondorConnectionState.CONNECTED,
                    framing = framingState,
                    freshness = CondorStreamFreshness.HEALTHY,
                    hasFixPayload = currentState.session.hasFixPayload || sawFixPayload,
                    lastReceiveElapsedRealtimeMs = chunk.receivedMonoMs
                ),
                lastFailure = null
            )
        }
    }

    private suspend fun handleConnectionStateChangedLocked(
        connectionState: BluetoothConnectionState
    ) {
        when (connectionState) {
            BluetoothConnectionState.Disconnected -> handleDisconnectedLocked()
            is BluetoothConnectionState.Connecting -> {
                manualDisconnect = false
                mutableState.value = mutableState.value.copy(
                    activeBridge = connectionState.device.toBridgeRef(),
                    session = mutableState.value.session.copy(
                        connection = CondorConnectionState.CONNECTING,
                        framing = if (mutableState.value.session.lastReceiveElapsedRealtimeMs == null) {
                            CondorFramingState.IDLE
                        } else {
                            mutableState.value.session.framing
                        }
                    ),
                    lastFailure = null
                )
            }
            is BluetoothConnectionState.Connected -> {
                connectedOnceInTarget = true
                reconnectAttemptCount = 0
                cancelReconnectLocked()
                startFreshnessTickerLocked()
                val currentSession = mutableState.value.session
                mutableState.value = mutableState.value.copy(
                    activeBridge = connectionState.device.toBridgeRef(),
                    session = currentSession.copy(
                        connection = CondorConnectionState.CONNECTED,
                        framing = if (currentSession.lastReceiveElapsedRealtimeMs == null) {
                            CondorFramingState.WAITING_FOR_FIRST_LINE
                        } else {
                            currentSession.framing
                        }
                    ),
                    reconnect = CondorReconnectState.IDLE,
                    lastFailure = if (currentSession.freshness == CondorStreamFreshness.STALE) {
                        CondorLiveDegradedReason.STALE_STREAM
                    } else {
                        null
                    }
                )
            }
            is BluetoothConnectionState.Error -> handleTransportErrorLocked(connectionState)
        }
    }

    private suspend fun handleDisconnectedLocked() {
        stopFreshnessTickerLocked()
        val targetBridge = currentTargetBridge
        if (suppressNextDisconnect) {
            suppressNextDisconnect = false
            return
        }
        if (manualDisconnect || targetBridge == null) {
            framer.reset()
            mutableState.value = CondorTransportState()
            return
        }

        mutableState.value = mutableState.value.copy(
            activeBridge = null,
            session = mutableState.value.session.copy(
                connection = CondorConnectionState.DISCONNECTED
            ),
            lastFailure = mutableState.value.lastFailure ?: CondorLiveDegradedReason.DISCONNECTED
        )
        if (connectedOnceInTarget) {
            scheduleReconnectLocked(targetBridge)
        }
    }

    private suspend fun handleTransportErrorLocked(
        connectionState: BluetoothConnectionState.Error
    ) {
        stopFreshnessTickerLocked()
        val failure = when (connectionState.error) {
            BluetoothConnectionError.STREAM_CLOSED -> CondorLiveDegradedReason.DISCONNECTED
            else -> CondorLiveDegradedReason.TRANSPORT_ERROR
        }
        mutableState.value = mutableState.value.copy(
            activeBridge = connectionState.device?.toBridgeRef() ?: mutableState.value.activeBridge,
            session = mutableState.value.session.copy(
                connection = CondorConnectionState.ERROR
            ),
            lastFailure = failure
        )
        if (connectedOnceInTarget) {
            currentTargetBridge?.let { scheduleReconnectLocked(it) }
        }
    }

    private fun scheduleReconnectLocked(bridge: CondorBridgeRef) {
        val nextAttempt = reconnectAttemptCount + 1
        if (nextAttempt > MAX_RECONNECT_ATTEMPTS) {
            cancelReconnectLocked()
            mutableState.value = mutableState.value.copy(
                activeBridge = null,
                session = mutableState.value.session.copy(
                    connection = CondorConnectionState.DISCONNECTED
                ),
                reconnect = CondorReconnectState.EXHAUSTED
            )
            return
        }

        cancelReconnectLocked()
        mutableState.value = mutableState.value.copy(
            reconnect = CondorReconnectState.WAITING
        )
        reconnectJob = scope.launch {
            delay(RECONNECT_BACKOFF_MS[nextAttempt - 1])
            commandMutex.withLock {
                if (manualDisconnect || currentTargetBridge?.stableId != bridge.stableId) return@withLock
                reconnectAttemptCount = nextAttempt
                mutableState.value = mutableState.value.copy(
                    activeBridge = bridge,
                    reconnect = CondorReconnectState.ATTEMPTING,
                    session = mutableState.value.session.copy(
                        connection = CondorConnectionState.CONNECTING
                    ),
                    lastFailure = null
                )
                cancelSessionLocked()
                framer.reset()
                launchSessionLocked(bridge)
            }
        }
    }

    private fun startFreshnessTickerLocked() {
        stopFreshnessTickerLocked()
        freshnessJob = scope.launch {
            while (true) {
                delay(FRESHNESS_TICK_MS)
                commandMutex.withLock {
                    val currentState = mutableState.value
                    val lastReceiveMs = currentState.session.lastReceiveElapsedRealtimeMs
                        ?: return@withLock
                    if (currentState.session.connection != CondorConnectionState.CONNECTED) {
                        return@withLock
                    }
                    val ageMs = (clock.nowMonoMs() - lastReceiveMs).coerceAtLeast(0L)
                    if (ageMs > STREAM_STALE_MS) {
                        mutableState.value = currentState.copy(
                            session = currentState.session.copy(
                                freshness = CondorStreamFreshness.STALE
                            ),
                            lastFailure = CondorLiveDegradedReason.STALE_STREAM
                        )
                    }
                }
            }
        }
    }

    private fun stopFreshnessTickerLocked() {
        freshnessJob?.cancel()
        freshnessJob = null
    }

    private fun cancelReconnectLocked() {
        reconnectJob?.cancel()
        reconnectJob = null
        if (mutableState.value.reconnect == CondorReconnectState.WAITING ||
            mutableState.value.reconnect == CondorReconnectState.ATTEMPTING
        ) {
            mutableState.value = mutableState.value.copy(
                reconnect = CondorReconnectState.IDLE
            )
        }
    }

    private fun cancelSessionLocked() {
        sessionJob?.cancel()
        sessionJob = null
    }

    private suspend fun forgetCurrentTargetLocked() {
        val targetBridge = currentTargetBridge ?: return
        val currentState = mutableState.value
        val preserveConnectedSession =
            currentState.session.connection == CondorConnectionState.CONNECTED &&
                currentState.activeBridge?.stableId == targetBridge.stableId

        currentTargetBridge = null
        connectedOnceInTarget = false
        reconnectAttemptCount = 0
        cancelReconnectLocked()

        if (preserveConnectedSession) {
            mutableState.value = currentState.copy(
                reconnect = CondorReconnectState.IDLE
            )
            return
        }

        stopFreshnessTickerLocked()
        cancelSessionLocked()
        if (bluetoothTransport.connectionState.value != BluetoothConnectionState.Disconnected) {
            suppressDisconnectHandlingLocked()
            bluetoothTransport.close()
        }
        framer.reset()
        liveSampleRepository.clear()
        mutableState.value = CondorTransportState()
    }

    private fun suppressDisconnectHandlingLocked() {
        suppressNextDisconnect = true
    }

    private fun CondorBridgeRef.toBondedDevice(): BondedBluetoothDevice =
        BondedBluetoothDevice(
            address = stableId,
            displayName = displayName
        )

    private fun BondedBluetoothDevice.toBridgeRef(): CondorBridgeRef =
        CondorBridgeRef(
            stableId = address,
            displayName = displayName
        )

    private fun isFixPayloadLine(line: NmeaLine): Boolean =
        line.text.startsWith("\$GPGGA") ||
            line.text.startsWith("\$GNGGA") ||
            line.text.startsWith("\$GPRMC") ||
            line.text.startsWith("\$GNRMC") ||
            line.text.startsWith("\$LXWP0")

    private companion object {
        const val STREAM_STALE_MS: Long = 5_000L
        const val FRESHNESS_TICK_MS: Long = 1_000L
        val RECONNECT_BACKOFF_MS: LongArray = longArrayOf(1_000L, 2_000L, 5_000L)
        const val MAX_RECONNECT_ATTEMPTS: Int = 3
    }

    internal fun shutdown() {
        reconnectJob?.cancel()
        sessionJob?.cancel()
        freshnessJob?.cancel()
        scope.cancel()
    }
}
