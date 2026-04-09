package com.example.xcpro.screens.navdrawer

import com.example.xcpro.variometer.bluetooth.BluetoothConnectionError
import com.example.xcpro.variometer.bluetooth.BluetoothConnectionState
import com.example.xcpro.variometer.bluetooth.lxnav.control.BluetoothBondedDeviceItem
import com.example.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothControlPort
import com.example.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothControlState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BluetoothVarioSettingsUseCaseTest {

    @Test
    fun maps_control_port_state_to_ui_state() = runTest {
        val controlPort = mock<LxBluetoothControlPort>()
        whenever(controlPort.state).thenReturn(
            MutableStateFlow(
                LxBluetoothControlState(
                    permissionRequired = false,
                    bondedDevices = listOf(
                        BluetoothBondedDeviceItem(
                            address = "AA:BB",
                            displayName = "LXNAV S100"
                        )
                    ),
                    selectedDeviceAddress = "AA:BB",
                    selectedDeviceDisplayName = "LXNAV S100",
                    selectedDeviceAvailable = true,
                    activeDeviceAddress = "AA:BB",
                    activeDeviceName = "LXNAV S100",
                    connectionState = BluetoothConnectionState.Connected(
                        device = com.example.xcpro.variometer.bluetooth.BondedBluetoothDevice(
                            address = "AA:BB",
                            displayName = "LXNAV S100"
                        )
                    ),
                    lastError = null,
                    canConnect = false,
                    canDisconnect = true
                )
            )
        )

        val useCase = BluetoothVarioSettingsUseCase(controlPort)
        val uiState = useCase.uiState.first()

        assertEquals("LXNAV S100 (AA:BB)", uiState.selectedDeviceLabel)
        assertEquals("LXNAV S100 (AA:BB)", uiState.activeDeviceLabel)
        assertEquals("Connected", uiState.statusText)
        assertEquals(1, uiState.bondedDevices.size)
        assertFalse(uiState.connectEnabled)
        assertTrue(uiState.disconnectEnabled)
    }

    @Test
    fun permission_required_and_unavailable_selected_device_map_cleanly() = runTest {
        val controlPort = mock<LxBluetoothControlPort>()
        whenever(controlPort.state).thenReturn(
            MutableStateFlow(
                LxBluetoothControlState(
                    permissionRequired = true,
                    selectedDeviceAddress = "AA:BB",
                    selectedDeviceDisplayName = "LXNAV S100",
                    selectedDeviceAvailable = false,
                    connectionState = BluetoothConnectionState.Error(
                        device = null,
                        error = BluetoothConnectionError.PERMISSION_REQUIRED
                    ),
                    lastError = BluetoothConnectionError.PERMISSION_REQUIRED,
                    canConnect = false,
                    canDisconnect = true
                )
            )
        )

        val useCase = BluetoothVarioSettingsUseCase(controlPort)
        val uiState = useCase.uiState.first()

        assertTrue(uiState.permissionRequired)
        assertEquals("Bluetooth permission required", uiState.statusText)
        assertEquals("Selected device is not currently bonded.", uiState.selectedDeviceWarningText)
        assertEquals("Bluetooth permission is required.", uiState.failureText)
    }

    @Test
    fun forwards_select_connect_disconnect_and_permission_actions() = runTest {
        val controlPort = mock<LxBluetoothControlPort>()
        whenever(controlPort.state).thenReturn(MutableStateFlow(LxBluetoothControlState()))
        val useCase = BluetoothVarioSettingsUseCase(controlPort)

        useCase.refresh()
        useCase.selectDevice("AA:BB")
        useCase.connect()
        useCase.disconnect()
        useCase.onPermissionResult(true)

        verify(controlPort).refresh()
        verify(controlPort).selectDevice("AA:BB")
        verify(controlPort).connectSelected()
        verify(controlPort).disconnect()
        verify(controlPort).onPermissionResult(true)
    }
}
