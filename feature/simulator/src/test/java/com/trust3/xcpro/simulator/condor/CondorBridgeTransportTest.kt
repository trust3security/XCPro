package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.bluetooth.BluetoothConnectionError
import com.trust3.xcpro.bluetooth.BluetoothConnectionState
import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.simulator.CondorReconnectState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CondorBridgeTransportTest {

    @Test
    fun reconnect_transitions_waiting_attempting_and_exhausted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock()
        val bluetoothTransport = FakeBluetoothTransport(
            bondedDevices = listOf(TEST_CONDOR_BRIDGE_A.toBondedDevice())
        )
        val liveSampleRepository = CondorLiveSampleRepository(
            parser = CondorSentenceParser(),
            clock = clock
        )
        val transport = CondorBridgeTransport(
            bluetoothTransport = bluetoothTransport,
            clock = clock,
            liveSampleRepository = liveSampleRepository,
            dispatcher = dispatcher
        )
        try {
            transport.connect(TEST_CONDOR_BRIDGE_A)
            runCurrent()

            bluetoothTransport.mutableConnectionState.value =
                BluetoothConnectionState.Connected(TEST_CONDOR_BRIDGE_A.toBondedDevice())
            runCurrent()

            bluetoothTransport.mutableConnectionState.value =
                BluetoothConnectionState.Error(
                    device = TEST_CONDOR_BRIDGE_A.toBondedDevice(),
                    error = BluetoothConnectionError.READ_FAILED
                )
            runCurrent()
            assertEquals(CondorReconnectState.WAITING, transport.state.value.reconnect)

            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(CondorReconnectState.ATTEMPTING, transport.state.value.reconnect)

            bluetoothTransport.mutableConnectionState.value =
                BluetoothConnectionState.Connecting(TEST_CONDOR_BRIDGE_A.toBondedDevice())
            runCurrent()
            bluetoothTransport.mutableConnectionState.value =
                BluetoothConnectionState.Error(
                    device = TEST_CONDOR_BRIDGE_A.toBondedDevice(),
                    error = BluetoothConnectionError.READ_FAILED
                )
            runCurrent()
            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(CondorReconnectState.ATTEMPTING, transport.state.value.reconnect)

            bluetoothTransport.mutableConnectionState.value =
                BluetoothConnectionState.Connecting(TEST_CONDOR_BRIDGE_A.toBondedDevice())
            runCurrent()
            bluetoothTransport.mutableConnectionState.value =
                BluetoothConnectionState.Error(
                    device = TEST_CONDOR_BRIDGE_A.toBondedDevice(),
                    error = BluetoothConnectionError.READ_FAILED
                )
            runCurrent()
            advanceTimeBy(5_000L)
            runCurrent()
            assertEquals(CondorReconnectState.ATTEMPTING, transport.state.value.reconnect)

            bluetoothTransport.mutableConnectionState.value =
                BluetoothConnectionState.Connecting(TEST_CONDOR_BRIDGE_A.toBondedDevice())
            runCurrent()
            bluetoothTransport.mutableConnectionState.value =
                BluetoothConnectionState.Error(
                    device = TEST_CONDOR_BRIDGE_A.toBondedDevice(),
                    error = BluetoothConnectionError.READ_FAILED
                )
            runCurrent()
            assertEquals(CondorReconnectState.EXHAUSTED, transport.state.value.reconnect)
        } finally {
            transport.shutdown()
        }
    }
}
