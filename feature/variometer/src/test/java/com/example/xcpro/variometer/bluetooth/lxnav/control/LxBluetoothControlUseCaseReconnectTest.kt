package com.example.xcpro.variometer.bluetooth.lxnav.control

import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.variometer.bluetooth.BluetoothConnectionError
import com.example.xcpro.variometer.bluetooth.BluetoothConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class LxBluetoothControlUseCaseReconnectTest {

    @Test
    fun same_device_reconnect_from_ui_remains_explicit_after_user_disconnect() = runTest {
        val runtimeRepository = mockRuntimeRepository()
        val transport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_DEVICE_A)
        )
        val useCase = useCase(
            transport = transport,
            permissionPort = FakeBluetoothConnectPermissionPort(granted = true),
            runtimeRepository = runtimeRepository,
            selectedRepository = selectedRepository(),
            clock = FakeClock(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase.refresh()
        useCase.selectDevice(TEST_DEVICE_A.address)
        advanceUntilIdle()

        useCase.connectSelected()
        advanceUntilIdle()
        transport.mutableConnectionState.value = BluetoothConnectionState.Connected(TEST_DEVICE_A)
        advanceUntilIdle()

        useCase.disconnect()
        transport.mutableConnectionState.value = BluetoothConnectionState.Disconnected
        advanceUntilIdle()

        useCase.connectSelected()
        advanceUntilIdle()

        verify(runtimeRepository, times(2)).connect(TEST_DEVICE_A)
    }

    @Test
    fun reconnect_after_unexpected_drop_uses_backoff_and_only_after_prior_success() = runTest {
        val runtimeRepository = mockRuntimeRepository()
        val transport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_DEVICE_A)
        )
        val useCase = useCase(
            transport = transport,
            permissionPort = FakeBluetoothConnectPermissionPort(granted = true),
            runtimeRepository = runtimeRepository,
            selectedRepository = selectedRepository(),
            clock = FakeClock(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase.refresh()
        useCase.selectDevice(TEST_DEVICE_A.address)
        useCase.connectSelected()
        advanceUntilIdle()
        verify(runtimeRepository).connect(TEST_DEVICE_A)

        transport.mutableConnectionState.value = BluetoothConnectionState.Connected(TEST_DEVICE_A)
        advanceUntilIdle()
        transport.mutableConnectionState.value = BluetoothConnectionState.Error(
            device = TEST_DEVICE_A,
            error = BluetoothConnectionError.STREAM_CLOSED
        )
        runCurrent()

        assertEquals(
            LxBluetoothReconnectState.Waiting(
                attemptNumber = 1,
                maxAttempts = 3,
                delayMs = 1_000L
            ),
            useCase.state.value.reconnectState
        )
        assertEquals(0, useCase.state.value.reconnectCount)
        assertEquals(
            LxBluetoothDisconnectReason.STREAM_CLOSED,
            useCase.state.value.lastDisconnectReason
        )

        advanceTimeBy(999L)
        runCurrent()
        verify(runtimeRepository, times(1)).connect(TEST_DEVICE_A)

        advanceTimeBy(1L)
        runCurrent()

        verify(runtimeRepository, times(2)).connect(TEST_DEVICE_A)
        assertEquals(
            LxBluetoothReconnectState.Attempting(
                attemptNumber = 1,
                maxAttempts = 3
            ),
            useCase.state.value.reconnectState
        )
        assertEquals(1, useCase.state.value.reconnectCount)
    }

    @Test
    fun first_connect_failure_does_not_auto_retry() = runTest {
        val runtimeRepository = mockRuntimeRepository()
        val transport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_DEVICE_A)
        )
        val useCase = useCase(
            transport = transport,
            permissionPort = FakeBluetoothConnectPermissionPort(granted = true),
            runtimeRepository = runtimeRepository,
            selectedRepository = selectedRepository(),
            clock = FakeClock(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase.refresh()
        useCase.selectDevice(TEST_DEVICE_A.address)
        useCase.connectSelected()
        advanceUntilIdle()

        transport.mutableConnectionState.value = BluetoothConnectionState.Error(
            device = TEST_DEVICE_A,
            error = BluetoothConnectionError.CONNECT_FAILED
        )
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()

        verify(runtimeRepository, times(1)).connect(TEST_DEVICE_A)
        assertEquals(LxBluetoothReconnectState.Idle, useCase.state.value.reconnectState)
        assertEquals(0, useCase.state.value.reconnectCount)
    }

    @Test
    fun reconnect_stops_when_permission_is_lost() = runTest {
        val runtimeRepository = mockRuntimeRepository()
        val transport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_DEVICE_A)
        )
        val permissionPort = FakeBluetoothConnectPermissionPort(granted = true)
        val useCase = useCase(
            transport = transport,
            permissionPort = permissionPort,
            runtimeRepository = runtimeRepository,
            selectedRepository = selectedRepository(),
            clock = FakeClock(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase.refresh()
        useCase.selectDevice(TEST_DEVICE_A.address)
        useCase.connectSelected()
        advanceUntilIdle()

        transport.mutableConnectionState.value = BluetoothConnectionState.Connected(TEST_DEVICE_A)
        advanceUntilIdle()
        transport.mutableConnectionState.value = BluetoothConnectionState.Error(
            device = TEST_DEVICE_A,
            error = BluetoothConnectionError.STREAM_CLOSED
        )
        runCurrent()

        permissionPort.granted = false
        useCase.refresh()
        advanceUntilIdle()
        advanceTimeBy(1_000L)
        runCurrent()

        verify(runtimeRepository, times(1)).connect(TEST_DEVICE_A)
        assertEquals(
            LxBluetoothReconnectState.Blocked(LxBluetoothReconnectBlockReason.PERMISSION_REQUIRED),
            useCase.state.value.reconnectState
        )
        assertEquals(
            LxBluetoothDisconnectReason.PERMISSION_REQUIRED,
            useCase.state.value.lastDisconnectReason
        )
    }

    @Test
    fun reconnect_stops_when_device_is_no_longer_bonded() = runTest {
        val runtimeRepository = mockRuntimeRepository()
        val transport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_DEVICE_A)
        )
        val useCase = useCase(
            transport = transport,
            permissionPort = FakeBluetoothConnectPermissionPort(granted = true),
            runtimeRepository = runtimeRepository,
            selectedRepository = selectedRepository(),
            clock = FakeClock(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase.refresh()
        useCase.selectDevice(TEST_DEVICE_A.address)
        useCase.connectSelected()
        advanceUntilIdle()

        transport.mutableConnectionState.value = BluetoothConnectionState.Connected(TEST_DEVICE_A)
        advanceUntilIdle()
        transport.mutableConnectionState.value = BluetoothConnectionState.Error(
            device = TEST_DEVICE_A,
            error = BluetoothConnectionError.STREAM_CLOSED
        )
        runCurrent()

        transport.bondedDevices = emptyList()
        useCase.refresh()
        advanceUntilIdle()
        advanceTimeBy(1_000L)
        runCurrent()

        verify(runtimeRepository, times(1)).connect(TEST_DEVICE_A)
        assertEquals(
            LxBluetoothReconnectState.Blocked(LxBluetoothReconnectBlockReason.DEVICE_NOT_BONDED),
            useCase.state.value.reconnectState
        )
        assertEquals(
            LxBluetoothDisconnectReason.DEVICE_NOT_BONDED,
            useCase.state.value.lastDisconnectReason
        )
    }

    @Test
    fun selection_change_clears_pending_reconnect_and_manual_connect_can_target_new_device() = runTest {
        val runtimeRepository = mockRuntimeRepository()
        val selectedRepository = selectedRepository()
        val transport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_DEVICE_A, TEST_DEVICE_B)
        )
        val useCase = useCase(
            transport = transport,
            permissionPort = FakeBluetoothConnectPermissionPort(granted = true),
            runtimeRepository = runtimeRepository,
            selectedRepository = selectedRepository,
            clock = FakeClock(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase.refresh()
        useCase.selectDevice(TEST_DEVICE_A.address)
        useCase.connectSelected()
        advanceUntilIdle()

        transport.mutableConnectionState.value = BluetoothConnectionState.Connected(TEST_DEVICE_A)
        advanceUntilIdle()
        transport.mutableConnectionState.value = BluetoothConnectionState.Error(
            device = TEST_DEVICE_A,
            error = BluetoothConnectionError.STREAM_CLOSED
        )
        runCurrent()

        useCase.selectDevice(TEST_DEVICE_B.address)
        advanceUntilIdle()
        advanceTimeBy(1_000L)
        runCurrent()

        verify(runtimeRepository, times(1)).connect(TEST_DEVICE_A)
        assertEquals(TEST_DEVICE_B.address, selectedRepository.selectedDevice.value?.address)
        assertEquals(LxBluetoothReconnectState.Idle, useCase.state.value.reconnectState)

        useCase.connectSelected()
        advanceUntilIdle()
        verify(runtimeRepository).connect(TEST_DEVICE_B)
    }

    @Test
    fun reconnect_transitions_to_retries_exhausted_after_third_failed_retry() = runTest {
        val runtimeRepository = mockRuntimeRepository()
        val transport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_DEVICE_A)
        )
        val useCase = useCase(
            transport = transport,
            permissionPort = FakeBluetoothConnectPermissionPort(granted = true),
            runtimeRepository = runtimeRepository,
            selectedRepository = selectedRepository(),
            clock = FakeClock(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase.refresh()
        useCase.selectDevice(TEST_DEVICE_A.address)
        useCase.connectSelected()
        advanceUntilIdle()
        transport.mutableConnectionState.value = BluetoothConnectionState.Connected(TEST_DEVICE_A)
        advanceUntilIdle()

        transport.mutableConnectionState.value = BluetoothConnectionState.Error(
            device = TEST_DEVICE_A,
            error = BluetoothConnectionError.STREAM_CLOSED
        )
        runCurrent()

        advanceTimeBy(1_000L)
        runCurrent()
        transport.mutableConnectionState.value = BluetoothConnectionState.Connecting(TEST_DEVICE_A)
        runCurrent()
        transport.mutableConnectionState.value = BluetoothConnectionState.Error(
            device = TEST_DEVICE_A,
            error = BluetoothConnectionError.STREAM_CLOSED
        )
        runCurrent()
        transport.mutableConnectionState.value = BluetoothConnectionState.Disconnected
        runCurrent()

        advanceTimeBy(2_000L)
        runCurrent()
        transport.mutableConnectionState.value = BluetoothConnectionState.Connecting(TEST_DEVICE_A)
        runCurrent()
        transport.mutableConnectionState.value = BluetoothConnectionState.Error(
            device = TEST_DEVICE_A,
            error = BluetoothConnectionError.STREAM_CLOSED
        )
        runCurrent()
        transport.mutableConnectionState.value = BluetoothConnectionState.Disconnected
        runCurrent()

        advanceTimeBy(5_000L)
        runCurrent()
        transport.mutableConnectionState.value = BluetoothConnectionState.Connecting(TEST_DEVICE_A)
        runCurrent()
        transport.mutableConnectionState.value = BluetoothConnectionState.Error(
            device = TEST_DEVICE_A,
            error = BluetoothConnectionError.STREAM_CLOSED
        )
        runCurrent()

        verify(runtimeRepository, times(4)).connect(TEST_DEVICE_A)
        assertEquals(
            LxBluetoothReconnectState.Exhausted(attempts = 3),
            useCase.state.value.reconnectState
        )
        assertEquals(3, useCase.state.value.reconnectCount)
        assertEquals(
            LxBluetoothDisconnectReason.RETRIES_EXHAUSTED,
            useCase.state.value.lastDisconnectReason
        )
    }
}
