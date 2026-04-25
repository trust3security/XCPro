package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.bluetooth.BluetoothConnectionError
import com.trust3.xcpro.bluetooth.BluetoothConnectionState
import com.trust3.xcpro.variometer.bluetooth.lxnav.control.BluetoothBondedDeviceItem
import com.trust3.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothDisconnectReason
import com.trust3.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothReconnectState
import com.trust3.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothControlPort
import com.trust3.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothControlState
import com.trust3.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothDetailRow
import com.trust3.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothDetailSection
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
                        device = com.trust3.xcpro.bluetooth.BondedBluetoothDevice(
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
        assertEquals("Stream waiting for first sentence.", uiState.healthText)
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
        assertEquals(null, uiState.failureText)
    }

    @Test
    fun reconnect_and_health_projection_map_cleanly() = runTest {
        val controlPort = mock<LxBluetoothControlPort>()
        whenever(controlPort.state).thenReturn(
            MutableStateFlow(
                LxBluetoothControlState(
                    selectedDeviceAddress = "AA:BB",
                    selectedDeviceDisplayName = "LXNAV S100",
                    selectedDeviceAvailable = true,
                    connectionState = BluetoothConnectionState.Disconnected,
                    lastDisconnectReason = LxBluetoothDisconnectReason.RETRIES_EXHAUSTED,
                    reconnectState = LxBluetoothReconnectState.Waiting(
                        attemptNumber = 2,
                        maxAttempts = 3,
                        delayMs = 2_000L
                    ),
                    reconnectCount = 1,
                    streamAlive = false,
                    lastReceivedAgeMs = 250L,
                    rollingSentenceRatePerSecond = 2.5,
                    canConnect = false,
                    canDisconnect = true
                )
            )
        )

        val useCase = BluetoothVarioSettingsUseCase(controlPort)
        val uiState = useCase.uiState.first()

        assertEquals("Reconnecting", uiState.statusText)
        assertEquals(
            "Last stream sample, last data 250 ms ago, 2.5 sentences/s.",
            uiState.healthText
        )
        assertEquals(
            "Reconnect scheduled: attempt 2/3 in 2s.",
            uiState.reconnectText
        )
        assertEquals("Reconnect attempts exhausted.", uiState.failureText)
    }

    @Test
    fun detail_sections_map_to_ui_state() = runTest {
        val controlPort = mock<LxBluetoothControlPort>()
        whenever(controlPort.state).thenReturn(
            MutableStateFlow(
                LxBluetoothControlState(
                    detailSections = listOf(
                        LxBluetoothDetailSection(
                            title = "Active overrides",
                            rows = listOf(
                                LxBluetoothDetailRow("MacCready", "1.5 m/s"),
                                LxBluetoothDetailRow("QNH", "1008.4 hPa")
                            )
                        ),
                        LxBluetoothDetailSection(
                            title = "Device / environment",
                            rows = listOf(
                                LxBluetoothDetailRow("OAT", "+23.1 C")
                            )
                        )
                    )
                )
            )
        )

        val useCase = BluetoothVarioSettingsUseCase(controlPort)
        val uiState = useCase.uiState.first()

        assertEquals(2, uiState.detailSections.size)
        assertEquals("Active overrides", uiState.detailSections[0].title)
        assertEquals("MacCready", uiState.detailSections[0].rows[0].label)
        assertEquals("1.5 m/s", uiState.detailSections[0].rows[0].value)
        assertEquals("Device / environment", uiState.detailSections[1].title)
        assertEquals("+23.1 C", uiState.detailSections[1].rows[0].value)
    }

    @Test
    fun internal_transport_error_does_not_project_without_disconnect_reason_or_reconnect_state() = runTest {
        val controlPort = mock<LxBluetoothControlPort>()
        whenever(controlPort.state).thenReturn(
            MutableStateFlow(
                LxBluetoothControlState(
                    connectionState = BluetoothConnectionState.Disconnected,
                    lastError = BluetoothConnectionError.READ_FAILED,
                    canConnect = true,
                    canDisconnect = false
                )
            )
        )

        val useCase = BluetoothVarioSettingsUseCase(controlPort)
        val uiState = useCase.uiState.first()

        assertEquals("No bonded devices", uiState.statusText)
        assertEquals(null, uiState.failureText)
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


