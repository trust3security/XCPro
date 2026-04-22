package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.livesource.DesiredLiveMode
import com.trust3.xcpro.simulator.CondorTransportKind

internal const val CONDOR_BRIDGE_TAG_PERMISSION_BUTTON = "condor_bridge_permission_button"
internal const val CONDOR_BRIDGE_TAG_CONNECT_BUTTON = "condor_bridge_connect_button"
internal const val CONDOR_BRIDGE_TAG_DISCONNECT_BUTTON = "condor_bridge_disconnect_button"
internal const val CONDOR_BRIDGE_TAG_CLEAR_BUTTON = "condor_bridge_clear_button"
internal const val CONDOR_BRIDGE_TAG_LIVE_MODE_PHONE = "condor_bridge_live_mode_phone"
internal const val CONDOR_BRIDGE_TAG_LIVE_MODE_CONDOR2 = "condor_bridge_live_mode_condor2"
internal const val CONDOR_BRIDGE_TAG_TRANSPORT_BLUETOOTH = "condor_bridge_transport_bluetooth"
internal const val CONDOR_BRIDGE_TAG_TRANSPORT_TCP = "condor_bridge_transport_tcp"
internal const val CONDOR_BRIDGE_TAG_TCP_IP_ADDRESS = "condor_bridge_tcp_ip_address"
internal const val CONDOR_BRIDGE_TAG_TCP_PORT = "condor_bridge_tcp_port"

internal fun condorBridgeDeviceRowTag(address: String): String =
    "condor_bridge_device_$address"

internal fun condorBridgeLiveModeTag(mode: DesiredLiveMode): String =
    when (mode) {
        DesiredLiveMode.PHONE_ONLY -> CONDOR_BRIDGE_TAG_LIVE_MODE_PHONE
        DesiredLiveMode.CONDOR2_FULL -> CONDOR_BRIDGE_TAG_LIVE_MODE_CONDOR2
    }

internal fun condorBridgeTransportTag(kind: CondorTransportKind): String =
    when (kind) {
        CondorTransportKind.BLUETOOTH -> CONDOR_BRIDGE_TAG_TRANSPORT_BLUETOOTH
        CondorTransportKind.TCP_LISTENER -> CONDOR_BRIDGE_TAG_TRANSPORT_TCP
    }

data class CondorBondedBridgeRowUiState(
    val address: String,
    val title: String,
    val subtitle: String?,
    val isSelected: Boolean
)

data class CondorBridgeSettingsUiState(
    val desiredLiveMode: DesiredLiveMode = DesiredLiveMode.PHONE_ONLY,
    val selectedTransport: CondorTransportKind = CondorTransportKind.BLUETOOTH,
    val bluetoothPermissionRequired: Boolean = false,
    val bondedBridges: List<CondorBondedBridgeRowUiState> = emptyList(),
    val selectedEndpointLabel: String = "No endpoint selected",
    val selectedBridgeWarningText: String? = null,
    val activeEndpointLabel: String = "No active endpoint",
    val statusText: String = "Disconnected",
    val healthText: String? = null,
    val reconnectText: String? = null,
    val failureText: String? = null,
    val tcpListenPort: Int = 4_353,
    val tcpIpAddress: String? = null,
    val tcpLocalIpAddress: String? = null,
    val connectEnabled: Boolean = false,
    val disconnectEnabled: Boolean = false,
    val clearSelectionEnabled: Boolean = false
)
