package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.bluetooth.BluetoothConnectPermissionPort
import com.trust3.xcpro.bluetooth.BluetoothTransport
import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.simulator.CondorBridgeRef
import com.trust3.xcpro.simulator.CondorConnectionState
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class CondorBridgeUiBaseInputs(
    val permissionRequired: Boolean,
    val bondedBridges: List<CondorBridgeRef>,
    val localIpAddress: String?
)

private data class CondorBridgeUiTransportInputs(
    val selectedTransport: CondorTransportKind,
    val tcpListenPort: Int
)

private data class CondorBridgeUiLiveInputs(
    val liveState: com.trust3.xcpro.simulator.CondorLiveState,
    val selectedBridgeAvailable: Boolean?,
    val tcpState: CondorTcpTransportState
)

@Singleton
internal class CondorBridgeController @Inject constructor(
    private val bluetoothTransport: BluetoothTransport,
    private val permissionPort: BluetoothConnectPermissionPort,
    private val selectedBridgeRepository: CondorSelectedBridgeRepository,
    private val transportPreferencesRepository: CondorTransportPreferencesRepository,
    private val sessionRepository: CondorSessionRepository,
    private val bridgeTransport: CondorBridgeTransport,
    private val tcpTransport: CondorTcpBridgeTransport,
    private val localNetworkInfoPort: LocalNetworkInfoPort,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : CondorBridgeControlPort, CondorRuntimeSessionPort {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val commandMutex = Mutex()
    private val mutablePermissionRequired = MutableStateFlow(!permissionPort.isGranted())
    private val mutableBondedBridges = MutableStateFlow<List<CondorBridgeRef>>(emptyList())
    private val mutableLocalIpAddress = MutableStateFlow(localNetworkInfoPort.currentIpv4Address())

    private val baseInputs =
        combine(
            mutablePermissionRequired,
            mutableBondedBridges,
            mutableLocalIpAddress
        ) { permissionRequired, bondedBridges, localIpAddress ->
            CondorBridgeUiBaseInputs(
                permissionRequired = permissionRequired,
                bondedBridges = bondedBridges,
                localIpAddress = localIpAddress
            )
        }

    private val transportInputs =
        combine(
            transportPreferencesRepository.selectedTransport,
            transportPreferencesRepository.tcpListenPort
        ) { selectedTransport, tcpListenPort ->
            CondorBridgeUiTransportInputs(
                selectedTransport = selectedTransport,
                tcpListenPort = tcpListenPort
            )
        }

    private val liveInputs =
        combine(
            sessionRepository.state,
            sessionRepository.selectedBridgeAvailable,
            tcpTransport.state
        ) { liveState, selectedBridgeAvailable, tcpState ->
            CondorBridgeUiLiveInputs(
                liveState = liveState,
                selectedBridgeAvailable = selectedBridgeAvailable,
                tcpState = tcpState
            )
        }

    override val settingsState: StateFlow<CondorBridgeSettingsState> =
        combine(
            baseInputs,
            transportInputs,
            liveInputs
        ) { base, transport, live ->
            buildSettingsState(base, transport, live)
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = CondorBridgeSettingsState()
        )

    override suspend fun refresh() {
        commandMutex.withLock {
            refreshInputsLocked()
        }
    }

    override suspend fun selectTransport(kind: CondorTransportKind) {
        commandMutex.withLock {
            if (transportPreferencesRepository.selectedTransport.value == kind) {
                refreshInputsLocked()
                return
            }
            disconnectAllTransportsLocked()
            transportPreferencesRepository.setSelectedTransport(kind)
            refreshInputsLocked()
        }
    }

    override suspend fun updateTcpListenPort(port: Int) {
        require(CondorTcpPortSpec.isValid(port)) { "TCP listen port out of range: $port" }
        commandMutex.withLock {
            val selectedTransport = transportPreferencesRepository.selectedTransport.value
            val liveState = sessionRepository.state.value
            val reconnectActive = isReconnectActive(liveState.reconnect)
            val sessionActive =
                reconnectActive || liveState.session.connection != CondorConnectionState.DISCONNECTED
            if (selectedTransport == CondorTransportKind.TCP_LISTENER && sessionActive) {
                return
            }
            transportPreferencesRepository.setTcpListenPort(port)
            refreshInputsLocked()
        }
    }

    override suspend fun selectBridge(bridge: CondorBridgeRef) {
        commandMutex.withLock {
            val previousSelection = selectedBridgeRepository.selectedBridge.value?.toBridgeRef()
            if (previousSelection != null && previousSelection.stableId != bridge.stableId) {
                bridgeTransport.forgetCurrentTarget()
            }
            selectedBridgeRepository.setSelectedBridge(bridge)
            val selectedAvailable = mutableBondedBridges.value.any { it.stableId == bridge.stableId }
            sessionRepository.updateSelectedBridgeAvailability(
                if (mutablePermissionRequired.value) null else selectedAvailable
            )
        }
    }

    override suspend fun clearSelectedBridge() {
        commandMutex.withLock {
            bridgeTransport.disconnect()
            selectedBridgeRepository.clearSelection()
            sessionRepository.updateSelectedBridgeAvailability(null)
        }
    }

    override suspend fun connect() {
        commandMutex.withLock {
            refreshInputsLocked()
            connectSelectedTransportLocked(allowWhileReconnect = true)
        }
    }

    override suspend fun disconnect() {
        commandMutex.withLock {
            disconnectSelectedTransportLocked()
        }
    }

    override fun requestConnect() {
        scope.launch {
            commandMutex.withLock {
                refreshInputsLocked()
                connectSelectedTransportLocked(allowWhileReconnect = false)
            }
        }
    }

    override fun requestDisconnect() {
        scope.launch {
            commandMutex.withLock {
                disconnectSelectedTransportLocked()
            }
        }
    }

    private suspend fun connectSelectedTransportLocked(
        allowWhileReconnect: Boolean
    ) {
        when (transportPreferencesRepository.selectedTransport.value) {
            CondorTransportKind.BLUETOOTH -> connectBluetoothLocked(allowWhileReconnect)
            CondorTransportKind.TCP_LISTENER -> connectTcpLocked(allowWhileReconnect)
        }
    }

    private suspend fun connectBluetoothLocked(
        allowWhileReconnect: Boolean
    ) {
        val selectedBridge = selectedBridgeRepository.selectedBridge.value?.toBridgeRef() ?: return
        val liveState = sessionRepository.state.value
        if (mutablePermissionRequired.value) return
        if (!allowWhileReconnect && isReconnectActive(liveState.reconnect)) return
        if (liveState.session.connection == CondorConnectionState.CONNECTED ||
            liveState.session.connection == CondorConnectionState.CONNECTING
        ) {
            return
        }
        if (mutableBondedBridges.value.none { it.stableId == selectedBridge.stableId }) {
            sessionRepository.updateSelectedBridgeAvailability(false)
            return
        }
        bridgeTransport.connect(selectedBridge)
    }

    private suspend fun connectTcpLocked(
        allowWhileReconnect: Boolean
    ) {
        val liveState = sessionRepository.state.value
        if (!allowWhileReconnect && isReconnectActive(liveState.reconnect)) return
        if (liveState.session.connection == CondorConnectionState.CONNECTED ||
            liveState.session.connection == CondorConnectionState.CONNECTING
        ) {
            return
        }
        tcpTransport.connect(transportPreferencesRepository.tcpListenPort.value)
    }

    private suspend fun disconnectSelectedTransportLocked() {
        when (transportPreferencesRepository.selectedTransport.value) {
            CondorTransportKind.BLUETOOTH -> bridgeTransport.disconnect()
            CondorTransportKind.TCP_LISTENER -> tcpTransport.disconnect()
        }
    }

    private suspend fun disconnectAllTransportsLocked() {
        bridgeTransport.disconnect()
        tcpTransport.disconnect()
    }

    private suspend fun refreshInputsLocked() {
        val granted = permissionPort.isGranted()
        mutablePermissionRequired.value = !granted
        mutableLocalIpAddress.value = localNetworkInfoPort.currentIpv4Address()
        mutableBondedBridges.value =
            if (granted) {
                bluetoothTransport.listBondedDevices().map { bondedDevice ->
                    CondorBridgeRef(
                        stableId = bondedDevice.address,
                        displayName = bondedDevice.displayName
                    )
                }
            } else {
                emptyList()
            }

        val selectedBridge = selectedBridgeRepository.selectedBridge.value?.toBridgeRef()
        val selectedAvailable = when {
            !granted -> null
            selectedBridge == null -> null
            else -> mutableBondedBridges.value.any { it.stableId == selectedBridge.stableId }
        }
        sessionRepository.updateSelectedBridgeAvailability(selectedAvailable)
    }

    private fun buildSettingsState(
        base: CondorBridgeUiBaseInputs,
        transport: CondorBridgeUiTransportInputs,
        live: CondorBridgeUiLiveInputs
    ): CondorBridgeSettingsState {
        val reconnectActive = isReconnectActive(live.liveState.reconnect)
        val selectedAvailable =
            live.liveState.selectedBridge == null || live.selectedBridgeAvailable != false
        val connectEnabled = when (transport.selectedTransport) {
            CondorTransportKind.BLUETOOTH ->
                !base.permissionRequired &&
                    live.liveState.selectedBridge != null &&
                    selectedAvailable &&
                    live.liveState.session.connection != CondorConnectionState.CONNECTED &&
                    live.liveState.session.connection != CondorConnectionState.CONNECTING

            CondorTransportKind.TCP_LISTENER ->
                live.liveState.session.connection != CondorConnectionState.CONNECTED &&
                    live.liveState.session.connection != CondorConnectionState.CONNECTING
        }

        return CondorBridgeSettingsState(
            selectedTransport = transport.selectedTransport,
            permissionRequired = base.permissionRequired,
            bondedBridges = base.bondedBridges.map { bridge ->
                CondorBondedBridgeItem(
                    bridge = bridge,
                    isSelected = bridge.stableId == live.liveState.selectedBridge?.stableId
                )
            },
            selectedBridgeAvailable = selectedAvailable,
            tcpListenPort = transport.tcpListenPort,
            tcpLocalIpAddress = base.localIpAddress,
            tcpFailureDetail = live.tcpState.lastFailureDetail,
            liveState = live.liveState,
            connectEnabled = connectEnabled,
            disconnectEnabled =
                reconnectActive ||
                    live.liveState.session.connection != CondorConnectionState.DISCONNECTED,
            clearSelectionEnabled =
                transport.selectedTransport == CondorTransportKind.BLUETOOTH &&
                    live.liveState.selectedBridge != null
        )
    }

    private fun isReconnectActive(reconnectState: CondorReconnectState): Boolean =
        reconnectState == CondorReconnectState.WAITING ||
            reconnectState == CondorReconnectState.ATTEMPTING

    internal fun shutdown() {
        scope.cancel()
    }
}
