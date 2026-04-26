package com.trust3.xcpro.screens.navdrawer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.trust3.xcpro.livesource.DesiredLiveMode
import com.trust3.xcpro.simulator.CondorTransportKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CondorBridgeSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun liveSource_selector_remains_visible_when_bluetooth_permission_is_required() {
        setContent(
            CondorBridgeSettingsUiState(
                desiredLiveMode = DesiredLiveMode.PHONE_ONLY,
                bluetoothPermissionRequired = true
            )
        )

        composeRule.onNodeWithTag(CONDOR_BRIDGE_TAG_LIVE_MODE_PHONE).assertIsDisplayed()
        composeRule.onNodeWithTag(CONDOR_BRIDGE_TAG_LIVE_MODE_CONDOR2).assertIsDisplayed()
    }

    @Test
    fun selecting_tcp_transport_dispatches_callback() {
        var selectedTransport = CondorTransportKind.BLUETOOTH
        setContent(
            CondorBridgeSettingsUiState(selectedTransport = CondorTransportKind.BLUETOOTH),
            onSelectTransport = { selectedTransport = it }
        )

        composeRule.onNodeWithTag(CONDOR_BRIDGE_TAG_TRANSPORT_TCP).performClick()

        assertEquals(CondorTransportKind.TCP_LISTENER, selectedTransport)
    }

    @Test
    fun selecting_condor2_dispatches_mode_callback() {
        var selectedMode = DesiredLiveMode.PHONE_ONLY
        setContent(
            CondorBridgeSettingsUiState(desiredLiveMode = DesiredLiveMode.PHONE_ONLY),
            onSelectLiveMode = { selectedMode = it }
        )

        composeRule.onNodeWithTag(CONDOR_BRIDGE_TAG_LIVE_MODE_CONDOR2).performClick()

        assertEquals(DesiredLiveMode.CONDOR2_FULL, selectedMode)
    }

    @Test
    fun tcp_ip_field_accepts_short_octet_ipv4_address() {
        var updatedIpAddress: String? = null
        setContent(
            CondorBridgeSettingsUiState(selectedTransport = CondorTransportKind.TCP_LISTENER),
            onUpdateTcpIpAddress = { updatedIpAddress = it }
        )

        composeRule
            .onNodeWithTag(CONDOR_BRIDGE_TAG_TCP_IP_ADDRESS)
            .performScrollTo()
            .assertIsDisplayed()
            .performTextInput("192.168.1.2")

        assertEquals("192.168.1.2", updatedIpAddress)
    }

    @Test
    fun tcp_ip_validation_accepts_variable_length_octets() {
        assertEquals(null, tcpIpAddressValidationError("192.168.1.2"))
    }

    private fun setContent(
        uiState: CondorBridgeSettingsUiState,
        onRequestPermission: () -> Unit = {},
        onSelectTransport: (CondorTransportKind) -> Unit = {},
        onUpdateTcpListenPort: (Int) -> Unit = {},
        onUpdateTcpIpAddress: (String?) -> Unit = {},
        onSelectBridge: (String) -> Unit = {},
        onSelectLiveMode: (DesiredLiveMode) -> Unit = {},
        onConnect: () -> Unit = {},
        onDisconnect: () -> Unit = {},
        onClearSelection: () -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                CondorBridgeSettingsContent(
                    uiState = uiState,
                    onRequestPermission = onRequestPermission,
                    onSelectTransport = onSelectTransport,
                    onUpdateTcpListenPort = onUpdateTcpListenPort,
                    onUpdateTcpIpAddress = onUpdateTcpIpAddress,
                    onSelectBridge = onSelectBridge,
                    onSelectLiveMode = onSelectLiveMode,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onClearSelection = onClearSelection
                )
            }
        }
    }
}
