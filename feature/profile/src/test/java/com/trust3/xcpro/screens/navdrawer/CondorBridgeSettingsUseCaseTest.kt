package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.livesource.DesiredLiveMode
import com.trust3.xcpro.livesource.DesiredLiveModePreferencesRepository
import com.trust3.xcpro.simulator.CondorBridgeRef
import com.trust3.xcpro.simulator.CondorConnectionState
import com.trust3.xcpro.simulator.CondorLiveDegradedReason
import com.trust3.xcpro.simulator.CondorLiveState
import com.trust3.xcpro.simulator.CondorReconnectState
import com.trust3.xcpro.simulator.CondorSessionState
import com.trust3.xcpro.simulator.CondorStreamFreshness
import com.trust3.xcpro.simulator.CondorTransportKind
import com.trust3.xcpro.simulator.condor.CondorBondedBridgeItem
import com.trust3.xcpro.simulator.condor.CondorBridgeControlPort
import com.trust3.xcpro.simulator.condor.CondorBridgeSettingsState
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
class CondorBridgeSettingsUseCaseTest {

    @Test
    fun maps_connected_condor_state_to_ui_state() = runTest {
        val controlPort = mock<CondorBridgeControlPort>()
        val desiredLiveModeRepository = mock<DesiredLiveModePreferencesRepository>()
        whenever(controlPort.settingsState).thenReturn(
            MutableStateFlow(
                CondorBridgeSettingsState(
                    bondedBridges = listOf(
                        CondorBondedBridgeItem(
                            bridge = TEST_CONDOR_BRIDGE,
                            isSelected = true
                        )
                    ),
                    liveState = CondorLiveState(
                        selectedBridge = TEST_CONDOR_BRIDGE,
                        activeBridge = TEST_CONDOR_BRIDGE,
                        session = CondorSessionState(
                            connection = CondorConnectionState.CONNECTED,
                            freshness = CondorStreamFreshness.HEALTHY,
                            lastReceiveElapsedRealtimeMs = 5_500L
                        )
                    ),
                    connectEnabled = false,
                    disconnectEnabled = true,
                    clearSelectionEnabled = true
                )
            )
        )
        whenever(desiredLiveModeRepository.desiredLiveMode).thenReturn(
            MutableStateFlow(DesiredLiveMode.CONDOR2_FULL)
        )

        val useCase = CondorBridgeSettingsUseCase(
            controlPort = controlPort,
            desiredLiveModeRepository = desiredLiveModeRepository,
            clock = FakeClock(monoMs = 6_000L)
        )
        val uiState = useCase.uiState.first()

        assertEquals(DesiredLiveMode.CONDOR2_FULL, uiState.desiredLiveMode)
        assertEquals(CondorTransportKind.BLUETOOTH, uiState.selectedTransport)
        assertEquals("Condor Bridge (AA:BB)", uiState.selectedEndpointLabel)
        assertEquals("Condor Bridge (AA:BB)", uiState.activeEndpointLabel)
        assertEquals("Connected", uiState.statusText)
        assertEquals("Stream healthy, last data 500 ms ago.", uiState.healthText)
        assertFalse(uiState.connectEnabled)
        assertTrue(uiState.disconnectEnabled)
        assertTrue(uiState.clearSelectionEnabled)
    }

    @Test
    fun maps_blocked_unavailable_bridge_state_to_ui_state() = runTest {
        val controlPort = mock<CondorBridgeControlPort>()
        val desiredLiveModeRepository = mock<DesiredLiveModePreferencesRepository>()
        whenever(controlPort.settingsState).thenReturn(
            MutableStateFlow(
                CondorBridgeSettingsState(
                    selectedBridgeAvailable = false,
                    liveState = CondorLiveState(
                        selectedBridge = TEST_CONDOR_BRIDGE,
                        reconnect = CondorReconnectState.BLOCKED,
                        lastFailure = CondorLiveDegradedReason.DISCONNECTED
                    ),
                    clearSelectionEnabled = true
                )
            )
        )
        whenever(desiredLiveModeRepository.desiredLiveMode).thenReturn(
            MutableStateFlow(DesiredLiveMode.PHONE_ONLY)
        )

        val useCase = CondorBridgeSettingsUseCase(
            controlPort = controlPort,
            desiredLiveModeRepository = desiredLiveModeRepository,
            clock = FakeClock()
        )
        val uiState = useCase.uiState.first()

        assertEquals(DesiredLiveMode.PHONE_ONLY, uiState.desiredLiveMode)
        assertEquals("Saved bridge unavailable", uiState.statusText)
        assertEquals(
            "Saved bridge is not currently bonded. Clear it or select another bridge.",
            uiState.selectedBridgeWarningText
        )
        assertEquals(
            "Reconnect blocked until the saved bridge is available or cleared.",
            uiState.reconnectText
        )
        assertEquals("Bridge disconnected.", uiState.failureText)
    }

