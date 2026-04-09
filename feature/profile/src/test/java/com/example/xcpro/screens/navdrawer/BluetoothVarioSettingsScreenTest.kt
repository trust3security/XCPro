package com.example.xcpro.screens.navdrawer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BluetoothVarioSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun permission_cta_visible_when_required() {
        setContent(
            BluetoothVarioSettingsUiState(
                permissionRequired = true,
                statusText = "Bluetooth permission required"
            )
        )

        composeRule.onNodeWithTag(BLUETOOTH_VARIO_TAG_PERMISSION_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithText("Grant Bluetooth permission").assertIsDisplayed()
    }

    @Test
    fun bonded_list_renders_after_permission() {
        setContent(
            BluetoothVarioSettingsUiState(
                bondedDevices = listOf(
                    BluetoothBondedDeviceRowUiState(
                        address = "AA:BB",
                        title = "LXNAV S100",
                        subtitle = "AA:BB",
                        isSelected = true
                    )
                ),
                selectedDeviceLabel = "LXNAV S100 (AA:BB)",
                statusText = "Disconnected",
                connectEnabled = true
            )
        )

        composeRule.onNodeWithTag(bluetoothVarioDeviceRowTag("AA:BB")).assertIsDisplayed()
        composeRule.onNodeWithText("LXNAV S100").assertIsDisplayed()
        composeRule.onNodeWithText("AA:BB").assertIsDisplayed()
    }

    @Test
    fun connect_and_disconnect_actions_dispatch() {
        var connectClicks = 0
        var disconnectClicks = 0
        setContent(
            BluetoothVarioSettingsUiState(
                statusText = "Disconnected",
                connectEnabled = true,
                disconnectEnabled = true
            ),
            onConnect = { connectClicks += 1 },
            onDisconnect = { disconnectClicks += 1 }
        )

        composeRule.onNodeWithTag(BLUETOOTH_VARIO_TAG_CONNECT_BUTTON).performClick()
        composeRule.onNodeWithTag(BLUETOOTH_VARIO_TAG_DISCONNECT_BUTTON).performClick()

        assertEquals(1, connectClicks)
        assertEquals(1, disconnectClicks)
    }

    private fun setContent(
        uiState: BluetoothVarioSettingsUiState,
        onRequestPermission: () -> Unit = {},
        onSelectDevice: (String) -> Unit = {},
        onConnect: () -> Unit = {},
        onDisconnect: () -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                BluetoothVarioSettingsContent(
                    uiState = uiState,
                    onRequestPermission = onRequestPermission,
                    onSelectDevice = onSelectDevice,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect
                )
            }
        }
    }
}
