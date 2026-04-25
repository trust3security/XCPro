package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.bluetooth.BluetoothConnectionState
import com.trust3.xcpro.variometer.bluetooth.lxnav.control.BluetoothBondedDeviceItem
import com.trust3.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothDisconnectReason
import com.trust3.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothReconnectBlockReason
import com.trust3.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothControlPort
import com.trust3.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothControlState
import com.trust3.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothReconnectState
import javax.inject.Inject
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BluetoothVarioSettingsUseCase @Inject constructor(
    private val controlPort: LxBluetoothControlPort
) {
    val uiState: Flow<BluetoothVarioSettingsUiState> =
        controlPort.state.map(::mapControlState)

    suspend fun refresh() {
        controlPort.refresh()
    }

    suspend fun selectDevice(address: String) {
        controlPort.selectDevice(address)
    }

    suspend fun connect() {
        controlPort.connectSelected()
    }

    suspend fun disconnect() {
        controlPort.disconnect()
    }

    suspend fun onPermissionResult(granted: Boolean) {
        controlPort.onPermissionResult(granted)
    }

    private fun mapControlState(
        state: LxBluetoothControlState
    ): BluetoothVarioSettingsUiState {
        return BluetoothVarioSettingsUiState(
            permissionRequired = state.permissionRequired,
            bondedDevices = state.bondedDevices.map { bondedDevice ->
                bondedDevice.toUiState(selectedAddress = state.selectedDeviceAddress)
            },
            selectedDeviceLabel = formatDeviceLabel(
                address = state.selectedDeviceAddress,
                displayName = state.selectedDeviceDisplayName,
                emptyLabel = "No device selected"
            ),
            selectedDeviceWarningText = when {
                state.selectedDeviceAddress == null -> null
                !state.selectedDeviceAvailable -> "Selected device is not currently bonded."
                else -> null
            },
            activeDeviceLabel = formatDeviceLabel(
                address = state.activeDeviceAddress,
                displayName = state.activeDeviceName,
                emptyLabel = "No active device"
            ),
            statusText = state.toStatusText(),
            healthText = state.toHealthText(),
            reconnectText = state.toReconnectText(),
            failureText = state.toFailureText(),
            detailSections = state.detailSections.map { section ->
                BluetoothVarioDetailSectionUiState(
                    title = section.title,
                    rows = section.rows.map { row ->
                        BluetoothVarioDetailRowUiState(
                            label = row.label,
                            value = row.value
                        )
                    }
                )
            },
            connectEnabled = state.canConnect,
            disconnectEnabled = state.canDisconnect
        )
    }

    private fun BluetoothBondedDeviceItem.toUiState(
        selectedAddress: String?
    ): BluetoothBondedDeviceRowUiState =
        BluetoothBondedDeviceRowUiState(
            address = address,
            title = displayName ?: address,
            subtitle = if (displayName == null) null else address,
            isSelected = address == selectedAddress
        )

    private fun LxBluetoothControlState.toStatusText(): String {
        if (permissionRequired) return "Bluetooth permission required"
        when (reconnectState) {
            is LxBluetoothReconnectState.Waiting,
            is LxBluetoothReconnectState.Attempting -> return "Reconnecting"
            is LxBluetoothReconnectState.Blocked -> {
                val blockedState = reconnectState as LxBluetoothReconnectState.Blocked
                if (blockedState.reason == LxBluetoothReconnectBlockReason.DEVICE_NOT_BONDED) {
                    return "Selected device unavailable"
                }
            }
            is LxBluetoothReconnectState.Exhausted,
            LxBluetoothReconnectState.Idle -> Unit
        }
        return when (connectionState) {
            is BluetoothConnectionState.Connecting -> "Connecting"
            is BluetoothConnectionState.Connected -> "Connected"
            is BluetoothConnectionState.Error -> "Connection failed"
            BluetoothConnectionState.Disconnected -> when {
                selectedDeviceAddress != null && !selectedDeviceAvailable ->
                    "Selected device unavailable"
                bondedDevices.isEmpty() -> "No bonded devices"
                else -> "Disconnected"
            }
        }
    }

    private fun LxBluetoothControlState.toHealthText(): String? {
        if (connectionState is BluetoothConnectionState.Connected && lastReceivedAgeMs == null) {
            return "Stream waiting for first sentence."
        }
        if (lastReceivedAgeMs == null && rollingSentenceRatePerSecond <= 0.0) return null

        val linkText = when {
            streamAlive -> "Stream alive"
            connectionState is BluetoothConnectionState.Connected -> "Stream stale"
            else -> "Last stream sample"
        }
        val ageText = lastReceivedAgeMs?.let { "${it} ms ago" } ?: "not yet received"
        val rateText = String.format(Locale.US, "%.1f sentences/s", rollingSentenceRatePerSecond)
        return "$linkText, last data $ageText, $rateText."
    }

    private fun LxBluetoothControlState.toReconnectText(): String? =
        when (val state = reconnectState) {
            is LxBluetoothReconnectState.Waiting ->
                "Reconnect scheduled: attempt ${state.attemptNumber}/${state.maxAttempts} in ${state.delayMs / 1_000}s."

            is LxBluetoothReconnectState.Attempting ->
                "Reconnect attempt ${state.attemptNumber}/${state.maxAttempts} in progress."

            is LxBluetoothReconnectState.Blocked -> when (state.reason) {
                LxBluetoothReconnectBlockReason.PERMISSION_REQUIRED ->
                    "Reconnect stopped: Bluetooth permission required."

                LxBluetoothReconnectBlockReason.DEVICE_NOT_BONDED ->
                    "Reconnect stopped: selected device is not bonded."

                LxBluetoothReconnectBlockReason.SELECTION_CHANGED,
                LxBluetoothReconnectBlockReason.DIFFERENT_DEVICE_REQUESTED -> null
            }

            is LxBluetoothReconnectState.Exhausted ->
                "Reconnect stopped after ${state.attempts} attempts."

            LxBluetoothReconnectState.Idle -> null
        }

    private fun LxBluetoothControlState.toFailureText(): String? =
        when (val disconnectReason = lastDisconnectReason) {
            null -> when {
                reconnectState is LxBluetoothReconnectState.Blocked ->
                    (reconnectState as LxBluetoothReconnectState.Blocked).reason.toFailureText()
                reconnectState is LxBluetoothReconnectState.Exhausted ->
                    "Reconnect attempts exhausted."
                else -> null
            }
            else -> disconnectReason.toFailureText()
        }

    private fun LxBluetoothDisconnectReason.toFailureText(): String =
        when (this) {
            LxBluetoothDisconnectReason.PERMISSION_REQUIRED ->
                "Bluetooth permission is required."

            LxBluetoothDisconnectReason.DEVICE_NOT_BONDED ->
                "Selected device is not bonded."

            LxBluetoothDisconnectReason.CONNECT_FAILED ->
                "Could not connect to the selected device."

            LxBluetoothDisconnectReason.STREAM_CLOSED ->
                "Bluetooth stream closed."

            LxBluetoothDisconnectReason.READ_FAILED ->
                "Bluetooth read failed."

            LxBluetoothDisconnectReason.RETRIES_EXHAUSTED ->
                "Reconnect attempts exhausted."
        }

    private fun LxBluetoothReconnectBlockReason.toFailureText(): String? =
        when (this) {
            LxBluetoothReconnectBlockReason.PERMISSION_REQUIRED ->
                "Bluetooth permission is required."

            LxBluetoothReconnectBlockReason.DEVICE_NOT_BONDED ->
                "Selected device is not bonded."

            LxBluetoothReconnectBlockReason.SELECTION_CHANGED,
            LxBluetoothReconnectBlockReason.DIFFERENT_DEVICE_REQUESTED -> null
        }

    private fun formatDeviceLabel(
        address: String?,
        displayName: String?,
        emptyLabel: String
    ): String {
        if (address == null) return emptyLabel
        return when {
            displayName.isNullOrBlank() -> address
            else -> "$displayName ($address)"
        }
    }
}