    @Test
    fun maps_tcp_listener_state_to_ui_state() = runTest {
        val controlPort = mock<CondorBridgeControlPort>()
        val desiredLiveModeRepository = mock<DesiredLiveModePreferencesRepository>()
        whenever(controlPort.settingsState).thenReturn(
            MutableStateFlow(
                CondorBridgeSettingsState(
                    selectedTransport = CondorTransportKind.TCP_LISTENER,
                    tcpListenPort = 4_353,
                    tcpLocalIpAddress = "192.168.1.20",
                    liveState = CondorLiveState(
                        selectedTransport = CondorTransportKind.TCP_LISTENER,
                        session = CondorSessionState(
                            connection = CondorConnectionState.CONNECTING
                        )
                    )
                )
            )
        )
        whenever(desiredLiveModeRepository.desiredLiveMode).thenReturn(
            MutableStateFlow(DesiredLiveMode.CONDOR2_FULL)
        )

        val useCase = CondorBridgeSettingsUseCase(
            controlPort = controlPort,
            desiredLiveModeRepository = desiredLiveModeRepository,
            clock = FakeClock()
        )
        val uiState = useCase.uiState.first()

        assertEquals(CondorTransportKind.TCP_LISTENER, uiState.selectedTransport)
        assertEquals("192.168.1.20:4353", uiState.selectedEndpointLabel)
        assertEquals("192.168.1.20:4353", uiState.activeEndpointLabel)
        assertEquals("Listening for connection", uiState.statusText)
        assertEquals(4_353, uiState.tcpListenPort)
        assertEquals("192.168.1.20", uiState.tcpLocalIpAddress)
    }

    @Test
    fun forwards_bridge_actions() = runTest {
        val controlPort = mock<CondorBridgeControlPort>()
        val desiredLiveModeRepository = mock<DesiredLiveModePreferencesRepository>()
        whenever(controlPort.settingsState).thenReturn(
            MutableStateFlow(
                CondorBridgeSettingsState(
                    bondedBridges = listOf(
                        CondorBondedBridgeItem(
                            bridge = TEST_CONDOR_BRIDGE,
                            isSelected = false
                        )
                    )
                )
            )
        )
        whenever(desiredLiveModeRepository.desiredLiveMode).thenReturn(
            MutableStateFlow(DesiredLiveMode.PHONE_ONLY)
        )
        val useCase = CondorBridgeSettingsUseCase(
            controlPort = controlPort,
            desiredLiveModeRepository = desiredLiveModeRepository,
            clock = FakeClock()
        )

        useCase.refresh()
        useCase.selectTransport(CondorTransportKind.TCP_LISTENER)
        useCase.updateTcpListenPort(5_000)
        useCase.selectBridge(TEST_CONDOR_BRIDGE.stableId)
        useCase.setDesiredLiveMode(DesiredLiveMode.CONDOR2_FULL)
        useCase.connect()
        useCase.disconnect()
        useCase.clearSelectedBridge()

        verify(controlPort).refresh()
        verify(controlPort).selectTransport(CondorTransportKind.TCP_LISTENER)
        verify(controlPort).updateTcpListenPort(5_000)
        verify(controlPort).selectBridge(TEST_CONDOR_BRIDGE)
        verify(desiredLiveModeRepository).setDesiredLiveMode(DesiredLiveMode.CONDOR2_FULL)
        verify(controlPort).connect()
        verify(controlPort).disconnect()
        verify(controlPort).clearSelectedBridge()
    }
}

private val TEST_CONDOR_BRIDGE = CondorBridgeRef(
    stableId = "AA:BB",
    displayName = "Condor Bridge"
)
