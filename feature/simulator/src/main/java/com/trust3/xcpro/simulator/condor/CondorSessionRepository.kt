package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.simulator.CondorBridgeRef
import com.trust3.xcpro.simulator.CondorLiveState
import com.trust3.xcpro.simulator.CondorLiveStatePort
import com.trust3.xcpro.simulator.CondorReconnectState
import com.trust3.xcpro.simulator.CondorTransportKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Singleton
internal class CondorSessionRepository @Inject constructor(
    private val selectedBridgeRepository: CondorSelectedBridgeRepository,
    private val transportPreferencesRepository: CondorTransportPreferencesRepository,
    private val bluetoothTransport: CondorBridgeTransport,
    private val tcpTransport: CondorTcpBridgeTransport,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : CondorLiveStatePort {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableSelectedBridgeAvailable = MutableStateFlow<Boolean?>(null)

    val selectedBridgeAvailable: StateFlow<Boolean?> =
        mutableSelectedBridgeAvailable.asStateFlow()

    override val state: StateFlow<CondorLiveState> =
        combine(
            transportPreferencesRepository.selectedTransport,
            selectedBridgeRepository.selectedBridge,
            bluetoothTransport.state,
            tcpTransport.state,
            mutableSelectedBridgeAvailable
        ) { selectedTransport, selectedBridge, bluetoothState, tcpState, selectedBridgeAvailable ->
            val selectedRef = selectedBridge?.toBridgeRef()
            val effectiveState = when (selectedTransport) {
                CondorTransportKind.BLUETOOTH -> bluetoothState.toSessionProjection()
                CondorTransportKind.TCP_LISTENER -> tcpState.toSessionProjection()
            }
            CondorLiveState(
                selectedTransport = selectedTransport,
                selectedBridge = selectedRef.takeIf { selectedTransport == CondorTransportKind.BLUETOOTH },
                activeBridge = effectiveState.activeBridge,
                session = effectiveState.session,
                reconnect = when {
                    selectedTransport == CondorTransportKind.BLUETOOTH &&
                        selectedRef != null &&
                        selectedBridgeAvailable == false ->
                        CondorReconnectState.BLOCKED
                    else -> effectiveState.reconnect
                },
                lastFailure = effectiveState.lastFailure
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = CondorLiveState()
        )

    suspend fun updateSelectedBridgeAvailability(isAvailable: Boolean?) {
        mutableSelectedBridgeAvailable.value = isAvailable
    }

    internal fun shutdown() {
        scope.cancel()
    }
}

private data class CondorSessionProjection(
    val activeBridge: CondorBridgeRef?,
    val session: com.trust3.xcpro.simulator.CondorSessionState,
    val reconnect: CondorReconnectState,
    val lastFailure: com.trust3.xcpro.simulator.CondorLiveDegradedReason?
)

private fun CondorTransportState.toSessionProjection(): CondorSessionProjection =
    CondorSessionProjection(
        activeBridge = activeBridge,
        session = session,
        reconnect = reconnect,
        lastFailure = lastFailure
    )

private fun CondorTcpTransportState.toSessionProjection(): CondorSessionProjection =
    CondorSessionProjection(
        activeBridge = null,
        session = session,
        reconnect = reconnect,
        lastFailure = lastFailure
    )

internal fun PersistedCondorBridge.toBridgeRef(): CondorBridgeRef =
    CondorBridgeRef(
        stableId = stableId,
        displayName = displayNameSnapshot
    )
