package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.livesource.DesiredLiveMode
import com.trust3.xcpro.livesource.DesiredLiveModePreferencesRepository
import com.trust3.xcpro.simulator.CondorBridgeRef
import com.trust3.xcpro.simulator.CondorConnectionState
import com.trust3.xcpro.simulator.CondorLiveDegradedReason
import com.trust3.xcpro.simulator.CondorReconnectState
import com.trust3.xcpro.simulator.CondorStreamFreshness
import com.trust3.xcpro.simulator.CondorTransportKind
import com.trust3.xcpro.simulator.condor.CondorBondedBridgeItem
import com.trust3.xcpro.simulator.condor.CondorBridgeControlPort
import com.trust3.xcpro.simulator.condor.CondorBridgeSettingsState
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class CondorBridgeSettingsUseCase @Inject constructor(
    private val controlPort: CondorBridgeControlPort,
    private val desiredLiveModeRepository: DesiredLiveModePreferencesRepository,
    private val clock: Clock
) {
    val uiState: Flow<CondorBridgeSettingsUiState> =
        combine(
            controlPort.settingsState,
            desiredLiveModeRepository.desiredLiveMode
        ) { settingsState, desiredLiveMode ->
            mapSettingsState(settingsState, desiredLiveMode)
        }

    suspend fun refresh() {
        controlPort.refresh()
    }

    suspend fun selectTransport(kind: CondorTransportKind) {
        controlPort.selectTransport(kind)
    }

    suspend fun updateTcpListenPort(port: Int) {
        controlPort.updateTcpListenPort(port)
    }

    suspend fun selectBridge(address: String) {
        val bridge = controlPort.settingsState.value.bondedBridges.firstOrNull {
            it.bridge.stableId == address
        }?.bridge ?: return
        controlPort.selectBridge(bridge)
    }

    suspend fun clearSelectedBridge() {
        controlPort.clearSelectedBridge()
    }

    suspend fun setDesiredLiveMode(mode: DesiredLiveMode) {
        desiredLiveModeRepository.setDesiredLiveMode(mode)
    }

    suspend fun connect() {
        controlPort.connect()
    }

    suspend fun disconnect() {
        controlPort.disconnect()
    }

    private fun mapSettingsState(
        state: CondorBridgeSettingsState,
        desiredLiveMode: DesiredLiveMode
    ): CondorBridgeSettingsUiState {
        val selectedTransport = state.selectedTransport
        return CondorBridgeSettingsUiState(
            desiredLiveMode = desiredLiveMode,
            selectedTransport = selectedTransport,
            bluetoothPermissionRequired = state.permissionRequired,
            bondedBridges = state.bondedBridges.map(::mapBondedBridge),
            selectedEndpointLabel = when (selectedTransport) {
                CondorTransportKind.BLUETOOTH -> formatBridgeLabel(
                    bridge = state.liveState.selectedBridge,
                    emptyLabel = "No bridge selected"
                )

                CondorTransportKind.TCP_LISTENER -> formatTcpEndpointLabel(
                    localIpAddress = state.tcpLocalIpAddress,
                    listenPort = state.tcpListenPort
                )
            },
            selectedBridgeWarningText = when {
                selectedTransport != CondorTransportKind.BLUETOOTH -> null
                state.liveState.selectedBridge == null -> null
                !state.selectedBridgeAvailable ->
                    "Saved bridge is not currently bonded. Clear it or select another bridge."

                else -> null
            },
            activeEndpointLabel = when (selectedTransport) {
                CondorTransportKind.BLUETOOTH -> formatBridgeLabel(
                    bridge = state.liveState.activeBridge,
                    emptyLabel = "No active bridge"
                )

                CondorTransportKind.TCP_LISTENER -> when {
                    state.liveState.session.connection != CondorConnectionState.DISCONNECTED ||
                        state.liveState.reconnect == CondorReconnectState.WAITING ||
                        state.liveState.reconnect == CondorReconnectState.ATTEMPTING ->
                        formatTcpEndpointLabel(
                            localIpAddress = state.tcpLocalIpAddress,
                            listenPort = state.tcpListenPort
                        )

                    else -> "No active listener"
                }
            },
            statusText = state.toStatusText(),
            healthText = state.toHealthText(),
            reconnectText = state.toReconnectText(),
            failureText = state.toFailureText(),
            tcpListenPort = state.tcpListenPort,
            tcpLocalIpAddress = state.tcpLocalIpAddress,
            connectEnabled = state.connectEnabled,
            disconnectEnabled = state.disconnectEnabled,
            clearSelectionEnabled = state.clearSelectionEnabled
        )
    }

    private fun mapBondedBridge(
        bridge: CondorBondedBridgeItem
    ): CondorBondedBridgeRowUiState =
        CondorBondedBridgeRowUiState(
            address = bridge.bridge.stableId,
            title = bridge.bridge.displayName ?: bridge.bridge.stableId,
            subtitle = bridge.bridge.displayName?.let { bridge.bridge.stableId },
            isSelected = bridge.isSelected
        )

    private fun CondorBridgeSettingsState.toStatusText(): String =
        when (selectedTransport) {
            CondorTransportKind.BLUETOOTH -> toBluetoothStatusText()
            CondorTransportKind.TCP_LISTENER -> toTcpStatusText()
        }

    private fun CondorBridgeSettingsState.toBluetoothStatusText(): String {
        if (permissionRequired) return "Bluetooth permission required"
        return when {
            liveState.reconnect == CondorReconnectState.WAITING ||
                liveState.reconnect == CondorReconnectState.ATTEMPTING ->
                "Reconnecting"

            liveState.reconnect == CondorReconnectState.BLOCKED ->
                "Saved bridge unavailable"

            liveState.session.connection == CondorConnectionState.CONNECTING ->
                "Connecting"

            liveState.session.connection == CondorConnectionState.CONNECTED ->
                "Connected"

            liveState.session.connection == CondorConnectionState.ERROR ->
                "Connection failed"

            liveState.selectedBridge != null && !selectedBridgeAvailable ->
                "Saved bridge unavailable"

            bondedBridges.isEmpty() -> "No bonded bridges"
            else -> "Disconnected"
        }
    }

    private fun CondorBridgeSettingsState.toTcpStatusText(): String =
        when {
            liveState.reconnect == CondorReconnectState.WAITING ||
                liveState.reconnect == CondorReconnectState.ATTEMPTING ->
                "Restarting listener"

            liveState.session.connection == CondorConnectionState.CONNECTING ->
                "Listening for connection"

            liveState.session.connection == CondorConnectionState.CONNECTED ->
                "Connected"

            liveState.session.connection == CondorConnectionState.ERROR ->
                "Listener failed"

            else -> "Disconnected"
        }

    private fun CondorBridgeSettingsState.toHealthText(): String? {
        val lastReceiveMs = liveState.session.lastReceiveElapsedRealtimeMs
        return when {
            liveState.session.connection == CondorConnectionState.CONNECTED &&
                lastReceiveMs == null ->
                "Stream waiting for first sentence."

            liveState.session.freshness == CondorStreamFreshness.HEALTHY &&
                lastReceiveMs != null ->
                "Stream healthy, last data ${formatAge(lastReceiveMs)}."

            liveState.session.freshness == CondorStreamFreshness.STALE &&
                lastReceiveMs != null ->
                "Stream stale, last data ${formatAge(lastReceiveMs)}."

            lastReceiveMs != null ->
                "Last stream sample ${formatAge(lastReceiveMs)}."

            else -> null
        }
    }

    private fun CondorBridgeSettingsState.toReconnectText(): String? =
        when (liveState.reconnect) {
            CondorReconnectState.WAITING -> when (selectedTransport) {
                CondorTransportKind.BLUETOOTH -> "Reconnect scheduled."
                CondorTransportKind.TCP_LISTENER -> "Listener restart scheduled."
            }

            CondorReconnectState.ATTEMPTING -> when (selectedTransport) {
                CondorTransportKind.BLUETOOTH -> "Reconnect attempt in progress."
                CondorTransportKind.TCP_LISTENER -> "Listener restart in progress."
            }

            CondorReconnectState.BLOCKED ->
                "Reconnect blocked until the saved bridge is available or cleared."

            CondorReconnectState.EXHAUSTED -> when (selectedTransport) {
                CondorTransportKind.BLUETOOTH -> "Reconnect stopped after the retry limit."
                CondorTransportKind.TCP_LISTENER -> "Listener stopped after the retry limit."
            }

            CondorReconnectState.IDLE -> null
        }

    private fun CondorBridgeSettingsState.toFailureText(): String? {
        if (selectedTransport == CondorTransportKind.TCP_LISTENER && tcpFailureDetail != null) {
            return tcpFailureDetail
        }
        val failure = liveState.lastFailure ?: return null
        return when (failure) {
            CondorLiveDegradedReason.DISCONNECTED -> when (selectedTransport) {
                CondorTransportKind.BLUETOOTH -> "Bridge disconnected."
                CondorTransportKind.TCP_LISTENER -> "TCP client disconnected."
            }

            CondorLiveDegradedReason.STALE_STREAM -> "Stream became stale."
            CondorLiveDegradedReason.TRANSPORT_ERROR -> when (selectedTransport) {
                CondorTransportKind.BLUETOOTH -> "Bluetooth transport failed."
                CondorTransportKind.TCP_LISTENER -> "TCP listener failed."
            }
        }
    }

    private fun formatBridgeLabel(
        bridge: CondorBridgeRef?,
        emptyLabel: String
    ): String {
        if (bridge == null) return emptyLabel
        return when {
            bridge.displayName.isNullOrBlank() -> bridge.stableId
            else -> "${bridge.displayName} (${bridge.stableId})"
        }
    }

    private fun formatTcpEndpointLabel(
        localIpAddress: String?,
        listenPort: Int
    ): String =
        if (localIpAddress.isNullOrBlank()) {
            "Port $listenPort"
        } else {
            "$localIpAddress:$listenPort"
        }

    private fun formatAge(lastReceiveElapsedRealtimeMs: Long): String {
        val ageMs = (clock.nowMonoMs() - lastReceiveElapsedRealtimeMs).coerceAtLeast(0L)
        return String.format(Locale.US, "%d ms ago", ageMs)
    }
}
