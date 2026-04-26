package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.simulator.CondorBridgeRef
import com.trust3.xcpro.simulator.CondorLiveState
import com.trust3.xcpro.simulator.CondorTransportKind

data class CondorBondedBridgeItem(
    val bridge: CondorBridgeRef,
    val isSelected: Boolean
)

data class CondorBridgeSettingsState(
    val selectedTransport: CondorTransportKind = CondorTransportKind.BLUETOOTH,
    val permissionRequired: Boolean = false,
    val bondedBridges: List<CondorBondedBridgeItem> = emptyList(),
    val selectedBridgeAvailable: Boolean = true,
    val tcpListenPort: Int = CondorTcpPortSpec.DEFAULT_PORT,
    val tcpIpAddress: String? = null,
    val tcpLocalIpAddress: String? = null,
    val tcpFailureDetail: String? = null,
    val liveState: CondorLiveState = CondorLiveState(),
    val connectEnabled: Boolean = false,
    val disconnectEnabled: Boolean = false,
    val clearSelectionEnabled: Boolean = false
)
