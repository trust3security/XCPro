package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.simulator.CondorBridgeRef
import com.trust3.xcpro.simulator.CondorTransportKind
import kotlinx.coroutines.flow.StateFlow

interface CondorBridgeControlPort {
    val settingsState: StateFlow<CondorBridgeSettingsState>

    suspend fun refresh()

    suspend fun selectTransport(kind: CondorTransportKind)

    suspend fun updateTcpListenPort(port: Int)

    suspend fun updateTcpIpAddress(address: String?)

    suspend fun selectBridge(bridge: CondorBridgeRef)

    suspend fun clearSelectedBridge()

    suspend fun connect()

    suspend fun disconnect()
}
