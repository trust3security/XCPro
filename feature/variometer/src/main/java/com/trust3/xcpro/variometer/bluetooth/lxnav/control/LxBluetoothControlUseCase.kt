package com.trust3.xcpro.variometer.bluetooth.lxnav.control

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.variometer.bluetooth.BluetoothConnectionState
import com.trust3.xcpro.variometer.bluetooth.BluetoothTransport
import com.trust3.xcpro.variometer.bluetooth.BondedBluetoothDevice
import com.trust3.xcpro.variometer.bluetooth.lxnav.runtime.LxExternalRuntimeRepository
import com.trust3.xcpro.variometer.bluetooth.lxnav.runtime.LxExternalRuntimeSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
internal class LxBluetoothControlUseCase @Inject constructor(
    private val transport: BluetoothTransport,
    private val externalRuntimeRepository: LxExternalRuntimeRepository,
    private val permissionPort: BluetoothConnectPermissionPort,
    private val selectedDeviceRepository: LxBluetoothSelectedDeviceRepository,
    private val clock: Clock,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : LxBluetoothControlPort {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val commandMutex = Mutex()
    private val mutablePermissionRequired = MutableStateFlow(!permissionPort.isGranted())
    private val mutableBondedDevices = MutableStateFlow<List<BondedBluetoothDevice>>(emptyList())
    private val mutableReconnectState =
        MutableStateFlow<LxBluetoothReconnectState>(LxBluetoothReconnectState.Idle)
    private val mutableReconnectCount = MutableStateFlow(0)
    private val mutableLastDisconnectReason =
        MutableStateFlow<LxBluetoothDisconnectReason?>(null)

    private var activeSessionDevice: BondedBluetoothDevice? = null
    private var connectedOnceInActiveSession: Boolean = false
    private var reconnectJob: Job? = null
    private var connectCommandInProgress: Boolean = false
    private var disconnectCommandInProgress: Boolean = false

    override val state: StateFlow<LxBluetoothControlState> =
        combine(
            mutablePermissionRequired,
            mutableBondedDevices,
            selectedDeviceRepository.selectedDevice,
            transport.connectionState,
        ) { permissionRequired, bondedDevices, selectedDevice, connectionState ->
            ControlStateInputs(
                permissionRequired = permissionRequired,
                bondedDevices = bondedDevices,
                selectedDevice = selectedDevice,
                connectionState = connectionState
            )
        }.combine(externalRuntimeRepository.runtimeSnapshot) { inputs, runtimeSnapshot ->
            inputs to runtimeSnapshot
        }.combine(mutableReconnectState) { inputsWithRuntime, reconnectState ->
            Triple(inputsWithRuntime.first, inputsWithRuntime.second, reconnectState)
        }.combine(mutableReconnectCount) { reconnectInputs, reconnectCount ->
            reconnectInputs to reconnectCount
        }.combine(mutableLastDisconnectReason) { reconnectInputsWithCount, lastDisconnectReason ->
            val reconnectInputs = reconnectInputsWithCount.first
            buildLxBluetoothControlState(
                permissionRequired = reconnectInputs.first.permissionRequired,
                bondedDevices = reconnectInputs.first.bondedDevices,
                selectedDevice = reconnectInputs.first.selectedDevice,
                connectionState = reconnectInputs.first.connectionState,
                runtimeSnapshot = reconnectInputs.second,
                reconnectState = reconnectInputs.third,
                reconnectCount = reconnectInputsWithCount.second,
                lastDisconnectReason = lastDisconnectReason,
                nowMonoMs = clock.nowMonoMs(),
                streamStaleMs = STREAM_STALE_MS
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = buildLxBluetoothControlState(
                permissionRequired = mutablePermissionRequired.value,
                bondedDevices = mutableBondedDevices.value,
                selectedDevice = selectedDeviceRepository.selectedDevice.value,
                connectionState = transport.connectionState.value,
                runtimeSnapshot = externalRuntimeRepository.runtimeSnapshot.value,
                reconnectState = mutableReconnectState.value,
                reconnectCount = mutableReconnectCount.value,
                lastDisconnectReason = mutableLastDisconnectReason.value,
                nowMonoMs = clock.nowMonoMs(),
                streamStaleMs = STREAM_STALE_MS
            )
        )

    init {
        scope.launch {
            transport.connectionState.collect { connectionState ->
                commandMutex.withLock {
                    handleConnectionStateChangedLocked(connectionState)
                }
            }
        }
    }

    override suspend fun refresh() {
        commandMutex.withLock {
            refreshInputsLocked()
            applyReconnectInputGuardsLocked()
            clearRecoveredBlockedStateLocked()
        }
    }

    override suspend fun selectDevice(address: String) {
        commandMutex.withLock {
            val bondedDevice = mutableBondedDevices.value.firstOrNull { it.address == address }
            val existingSelection = selectedDeviceRepository.selectedDevice.value
            val previousSelectionAddress = existingSelection?.address
            selectedDeviceRepository.setSelectedDevice(
                address = address,
                displayNameSnapshot = bondedDevice?.displayName
                    ?: existingSelection?.takeIf { it.address == address }?.displayNameSnapshot
            )

            if (previousSelectionAddress != null && previousSelectionAddress != address) {
                clearReconnectTrackingLocked(
                    blockedReason = LxBluetoothReconnectBlockReason.SELECTION_CHANGED,
                    surfaceBlockedState = false,
                    disconnectReason = null
                )
            }
        }
    }

    override suspend fun connectSelected() {
        commandMutex.withLock {
            refreshInputsLocked()
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

            if (activeSessionDevice?.address != null && activeSessionDevice?.address != bondedDevice.address) {
                clearReconnectTrackingLocked(
                    blockedReason = LxBluetoothReconnectBlockReason.DIFFERENT_DEVICE_REQUESTED,
                    surfaceBlockedState = false,
                    disconnectReason = null
                )
            } else {
                cancelReconnectJobLocked()
            }

            activeSessionDevice = bondedDevice
            connectedOnceInActiveSession = false
            connectCommandInProgress = true
            disconnectCommandInProgress = false
            mutableReconnectState.value = LxBluetoothReconnectState.Idle
            mutableReconnectCount.value = 0
            mutableLastDisconnectReason.value = null

            externalRuntimeRepository.connect(bondedDevice)
        }
    }

    override suspend fun disconnect() {
        commandMutex.withLock {
            val shouldDisconnect =
                transport.connectionState.value !is BluetoothConnectionState.Disconnected ||
                    mutableReconnectState.value is LxBluetoothReconnectState.Waiting ||
                    mutableReconnectState.value is LxBluetoothReconnectState.Attempting
            if (!shouldDisconnect) return

            disconnectCommandInProgress = true
            connectCommandInProgress = false
            clearReconnectTrackingLocked(
                blockedReason = null,
                surfaceBlockedState = false,
                disconnectReason = null
            )
            mutableReconnectCount.value = 0
            externalRuntimeRepository.disconnect()
        }
    }

    override suspend fun onPermissionResult(granted: Boolean) {
        commandMutex.withLock {
            refreshInputsLocked()
            applyReconnectInputGuardsLocked()
            clearRecoveredBlockedStateLocked()
        }
    }

    private suspend fun handleConnectionStateChangedLocked(
        connectionState: BluetoothConnectionState
    ) {
        when (connectionState) {
            BluetoothConnectionState.Disconnected -> {
                if (connectCommandInProgress) return
                if (disconnectCommandInProgress) {
                    disconnectCommandInProgress = false
                    return
                }
                if (mutableReconnectState.value is LxBluetoothReconnectState.Attempting) {
                    mutableReconnectState.value = LxBluetoothReconnectState.Idle
                }
                if (connectedOnceInActiveSession) {
                    maybeScheduleReconnectLocked()
                } else {
                    clearReconnectTrackingLocked(
                        blockedReason = null,
                        surfaceBlockedState = false,
                        disconnectReason = null
                    )
                }
            }

            is BluetoothConnectionState.Connecting -> {
                connectCommandInProgress = false
                disconnectCommandInProgress = false
                activeSessionDevice = connectionState.device
            }

            is BluetoothConnectionState.Connected -> {
                connectCommandInProgress = false
                disconnectCommandInProgress = false
                activeSessionDevice = connectionState.device
                connectedOnceInActiveSession = true
                cancelReconnectJobLocked()
                mutableReconnectState.value = LxBluetoothReconnectState.Idle
                mutableLastDisconnectReason.value = null
            }

            is BluetoothConnectionState.Error -> {
                connectCommandInProgress = false
                disconnectCommandInProgress = false
                connectionState.error.toDisconnectReasonOrNull()?.let { disconnectReason ->
                    mutableLastDisconnectReason.value = disconnectReason
                }
                if (mutableReconnectState.value is LxBluetoothReconnectState.Attempting) {
                    mutableReconnectState.value = LxBluetoothReconnectState.Idle
                }
                if (connectedOnceInActiveSession) {
                    maybeScheduleReconnectLocked()
                } else {
                    clearReconnectTrackingLocked(
                        blockedReason = null,
                        surfaceBlockedState = false,
                        disconnectReason = mutableLastDisconnectReason.value
                    )
                }
            }
        }
    }

    private suspend fun refreshInputsLocked() {
        val granted = permissionPort.isGranted()
        mutablePermissionRequired.value = !granted
        mutableBondedDevices.value =
            if (granted) transport.listBondedDevices() else emptyList()
    }

    private fun applyReconnectInputGuardsLocked() {
        val activeDeviceAddress = activeSessionDevice?.address ?: return
        if (mutablePermissionRequired.value) {
            clearReconnectTrackingLocked(
                blockedReason = LxBluetoothReconnectBlockReason.PERMISSION_REQUIRED,
                surfaceBlockedState = mutableReconnectState.value !is LxBluetoothReconnectState.Idle,
                disconnectReason = LxBluetoothDisconnectReason.PERMISSION_REQUIRED
            )
            return
        }

        val selectedAddress = selectedDeviceRepository.selectedDevice.value?.address
        if (selectedAddress != activeDeviceAddress) {
            clearReconnectTrackingLocked(
                blockedReason = LxBluetoothReconnectBlockReason.SELECTION_CHANGED,
                surfaceBlockedState = false,
                disconnectReason = null
            )
            return
        }

        val bondedDevice = mutableBondedDevices.value.firstOrNull { it.address == activeDeviceAddress }
        if (bondedDevice == null) {
            clearReconnectTrackingLocked(
                blockedReason = LxBluetoothReconnectBlockReason.DEVICE_NOT_BONDED,
                surfaceBlockedState = mutableReconnectState.value !is LxBluetoothReconnectState.Idle,
                disconnectReason = LxBluetoothDisconnectReason.DEVICE_NOT_BONDED
            )
            return
        }

        activeSessionDevice = bondedDevice
    }

    private fun clearRecoveredBlockedStateLocked() {
        val blockedState = mutableReconnectState.value as? LxBluetoothReconnectState.Blocked ?: return
        val selectedAddress = selectedDeviceRepository.selectedDevice.value?.address
        val recovered = when (blockedState.reason) {
            LxBluetoothReconnectBlockReason.PERMISSION_REQUIRED -> !mutablePermissionRequired.value
            LxBluetoothReconnectBlockReason.DEVICE_NOT_BONDED ->
                selectedAddress != null && mutableBondedDevices.value.any { it.address == selectedAddress }
            LxBluetoothReconnectBlockReason.SELECTION_CHANGED,
            LxBluetoothReconnectBlockReason.DIFFERENT_DEVICE_REQUESTED -> true
        }
        if (recovered) {
            mutableReconnectState.value = LxBluetoothReconnectState.Idle
        }
    }

    private suspend fun maybeScheduleReconnectLocked() {
        if (mutableReconnectState.value is LxBluetoothReconnectState.Waiting) return
        if (mutableReconnectState.value is LxBluetoothReconnectState.Attempting) return

        val device = activeSessionDevice ?: return
        if (!connectedOnceInActiveSession) {
            clearReconnectTrackingLocked(
                blockedReason = null,
                surfaceBlockedState = false,
                disconnectReason = mutableLastDisconnectReason.value
            )
            return
        }

        refreshInputsLocked()
        if (mutablePermissionRequired.value) {
            clearReconnectTrackingLocked(
                blockedReason = LxBluetoothReconnectBlockReason.PERMISSION_REQUIRED,
                surfaceBlockedState = true,
                disconnectReason = LxBluetoothDisconnectReason.PERMISSION_REQUIRED
            )
            return
        }

        if (selectedDeviceRepository.selectedDevice.value?.address != device.address) {
            clearReconnectTrackingLocked(
                blockedReason = LxBluetoothReconnectBlockReason.SELECTION_CHANGED,
                surfaceBlockedState = false,
                disconnectReason = null
            )
            return
        }

        val bondedDevice = mutableBondedDevices.value.firstOrNull { it.address == device.address }
        if (bondedDevice == null) {
            clearReconnectTrackingLocked(
                blockedReason = LxBluetoothReconnectBlockReason.DEVICE_NOT_BONDED,
                surfaceBlockedState = true,
                disconnectReason = LxBluetoothDisconnectReason.DEVICE_NOT_BONDED
            )
            return
        }

        val attemptNumber = mutableReconnectCount.value + 1
        if (attemptNumber > MAX_RECONNECT_ATTEMPTS) {
            cancelReconnectJobLocked()
            activeSessionDevice = null
            connectedOnceInActiveSession = false
            mutableReconnectState.value = LxBluetoothReconnectState.Exhausted(MAX_RECONNECT_ATTEMPTS)
            mutableLastDisconnectReason.value = LxBluetoothDisconnectReason.RETRIES_EXHAUSTED
            return
        }

        activeSessionDevice = bondedDevice
        scheduleReconnectAttemptLocked(bondedDevice, attemptNumber)
    }

    private fun scheduleReconnectAttemptLocked(
        device: BondedBluetoothDevice,
        attemptNumber: Int
    ) {
        val delayMs = RECONNECT_BACKOFF_MS[attemptNumber - 1]
        cancelReconnectJobLocked()
        mutableReconnectState.value = LxBluetoothReconnectState.Waiting(
            attemptNumber = attemptNumber,
            maxAttempts = MAX_RECONNECT_ATTEMPTS,
            delayMs = delayMs
        )
        reconnectJob = scope.launch {
            delay(delayMs)
            commandMutex.withLock {
                if (activeSessionDevice?.address != device.address) return@withLock
                if (mutableReconnectState.value !is LxBluetoothReconnectState.Waiting) return@withLock

                refreshInputsLocked()
                if (mutablePermissionRequired.value) {
                    clearReconnectTrackingLocked(
                        blockedReason = LxBluetoothReconnectBlockReason.PERMISSION_REQUIRED,
                        surfaceBlockedState = true,
                        disconnectReason = LxBluetoothDisconnectReason.PERMISSION_REQUIRED
                    )
                    return@withLock
                }

                if (selectedDeviceRepository.selectedDevice.value?.address != device.address) {
                    clearReconnectTrackingLocked(
                        blockedReason = LxBluetoothReconnectBlockReason.SELECTION_CHANGED,
                        surfaceBlockedState = false,
                        disconnectReason = null
                    )
                    return@withLock
                }

                val bondedDevice = mutableBondedDevices.value.firstOrNull { it.address == device.address }
                if (bondedDevice == null) {
                    clearReconnectTrackingLocked(
                        blockedReason = LxBluetoothReconnectBlockReason.DEVICE_NOT_BONDED,
                        surfaceBlockedState = true,
                        disconnectReason = LxBluetoothDisconnectReason.DEVICE_NOT_BONDED
                    )
                    return@withLock
                }

                mutableReconnectCount.value = attemptNumber
                connectCommandInProgress = true
                mutableReconnectState.value = LxBluetoothReconnectState.Attempting(
                    attemptNumber = attemptNumber,
                    maxAttempts = MAX_RECONNECT_ATTEMPTS
                )
                externalRuntimeRepository.connect(bondedDevice)
            }
        }
    }

    private fun cancelReconnectJobLocked() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun clearReconnectTrackingLocked(
        blockedReason: LxBluetoothReconnectBlockReason?,
        surfaceBlockedState: Boolean,
        disconnectReason: LxBluetoothDisconnectReason?
    ) {
        cancelReconnectJobLocked()
        activeSessionDevice = null
        connectedOnceInActiveSession = false
        connectCommandInProgress = false
        if (!disconnectCommandInProgress) {
            mutableReconnectCount.value = 0
        }
        mutableReconnectState.value =
            if (surfaceBlockedState && blockedReason != null) {
                LxBluetoothReconnectState.Blocked(blockedReason)
            } else {
                LxBluetoothReconnectState.Idle
            }
        mutableLastDisconnectReason.value = disconnectReason
    }

    private companion object {
        const val STREAM_STALE_MS: Long = 5_000L
        val RECONNECT_BACKOFF_MS: LongArray = longArrayOf(1_000L, 2_000L, 5_000L)
        const val MAX_RECONNECT_ATTEMPTS: Int = 3
    }
}
