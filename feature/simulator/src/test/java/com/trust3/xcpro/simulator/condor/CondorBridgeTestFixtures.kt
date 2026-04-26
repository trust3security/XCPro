package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.bluetooth.BluetoothConnectPermissionPort
import com.trust3.xcpro.bluetooth.BluetoothConnectionState
import com.trust3.xcpro.bluetooth.BluetoothReadChunk
import com.trust3.xcpro.bluetooth.BluetoothTransport
import com.trust3.xcpro.bluetooth.BondedBluetoothDevice
import com.trust3.xcpro.simulator.CondorBridgeRef
import com.trust3.xcpro.simulator.CondorTransportKind
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

internal class FakeCondorSelectedBridgeStorage : CondorSelectedBridgeStorage {
    private var current: PersistedCondorBridge? = null

    override fun read(): PersistedCondorBridge? = current

    override fun write(value: PersistedCondorBridge) {
        current = value
    }

    override fun clear() {
        current = null
    }
}

internal class FakeBluetoothConnectPermissionPort(
    var granted: Boolean
) : BluetoothConnectPermissionPort {
    override fun isGranted(): Boolean = granted
}

internal class FakeBluetoothTransport(
    bondedDevices: List<BondedBluetoothDevice>
) : BluetoothTransport {
    var bondedDevices: List<BondedBluetoothDevice> = bondedDevices
    val openedDevices = mutableListOf<BondedBluetoothDevice>()
    val mutableConnectionState =
        MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)

    override val connectionState: StateFlow<BluetoothConnectionState> = mutableConnectionState

    override suspend fun listBondedDevices(): List<BondedBluetoothDevice> = bondedDevices

    override fun open(device: BondedBluetoothDevice): Flow<BluetoothReadChunk> = flow {
        openedDevices += device
    }

    override suspend fun close() {
        mutableConnectionState.value = BluetoothConnectionState.Disconnected
    }
}

internal class FakeCondorTransportPreferencesStorage : CondorTransportPreferencesStorage {
    var selectedTransport: CondorTransportKind = CondorTransportKind.BLUETOOTH
    var tcpListenPort: Int = CondorTcpPortSpec.DEFAULT_PORT
    var tcpIpAddress: String? = null

    override fun readSelectedTransport(): CondorTransportKind = selectedTransport

    override fun writeSelectedTransport(value: CondorTransportKind) {
        selectedTransport = value
    }

    override fun readTcpListenPort(): Int = tcpListenPort

    override fun writeTcpListenPort(value: Int) {
        tcpListenPort = value
    }

    override fun readTcpIpAddress(): String? = tcpIpAddress

    override fun writeTcpIpAddress(value: String?) {
        tcpIpAddress = value
    }
}

internal class FakeCondorTcpServerPort : CondorTcpServerPort {
    val openedPorts = mutableListOf<Int>()
    var closeCount: Int = 0
    var chunksForNextOpen: List<CondorReadChunk> = emptyList()
    var keepSessionOpen: Boolean = false
    val mutableConnectionState =
        MutableStateFlow<CondorTcpServerState>(CondorTcpServerState.Disconnected)

    override val connectionState: StateFlow<CondorTcpServerState> = mutableConnectionState

    override fun open(port: Int): Flow<CondorReadChunk> = flow {
        openedPorts += port
        chunksForNextOpen.forEach { emit(it) }
        chunksForNextOpen = emptyList()
        if (keepSessionOpen) {
            awaitCancellation()
        }
    }

    override suspend fun close() {
        closeCount += 1
        mutableConnectionState.value = CondorTcpServerState.Disconnected
    }
}

internal class FakeLocalNetworkInfoPort(
    var localIpAddress: String? = "192.168.1.10"
) : LocalNetworkInfoPort {
    override fun currentIpv4Address(): String? = localIpAddress
}

internal fun CondorBridgeRef.toBondedDevice(): BondedBluetoothDevice =
    BondedBluetoothDevice(
        address = stableId,
        displayName = displayName
    )

internal val TEST_CONDOR_BRIDGE_A = CondorBridgeRef(
    stableId = "AA:BB",
    displayName = "Condor Bridge A"
)

internal val TEST_CONDOR_BRIDGE_B = CondorBridgeRef(
    stableId = "CC:DD",
    displayName = "Condor Bridge B"
)
