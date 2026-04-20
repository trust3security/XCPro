package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.simulator.CondorReconnectState
import com.trust3.xcpro.simulator.CondorTransportKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CondorBridgeControllerTest {

    @Test
    fun selecting_tcp_transport_disconnects_bluetooth_and_keeps_saved_bridge() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = createFixture(dispatcher)
        try {
            fixture.controller.refresh()
            fixture.controller.selectBridge(TEST_CONDOR_BRIDGE_A)
            fixture.controller.connect()
            runCurrent()

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Connected(
                    TEST_CONDOR_BRIDGE_A.toBondedDevice()
                )
            runCurrent()

            fixture.controller.selectTransport(CondorTransportKind.TCP_LISTENER)
            runCurrent()

            assertEquals(
                CondorTransportKind.TCP_LISTENER,
                fixture.transportPreferencesRepository.selectedTransport.value
            )
            assertEquals(
                TEST_CONDOR_BRIDGE_A.stableId,
                fixture.selectedBridgeRepository.selectedBridge.value?.stableId
            )
            assertEquals(
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Disconnected,
                fixture.bluetoothTransport.connectionState.value
            )
        } finally {
            fixture.shutdown()
        }
    }

    @Test
    fun tcp_transport_connects_without_bluetooth_permission() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = createFixture(dispatcher, bluetoothPermissionGranted = false)
        try {
            fixture.controller.selectTransport(CondorTransportKind.TCP_LISTENER)
            fixture.controller.refresh()
            runCurrent()

            assertTrue(fixture.controller.settingsState.value.connectEnabled)

            fixture.controller.connect()
            runCurrent()

            assertEquals(listOf(CondorTcpPortSpec.DEFAULT_PORT), fixture.tcpServerPort.openedPorts)
        } finally {
            fixture.shutdown()
        }
    }

    @Test
    fun updating_tcp_port_while_listening_is_ignored() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = createFixture(dispatcher)
        try {
            fixture.controller.selectTransport(CondorTransportKind.TCP_LISTENER)
            fixture.controller.updateTcpListenPort(5_000)
            fixture.controller.connect()
            runCurrent()

            fixture.tcpServerPort.mutableConnectionState.value =
                CondorTcpServerState.Listening(5_000)
            runCurrent()

            fixture.controller.updateTcpListenPort(6_000)
            runCurrent()

            assertEquals(5_000, fixture.transportPreferencesRepository.tcpListenPort.value)
            assertEquals(5_000, fixture.controller.settingsState.value.tcpListenPort)
        } finally {
            fixture.shutdown()
        }
    }

    @Test
    fun missing_saved_bridge_surfaces_blocked_state() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = createFixture(dispatcher)
        try {
            fixture.controller.selectBridge(TEST_CONDOR_BRIDGE_A)
            runCurrent()

            fixture.bluetoothTransport.bondedDevices = emptyList()
            fixture.controller.refresh()
            runCurrent()

            assertEquals(
                CondorReconnectState.BLOCKED,
                fixture.sessionRepository.state.value.reconnect
            )
            assertFalse(fixture.controller.settingsState.value.selectedBridgeAvailable)
        } finally {
            fixture.shutdown()
        }
    }

    @Test
    fun transient_disconnect_does_not_clear_saved_bridge() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = createFixture(dispatcher)
        try {
            fixture.controller.refresh()
            fixture.controller.selectBridge(TEST_CONDOR_BRIDGE_A)
            fixture.controller.connect()
            runCurrent()

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Connected(
                    TEST_CONDOR_BRIDGE_A.toBondedDevice()
                )
            runCurrent()

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Disconnected
            runCurrent()

            assertEquals(
                TEST_CONDOR_BRIDGE_A.stableId,
                fixture.selectedBridgeRepository.selectedBridge.value?.stableId
            )
        } finally {
            fixture.shutdown()
        }
    }

    @Test
    fun clear_selected_bridge_during_waiting_reconnect_cancels_old_target() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = createFixture(dispatcher)
        try {
            fixture.controller.refresh()
            fixture.controller.selectBridge(TEST_CONDOR_BRIDGE_A)
            fixture.controller.connect()
            runCurrent()

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Connected(
                    TEST_CONDOR_BRIDGE_A.toBondedDevice()
                )
            runCurrent()

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Error(
                    device = TEST_CONDOR_BRIDGE_A.toBondedDevice(),
                    error = com.trust3.xcpro.bluetooth.BluetoothConnectionError.READ_FAILED
                )
            runCurrent()
            assertEquals(CondorReconnectState.WAITING, fixture.sessionRepository.state.value.reconnect)

            fixture.controller.clearSelectedBridge()
            runCurrent()

            assertEquals(null, fixture.selectedBridgeRepository.selectedBridge.value)
            assertEquals(CondorReconnectState.IDLE, fixture.sessionRepository.state.value.reconnect)
            assertEquals(null, fixture.sessionRepository.state.value.activeBridge)

            advanceTimeBy(5_000L)
            runCurrent()

            assertEquals(1, fixture.bluetoothTransport.openedDevices.size)
            assertEquals(CondorReconnectState.IDLE, fixture.sessionRepository.state.value.reconnect)
        } finally {
            fixture.shutdown()
        }
    }

    @Test
    fun connect_while_waiting_reconnect_starts_immediate_retry_and_cancels_backoff() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = createFixture(dispatcher)
        try {
            fixture.controller.refresh()
            fixture.controller.selectBridge(TEST_CONDOR_BRIDGE_A)
            fixture.controller.connect()
            runCurrent()

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Connected(
                    TEST_CONDOR_BRIDGE_A.toBondedDevice()
                )
            runCurrent()

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Error(
                    device = TEST_CONDOR_BRIDGE_A.toBondedDevice(),
                    error = com.trust3.xcpro.bluetooth.BluetoothConnectionError.READ_FAILED
                )
            runCurrent()

            assertEquals(CondorReconnectState.WAITING, fixture.sessionRepository.state.value.reconnect)
            assertTrue(fixture.controller.settingsState.value.connectEnabled)

            fixture.controller.connect()
            runCurrent()

            assertEquals(2, fixture.bluetoothTransport.openedDevices.size)
            assertEquals(CondorReconnectState.IDLE, fixture.sessionRepository.state.value.reconnect)

            advanceTimeBy(5_000L)
            runCurrent()

            assertEquals(2, fixture.bluetoothTransport.openedDevices.size)
        } finally {
            fixture.shutdown()
        }
    }

    @Test
    fun selecting_different_bridge_while_waiting_reconnect_forgets_previous_target() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = createFixture(dispatcher)
        try {
            fixture.controller.refresh()
            fixture.controller.selectBridge(TEST_CONDOR_BRIDGE_A)
            fixture.controller.connect()
            runCurrent()

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Connected(
                    TEST_CONDOR_BRIDGE_A.toBondedDevice()
                )
            runCurrent()

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Error(
                    device = TEST_CONDOR_BRIDGE_A.toBondedDevice(),
                    error = com.trust3.xcpro.bluetooth.BluetoothConnectionError.READ_FAILED
                )
            runCurrent()
            assertEquals(CondorReconnectState.WAITING, fixture.sessionRepository.state.value.reconnect)

            fixture.controller.selectBridge(TEST_CONDOR_BRIDGE_B)
            runCurrent()

            assertEquals(
                TEST_CONDOR_BRIDGE_B.stableId,
                fixture.selectedBridgeRepository.selectedBridge.value?.stableId
            )
            assertEquals(CondorReconnectState.IDLE, fixture.sessionRepository.state.value.reconnect)

            advanceTimeBy(5_000L)
            runCurrent()

            assertEquals(1, fixture.bluetoothTransport.openedDevices.size)

            fixture.controller.connect()
            runCurrent()

            assertEquals(2, fixture.bluetoothTransport.openedDevices.size)
            assertEquals(
                TEST_CONDOR_BRIDGE_B.stableId,
                fixture.bluetoothTransport.openedDevices.last().address
            )
        } finally {
            fixture.shutdown()
        }
    }

    @Test
    fun selecting_different_bridge_while_connected_prevents_old_reconnect_after_drop() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = createFixture(dispatcher)
        try {
            fixture.controller.refresh()
            fixture.controller.selectBridge(TEST_CONDOR_BRIDGE_A)
            fixture.controller.connect()
            runCurrent()

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Connected(
                    TEST_CONDOR_BRIDGE_A.toBondedDevice()
                )
            runCurrent()

            fixture.controller.selectBridge(TEST_CONDOR_BRIDGE_B)
            runCurrent()

            assertEquals(
                TEST_CONDOR_BRIDGE_B.stableId,
                fixture.selectedBridgeRepository.selectedBridge.value?.stableId
            )
            assertEquals(
                TEST_CONDOR_BRIDGE_A.stableId,
                fixture.sessionRepository.state.value.activeBridge?.stableId
            )

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Error(
                    device = TEST_CONDOR_BRIDGE_A.toBondedDevice(),
                    error = com.trust3.xcpro.bluetooth.BluetoothConnectionError.READ_FAILED
                )
            runCurrent()
            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Disconnected
            runCurrent()

            advanceTimeBy(5_000L)
            runCurrent()

            assertEquals(1, fixture.bluetoothTransport.openedDevices.size)
            assertEquals(CondorReconnectState.IDLE, fixture.sessionRepository.state.value.reconnect)
        } finally {
            fixture.shutdown()
        }
    }

    @Test
    fun clear_selected_bridge_during_reconnect_attempt_aborts_inflight_attempt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = createFixture(dispatcher)
        try {
            fixture.controller.refresh()
            fixture.controller.selectBridge(TEST_CONDOR_BRIDGE_A)
            fixture.controller.connect()
            runCurrent()

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Connected(
                    TEST_CONDOR_BRIDGE_A.toBondedDevice()
                )
            runCurrent()

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Error(
                    device = TEST_CONDOR_BRIDGE_A.toBondedDevice(),
                    error = com.trust3.xcpro.bluetooth.BluetoothConnectionError.READ_FAILED
                )
            runCurrent()
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(CondorReconnectState.ATTEMPTING, fixture.sessionRepository.state.value.reconnect)
            assertEquals(2, fixture.bluetoothTransport.openedDevices.size)
            assertFalse(fixture.controller.settingsState.value.connectEnabled)

            fixture.controller.clearSelectedBridge()
            runCurrent()

            assertEquals(null, fixture.selectedBridgeRepository.selectedBridge.value)
            assertEquals(CondorReconnectState.IDLE, fixture.sessionRepository.state.value.reconnect)
            assertEquals(null, fixture.sessionRepository.state.value.activeBridge)

            advanceTimeBy(5_000L)
            runCurrent()

            assertEquals(2, fixture.bluetoothTransport.openedDevices.size)
        } finally {
            fixture.shutdown()
        }
    }

    @Test
    fun connect_after_reconnect_exhausted_starts_fresh_attempt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = createFixture(dispatcher)
        try {
            fixture.controller.refresh()
            fixture.controller.selectBridge(TEST_CONDOR_BRIDGE_A)
            fixture.controller.connect()
            runCurrent()

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Connected(
                    TEST_CONDOR_BRIDGE_A.toBondedDevice()
                )
            runCurrent()

            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Error(
                    device = TEST_CONDOR_BRIDGE_A.toBondedDevice(),
                    error = com.trust3.xcpro.bluetooth.BluetoothConnectionError.READ_FAILED
                )
            runCurrent()
            advanceTimeBy(1_000L)
            runCurrent()
            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Connecting(
                    TEST_CONDOR_BRIDGE_A.toBondedDevice()
                )
            runCurrent()
            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Error(
                    device = TEST_CONDOR_BRIDGE_A.toBondedDevice(),
                    error = com.trust3.xcpro.bluetooth.BluetoothConnectionError.READ_FAILED
                )
            runCurrent()
            advanceTimeBy(2_000L)
            runCurrent()
            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Connecting(
                    TEST_CONDOR_BRIDGE_A.toBondedDevice()
                )
            runCurrent()
            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Error(
                    device = TEST_CONDOR_BRIDGE_A.toBondedDevice(),
                    error = com.trust3.xcpro.bluetooth.BluetoothConnectionError.READ_FAILED
                )
            runCurrent()
            advanceTimeBy(5_000L)
            runCurrent()
            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Connecting(
                    TEST_CONDOR_BRIDGE_A.toBondedDevice()
                )
            runCurrent()
            fixture.bluetoothTransport.mutableConnectionState.value =
                com.trust3.xcpro.bluetooth.BluetoothConnectionState.Error(
                    device = TEST_CONDOR_BRIDGE_A.toBondedDevice(),
                    error = com.trust3.xcpro.bluetooth.BluetoothConnectionError.READ_FAILED
                )
            runCurrent()

            assertEquals(CondorReconnectState.EXHAUSTED, fixture.sessionRepository.state.value.reconnect)
            assertTrue(fixture.controller.settingsState.value.connectEnabled)
            assertEquals(4, fixture.bluetoothTransport.openedDevices.size)

            fixture.controller.connect()
            runCurrent()

            assertEquals(5, fixture.bluetoothTransport.openedDevices.size)
            assertEquals(CondorReconnectState.IDLE, fixture.sessionRepository.state.value.reconnect)
        } finally {
            fixture.shutdown()
        }
    }

    private fun createFixture(
        dispatcher: TestDispatcher,
        bluetoothPermissionGranted: Boolean = true
    ): ControllerFixture {
        val clock = FakeClock()
        val bluetoothTransport = FakeBluetoothTransport(
            bondedDevices = listOf(
                TEST_CONDOR_BRIDGE_A.toBondedDevice(),
                TEST_CONDOR_BRIDGE_B.toBondedDevice()
            )
        )
        val permissionPort = FakeBluetoothConnectPermissionPort(granted = bluetoothPermissionGranted)
        val selectedBridgeRepository =
            CondorSelectedBridgeRepository(FakeCondorSelectedBridgeStorage(), Unit)
        val transportPreferencesRepository =
            CondorTransportPreferencesRepository(FakeCondorTransportPreferencesStorage(), Unit)
        val liveSampleRepository = CondorLiveSampleRepository(
            parser = CondorSentenceParser(),
            clock = clock
        )
        val bridgeTransport = CondorBridgeTransport(
            bluetoothTransport = bluetoothTransport,
            clock = clock,
            liveSampleRepository = liveSampleRepository,
            dispatcher = dispatcher
        )
        val tcpServerPort = FakeCondorTcpServerPort()
        val tcpTransport = CondorTcpBridgeTransport(
            tcpServerPort = tcpServerPort,
            clock = clock,
            liveSampleRepository = liveSampleRepository,
            dispatcher = dispatcher
        )
        val sessionRepository = CondorSessionRepository(
            selectedBridgeRepository = selectedBridgeRepository,
            transportPreferencesRepository = transportPreferencesRepository,
            bluetoothTransport = bridgeTransport,
            tcpTransport = tcpTransport,
            dispatcher = dispatcher
        )
        val controller = CondorBridgeController(
            bluetoothTransport = bluetoothTransport,
            permissionPort = permissionPort,
            selectedBridgeRepository = selectedBridgeRepository,
            transportPreferencesRepository = transportPreferencesRepository,
            sessionRepository = sessionRepository,
            bridgeTransport = bridgeTransport,
            tcpTransport = tcpTransport,
            localNetworkInfoPort = FakeLocalNetworkInfoPort(),
            dispatcher = dispatcher
        )
        return ControllerFixture(
            bluetoothTransport = bluetoothTransport,
            selectedBridgeRepository = selectedBridgeRepository,
            transportPreferencesRepository = transportPreferencesRepository,
            sessionRepository = sessionRepository,
            bridgeTransport = bridgeTransport,
            tcpServerPort = tcpServerPort,
            tcpTransport = tcpTransport,
            controller = controller
        )
    }
}

private data class ControllerFixture(
    val bluetoothTransport: FakeBluetoothTransport,
    val selectedBridgeRepository: CondorSelectedBridgeRepository,
    val transportPreferencesRepository: CondorTransportPreferencesRepository,
    val sessionRepository: CondorSessionRepository,
    val bridgeTransport: CondorBridgeTransport,
    val tcpServerPort: FakeCondorTcpServerPort,
    val tcpTransport: CondorTcpBridgeTransport,
    val controller: CondorBridgeController
) {
    fun shutdown() {
        controller.shutdown()
        sessionRepository.shutdown()
        bridgeTransport.shutdown()
        tcpTransport.shutdown()
    }
}
