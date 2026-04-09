package com.example.xcpro.variometer.bluetooth.lxnav.control

import com.example.xcpro.variometer.bluetooth.BluetoothConnectionError
import com.example.xcpro.variometer.bluetooth.BluetoothConnectionState
import com.example.xcpro.variometer.bluetooth.BluetoothReadChunk
import com.example.xcpro.variometer.bluetooth.BluetoothTransport
import com.example.xcpro.variometer.bluetooth.BondedBluetoothDevice
import com.example.xcpro.variometer.bluetooth.lxnav.runtime.LxExternalRuntimeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@OptIn(ExperimentalCoroutinesApi::class)
class LxBluetoothControlUseCaseTest {

    @Test
    fun permission_required_state_vs_granted_state() = runTest {
        val transport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_DEVICE_A)
        )
        val permissionPort = FakeBluetoothConnectPermissionPort(granted = false)
        val useCase = useCase(
            transport = transport,
            permissionPort = permissionPort,
            runtimeRepository = mock(),
            selectedRepository = selectedRepository(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase.refresh()
        advanceUntilIdle()

        assertTrue(useCase.state.value.permissionRequired)
        assertTrue(useCase.state.value.bondedDevices.isEmpty())

        permissionPort.granted = true
        useCase.onPermissionResult(true)
        advanceUntilIdle()

        assertFalse(useCase.state.value.permissionRequired)
        assertEquals(1, useCase.state.value.bondedDevices.size)
        assertEquals(TEST_DEVICE_A.address, useCase.state.value.bondedDevices.single().address)
    }

    @Test
    fun selected_device_resolution_and_unavailable_when_bond_disappears() = runTest {
        val selectedRepository = selectedRepository()
        val transport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_DEVICE_A)
        )
        val useCase = useCase(
            transport = transport,
            permissionPort = FakeBluetoothConnectPermissionPort(granted = true),
            runtimeRepository = mock(),
            selectedRepository = selectedRepository,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase.refresh()
        useCase.selectDevice(TEST_DEVICE_A.address)
        advanceUntilIdle()

        assertEquals(TEST_DEVICE_A.address, useCase.state.value.selectedDeviceAddress)
        assertEquals(TEST_DEVICE_A.displayName, useCase.state.value.selectedDeviceDisplayName)
        assertTrue(useCase.state.value.selectedDeviceAvailable)

        transport.bondedDevices = emptyList()
        useCase.refresh()
        advanceUntilIdle()

        assertEquals(TEST_DEVICE_A.address, useCase.state.value.selectedDeviceAddress)
        assertEquals(TEST_DEVICE_A.displayName, useCase.state.value.selectedDeviceDisplayName)
        assertFalse(useCase.state.value.selectedDeviceAvailable)
    }

    @Test
    fun connect_and_disconnect_delegate_to_runtime_repository() = runTest {
        val runtimeRepository = mock<LxExternalRuntimeRepository>()
        val transport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_DEVICE_A)
        )
        val useCase = useCase(
            transport = transport,
            permissionPort = FakeBluetoothConnectPermissionPort(granted = true),
            runtimeRepository = runtimeRepository,
            selectedRepository = selectedRepository(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase.refresh()
        useCase.selectDevice(TEST_DEVICE_A.address)
        advanceUntilIdle()

        useCase.connectSelected()
        advanceUntilIdle()

        verify(runtimeRepository).connect(TEST_DEVICE_A)

        transport.mutableConnectionState.value = BluetoothConnectionState.Connected(TEST_DEVICE_A)
        useCase.disconnect()
        advanceUntilIdle()

        verify(runtimeRepository).disconnect()
    }

    @Test
    fun connection_state_projection_uses_transport_state() = runTest {
        val transport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_DEVICE_A)
        )
        val useCase = useCase(
            transport = transport,
            permissionPort = FakeBluetoothConnectPermissionPort(granted = true),
            runtimeRepository = mock(),
            selectedRepository = selectedRepository(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase.refresh()
        advanceUntilIdle()
        transport.mutableConnectionState.value = BluetoothConnectionState.Connecting(TEST_DEVICE_A)
        advanceUntilIdle()

        assertEquals(TEST_DEVICE_A.address, useCase.state.value.activeDeviceAddress)
        assertEquals(TEST_DEVICE_A.displayName, useCase.state.value.activeDeviceName)
        assertEquals(BluetoothConnectionState.Connecting(TEST_DEVICE_A), useCase.state.value.connectionState)

        transport.mutableConnectionState.value = BluetoothConnectionState.Error(
            device = TEST_DEVICE_A,
            error = BluetoothConnectionError.CONNECT_FAILED
        )
        advanceUntilIdle()

        assertEquals(BluetoothConnectionError.CONNECT_FAILED, useCase.state.value.lastError)
    }

    @Test
    fun selection_persists_immediately_and_device_switch_does_not_auto_connect() = runTest {
        val runtimeRepository = mock<LxExternalRuntimeRepository>()
        val selectedRepository = selectedRepository()
        val transport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_DEVICE_A, TEST_DEVICE_B)
        )
        val useCase = useCase(
            transport = transport,
            permissionPort = FakeBluetoothConnectPermissionPort(granted = true),
            runtimeRepository = runtimeRepository,
            selectedRepository = selectedRepository,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase.refresh()
        useCase.selectDevice(TEST_DEVICE_B.address)
        advanceUntilIdle()

        assertEquals(TEST_DEVICE_B.address, selectedRepository.selectedDevice.value?.address)
        assertEquals(TEST_DEVICE_B.displayName, selectedRepository.selectedDevice.value?.displayNameSnapshot)
        verifyNoInteractions(runtimeRepository)
    }

    @Test
    fun same_device_reconnect_from_ui_remains_explicit() = runTest {
        val runtimeRepository = mock<LxExternalRuntimeRepository>()
        val transport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_DEVICE_A)
        )
        val useCase = useCase(
            transport = transport,
            permissionPort = FakeBluetoothConnectPermissionPort(granted = true),
            runtimeRepository = runtimeRepository,
            selectedRepository = selectedRepository(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase.refresh()
        useCase.selectDevice(TEST_DEVICE_A.address)
        advanceUntilIdle()

        useCase.connectSelected()
        advanceUntilIdle()
        transport.mutableConnectionState.value = BluetoothConnectionState.Connected(TEST_DEVICE_A)
        advanceUntilIdle()
        transport.mutableConnectionState.value = BluetoothConnectionState.Disconnected
        advanceUntilIdle()

        useCase.connectSelected()
        advanceUntilIdle()

        verify(runtimeRepository, times(2)).connect(TEST_DEVICE_A)
    }

    @Test
    fun connect_disconnect_commands_are_serialized_against_double_tap_races() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_DEVICE_A)
        )
        val runtimeRepository = mock<LxExternalRuntimeRepository>()
        val permissionPort = FakeBluetoothConnectPermissionPort(granted = true)
        val useCase = useCase(
            transport = transport,
            permissionPort = permissionPort,
            runtimeRepository = runtimeRepository,
            selectedRepository = selectedRepository(),
            dispatcher = dispatcher
        )

        useCase.refresh()
        useCase.selectDevice(TEST_DEVICE_A.address)
        advanceUntilIdle()

        transport.bondedReadGate = CompletableDeferred()

        val first = backgroundScope.launch { useCase.connectSelected() }
        runCurrent()
        val second = backgroundScope.launch { useCase.connectSelected() }
        runCurrent()

        assertEquals(1, transport.maxConcurrentBondedReads)

        transport.bondedReadGate?.complete(Unit)
        advanceUntilIdle()

        first.cancel()
        second.cancel()
    }

    private fun useCase(
        transport: FakeBluetoothTransport,
        permissionPort: FakeBluetoothConnectPermissionPort,
        runtimeRepository: LxExternalRuntimeRepository,
        selectedRepository: LxBluetoothSelectedDeviceRepository,
        dispatcher: CoroutineDispatcher
    ): LxBluetoothControlUseCase {
        return LxBluetoothControlUseCase(
            transport = transport,
            externalRuntimeRepository = runtimeRepository,
            permissionPort = permissionPort,
            selectedDeviceRepository = selectedRepository,
            dispatcher = dispatcher
        )
    }

    private fun selectedRepository(): LxBluetoothSelectedDeviceRepository =
        LxBluetoothSelectedDeviceRepository(FakeSelectedDeviceStorage(), Unit)

    private class FakeBluetoothTransport(
        bondedDevices: List<BondedBluetoothDevice>
    ) : BluetoothTransport {
        var bondedDevices: List<BondedBluetoothDevice> = bondedDevices
        var bondedReadGate: CompletableDeferred<Unit>? = null
        var maxConcurrentBondedReads: Int = 0
        private var inFlightBondedReads: Int = 0
        val mutableConnectionState =
            MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)

        override val connectionState: StateFlow<BluetoothConnectionState> = mutableConnectionState

        override suspend fun listBondedDevices(): List<BondedBluetoothDevice> {
            inFlightBondedReads += 1
            maxConcurrentBondedReads = maxOf(maxConcurrentBondedReads, inFlightBondedReads)
            try {
                bondedReadGate?.await()
                return bondedDevices
            } finally {
                inFlightBondedReads -= 1
            }
        }

        override fun open(device: BondedBluetoothDevice): Flow<BluetoothReadChunk> = flow { }

        override suspend fun close() {
            mutableConnectionState.value = BluetoothConnectionState.Disconnected
        }
    }

    private class FakeBluetoothConnectPermissionPort(
        var granted: Boolean
    ) : BluetoothConnectPermissionPort {
        override fun isGranted(): Boolean = granted
    }

    private class FakeSelectedDeviceStorage : LxBluetoothSelectedDeviceStorage {
        private var current: PersistedLxBluetoothDevice? = null

        override fun read(): PersistedLxBluetoothDevice? = current

        override fun write(value: PersistedLxBluetoothDevice) {
            current = value
        }

        override fun clear() {
            current = null
        }
    }

    private companion object {
        val TEST_DEVICE_A = BondedBluetoothDevice(
            address = "AA:BB",
            displayName = "LXNAV S100 A"
        )
        val TEST_DEVICE_B = BondedBluetoothDevice(
            address = "CC:DD",
            displayName = "LXNAV S100 B"
        )
    }
}
