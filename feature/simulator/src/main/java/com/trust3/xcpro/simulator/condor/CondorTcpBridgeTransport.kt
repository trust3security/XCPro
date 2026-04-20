package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.core.time.Clock
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

internal data class CondorTcpTransportState(
    val listenPort: Int? = null,
    val remoteAddress: String? = null,
    val session: CondorSessionState = CondorSessionState(),
    val reconnect: CondorReconnectState = CondorReconnectState.IDLE,
    val lastFailure: CondorLiveDegradedReason? = null,
    val lastFailureDetail: String? = null
)

@Singleton
internal class CondorTcpBridgeTransport @Inject constructor(
    private val tcpServerPort: CondorTcpServerPort,
    private val clock: Clock,
    private val liveSampleRepository: CondorLiveSampleRepository,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val commandMutex = Mutex()
    private val framer = CondorSentenceFramer()
    private val mutableState = MutableStateFlow(CondorTcpTransportState())

    private var currentTargetPort: Int? = null
    private var connectedOnceInTarget: Boolean = false
    private var suppressNextDisconnect: Boolean = false
    private var manualDisconnect: Boolean = false
    private var reconnectAttemptCount: Int = 0
    private var sessionJob: Job? = null
    private var reconnectJob: Job? = null
    private var freshnessJob: Job? = null

    val state: StateFlow<CondorTcpTransportState> = mutableState.asStateFlow()

    init {
        scope.launch {
            tcpServerPort.connectionState.collect { connectionState ->
                commandMutex.withLock {
                    handleConnectionStateChangedLocked(connectionState)
                }
            }
        }
    }

    suspend fun connect(listenPort: Int) {
        commandMutex.withLock {
            prepareFreshSessionLocked(listenPort)
            launchSessionLocked(listenPort)
        }
    }

    suspend fun disconnect() {
        commandMutex.withLock {
            manualDisconnect = true
            currentTargetPort = null
            connectedOnceInTarget = false
            reconnectAttemptCount = 0
            cancelReconnectLocked()
            stopFreshnessTickerLocked()
            cancelSessionLocked()
            tcpServerPort.close()
            framer.reset()
            liveSampleRepository.clear()
            mutableState.value = CondorTcpTransportState()
        }
    }

    private suspend fun prepareFreshSessionLocked(listenPort: Int) {
        manualDisconnect = false
        currentTargetPort = listenPort
        connectedOnceInTarget = false
        reconnectAttemptCount = 0
        cancelReconnectLocked()
        stopFreshnessTickerLocked()
        cancelSessionLocked()
        if (tcpServerPort.connectionState.value != CondorTcpServerState.Disconnected) {
            suppressDisconnectHandlingLocked()
        }
        tcpServerPort.close()
        framer.reset()
        liveSampleRepository.clear()
        mutableState.value = CondorTcpTransportState(
            listenPort = listenPort
        )
    }

    private fun launchSessionLocked(listenPort: Int) {
        sessionJob = scope.launch {
            tcpServerPort.open(listenPort).collect { chunk ->
                onChunk(chunk)
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
                lastFailure = null,
                lastFailureDetail = null
            )
        }
    }

    private suspend fun handleConnectionStateChangedLocked(
        connectionState: CondorTcpServerState
    ) {
        when (connectionState) {
            CondorTcpServerState.Disconnected -> handleDisconnectedLocked()
            is CondorTcpServerState.Listening -> {
                manualDisconnect = false
                mutableState.value = mutableState.value.copy(
                    listenPort = connectionState.port,
                    session = mutableState.value.session.copy(
                        connection = CondorConnectionState.CONNECTING,
                        framing = if (mutableState.value.session.lastReceiveElapsedRealtimeMs == null) {
                            CondorFramingState.IDLE
                        } else {
                            mutableState.value.session.framing
                        }
                    ),
                    lastFailure = null,
                    lastFailureDetail = null
                )
            }

            is CondorTcpServerState.Connected -> {
                connectedOnceInTarget = true
                reconnectAttemptCount = 0
                cancelReconnectLocked()
                startFreshnessTickerLocked()
                val currentSession = mutableState.value.session
                mutableState.value = mutableState.value.copy(
                    listenPort = connectionState.port,
                    remoteAddress = connectionState.remoteAddress,
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
                    },
                    lastFailureDetail = null
                )
            }

            is CondorTcpServerState.Error -> handleTransportErrorLocked(connectionState)
        }
    }

    private suspend fun handleDisconnectedLocked() {
        stopFreshnessTickerLocked()
        val targetPort = currentTargetPort
        if (suppressNextDisconnect) {
            suppressNextDisconnect = false
            return
        }
        if (manualDisconnect || targetPort == null) {
            framer.reset()
            mutableState.value = CondorTcpTransportState()
            return
        }

        mutableState.value = mutableState.value.copy(
            remoteAddress = null,
            session = mutableState.value.session.copy(
                connection = CondorConnectionState.DISCONNECTED
            ),
            lastFailure = mutableState.value.lastFailure ?: CondorLiveDegradedReason.DISCONNECTED
        )
        if (connectedOnceInTarget) {
            scheduleReconnectLocked(targetPort)
        }
    }

    private suspend fun handleTransportErrorLocked(
        connectionState: CondorTcpServerState.Error
    ) {
        stopFreshnessTickerLocked()
        val failure = when (connectionState.error) {
            CondorTcpServerError.STREAM_CLOSED -> CondorLiveDegradedReason.DISCONNECTED
            else -> CondorLiveDegradedReason.TRANSPORT_ERROR
        }
        mutableState.value = mutableState.value.copy(
            listenPort = connectionState.port,
            remoteAddress = null,
            session = mutableState.value.session.copy(
                connection = CondorConnectionState.ERROR
            ),
            lastFailure = failure,
            lastFailureDetail = connectionState.detail
        )
        if (connectedOnceInTarget) {
            currentTargetPort?.let { scheduleReconnectLocked(it) }
        }
    }

    private fun scheduleReconnectLocked(listenPort: Int) {
        val nextAttempt = reconnectAttemptCount + 1
        if (nextAttempt > MAX_RECONNECT_ATTEMPTS) {
            cancelReconnectLocked()
            mutableState.value = mutableState.value.copy(
                remoteAddress = null,
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
                if (manualDisconnect || currentTargetPort != listenPort) return@withLock
                reconnectAttemptCount = nextAttempt
                mutableState.value = mutableState.value.copy(
                    listenPort = listenPort,
                    remoteAddress = null,
                    reconnect = CondorReconnectState.ATTEMPTING,
                    session = mutableState.value.session.copy(
                        connection = CondorConnectionState.CONNECTING
                    ),
                    lastFailure = null,
                    lastFailureDetail = null
                )
                cancelSessionLocked()
                if (tcpServerPort.connectionState.value != CondorTcpServerState.Disconnected) {
                    suppressDisconnectHandlingLocked()
                }
                tcpServerPort.close()
                framer.reset()
                launchSessionLocked(listenPort)
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
                            lastFailure = CondorLiveDegradedReason.STALE_STREAM,
                            lastFailureDetail = null
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

    private fun suppressDisconnectHandlingLocked() {
        suppressNextDisconnect = true
    }

    private fun isFixPayloadLine(line: com.trust3.xcpro.bluetooth.NmeaLine): Boolean =
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
