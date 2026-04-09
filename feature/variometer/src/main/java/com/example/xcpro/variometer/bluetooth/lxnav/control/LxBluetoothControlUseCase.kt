package com.example.xcpro.variometer.bluetooth.lxnav.control

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.variometer.bluetooth.BluetoothConnectionError
import com.example.xcpro.variometer.bluetooth.BluetoothConnectionState
import com.example.xcpro.variometer.bluetooth.BluetoothTransport
import com.example.xcpro.variometer.bluetooth.BondedBluetoothDevice
import com.example.xcpro.variometer.bluetooth.lxnav.runtime.LxExternalRuntimeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
internal class LxBluetoothControlUseCase @Inject constructor(
    private val transport: BluetoothTransport,
    private val externalRuntimeRepository: LxExternalRuntimeRepository,
    private val permissionPort: BluetoothConnectPermissionPort,
    private val selectedDeviceRepository: LxBluetoothSelectedDeviceRepository,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : LxBluetoothControlPort {

    // AI-NOTE: this singleton owns only derived settings/control state over the
    // transport + persisted selection seams. The actual Bluetooth session stays
    // owned by BluetoothTransport and LxExternalRuntimeRepository.
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val commandMutex = Mutex()
    private val mutablePermissionRequired = MutableStateFlow(!permissionPort.isGranted())
    private val mutableBondedDevices = MutableStateFlow<List<BondedBluetoothDevice>>(emptyList())

    override val state: StateFlow<LxBluetoothControlState> =
        combine(
            mutablePermissionRequired,
            mutableBondedDevices,
            selectedDeviceRepository.selectedDevice,
            transport.connectionState
        ) { permissionRequired, bondedDevices, selectedDevice, connectionState ->
            buildState(
                permissionRequired = permissionRequired,
                bondedDevices = bondedDevices,
                selectedDevice = selectedDevice,
                connectionState = connectionState
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = buildState(
                permissionRequired = mutablePermissionRequired.value,
                bondedDevices = mutableBondedDevices.value,
                selectedDevice = selectedDeviceRepository.selectedDevice.value,
                connectionState = transport.connectionState.value
            )
        )

    override suspend fun refresh() {
        commandMutex.withLock {
            refreshLocked()
        }
    }

    override suspend fun selectDevice(address: String) {
        commandMutex.withLock {
            val bondedDevice = mutableBondedDevices.value.firstOrNull { it.address == address }
            val existingSelection = selectedDeviceRepository.selectedDevice.value
            selectedDeviceRepository.setSelectedDevice(
                address = address,
                displayNameSnapshot = bondedDevice?.displayName
                    ?: existingSelection?.takeIf { it.address == address }?.displayNameSnapshot
            )
        }
    }

    override suspend fun connectSelected() {
        commandMutex.withLock {
            refreshLocked()
            if (mutablePermissionRequired.value) return

            val selectedDevice = selectedDeviceRepository.selectedDevice.value ?: return
            val connectionState = transport.connectionState.value
            if (
                connectionState is BluetoothConnectionState.Connecting ||
                connectionState is BluetoothConnectionState.Connected
            ) {
                return
            }

            val bondedDevice = mutableBondedDevices.value.firstOrNull {
                it.address == selectedDevice.address
            } ?: return

            externalRuntimeRepository.connect(bondedDevice)
        }
    }

    override suspend fun disconnect() {
        commandMutex.withLock {
            if (transport.connectionState.value is BluetoothConnectionState.Disconnected) return
            externalRuntimeRepository.disconnect()
        }
    }

    override suspend fun onPermissionResult(granted: Boolean) {
        commandMutex.withLock {
            refreshLocked()
        }
    }

    private suspend fun refreshLocked() {
        val granted = permissionPort.isGranted()
        mutablePermissionRequired.value = !granted
        mutableBondedDevices.value =
            if (granted) transport.listBondedDevices() else emptyList()
    }

    private fun buildState(
        permissionRequired: Boolean,
        bondedDevices: List<BondedBluetoothDevice>,
        selectedDevice: PersistedLxBluetoothDevice?,
        connectionState: BluetoothConnectionState
    ): LxBluetoothControlState {
        val selectedBondedDevice = selectedDevice?.address?.let { address ->
            bondedDevices.firstOrNull { it.address == address }
        }
        val selectedDisplayName =
            selectedBondedDevice?.displayName ?: selectedDevice?.displayNameSnapshot
        val activeDevice = when (connectionState) {
            is BluetoothConnectionState.Connected -> connectionState.device
            is BluetoothConnectionState.Connecting -> connectionState.device
            is BluetoothConnectionState.Error -> connectionState.device
            BluetoothConnectionState.Disconnected -> null
        }
        val lastError = (connectionState as? BluetoothConnectionState.Error)?.error
        val canConnect =
            !permissionRequired &&
                selectedDevice != null &&
                selectedBondedDevice != null &&
                connectionState !is BluetoothConnectionState.Connecting &&
                connectionState !is BluetoothConnectionState.Connected
        val canDisconnect = connectionState !is BluetoothConnectionState.Disconnected

        return LxBluetoothControlState(
            permissionRequired = permissionRequired,
            bondedDevices = bondedDevices.map { bondedDevice ->
                BluetoothBondedDeviceItem(
                    address = bondedDevice.address,
                    displayName = bondedDevice.displayName
                )
            },
            selectedDeviceAddress = selectedDevice?.address,
            selectedDeviceDisplayName = selectedDisplayName,
            selectedDeviceAvailable = selectedDevice != null && selectedBondedDevice != null,
            activeDeviceAddress = activeDevice?.address,
            activeDeviceName = activeDevice?.displayName,
            connectionState = connectionState,
            lastError = lastError,
            canConnect = canConnect,
            canDisconnect = canDisconnect
        )
    }
}
