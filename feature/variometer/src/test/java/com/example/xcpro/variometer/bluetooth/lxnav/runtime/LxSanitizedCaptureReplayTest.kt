package com.example.xcpro.variometer.bluetooth.lxnav.runtime

import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.variometer.bluetooth.BluetoothConnectionState
import com.example.xcpro.variometer.bluetooth.BluetoothConnectionError
import com.example.xcpro.variometer.bluetooth.BluetoothTransport
import com.example.xcpro.variometer.bluetooth.BondedBluetoothDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LxSanitizedCaptureReplayTest {

    @Test
    fun steady_stream_fixture_replays_through_runtime_and_updates_diagnostics() = runTest {
        val fixture = LxSanitizedCaptureFixtureLoader.load("lxnav_s100_steady_stream.fixture")
        val repository = LxExternalRuntimeRepository(
            transport = FixtureReplayBluetoothTransport(fixture),
            clock = FakeClock(monoMs = 10L),
            externalAirspeedWritePort = TestExternalAirspeedWritePort(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        repository.connect(TEST_DEVICE)
        advanceUntilIdle()

        assertEquals(setOf("LXWP1", "LXWP0", "PLXVF", "PGRMZ"), fixture.sentenceInventory)
        assertEquals(654.1, repository.runtimeSnapshot.value.pressureAltitudeM?.value ?: Double.NaN, 1e-6)
        assertEquals(1.12, repository.runtimeSnapshot.value.totalEnergyVarioMps?.value ?: Double.NaN, 1e-6)
        assertEquals(2, repository.runtimeSnapshot.value.diagnostics.acceptedSentenceCount)
        assertEquals(3, repository.runtimeSnapshot.value.diagnostics.rejectedSentenceCount)
        assertEquals(1, repository.runtimeSnapshot.value.diagnostics.checksumFailureCount)
        assertEquals(1, repository.runtimeSnapshot.value.diagnostics.parseFailureCount)
        assertEquals(5.0, repository.runtimeSnapshot.value.diagnostics.rollingSentenceRatePerSecond, 1e-6)
        assertEquals(654.1, repository.externalFlightSnapshot.value.pressureAltitudeM?.value ?: Double.NaN, 1e-6)
        assertEquals(1.12, repository.externalFlightSnapshot.value.totalEnergyVarioMps?.value ?: Double.NaN, 1e-6)

        repository.disconnect()
        advanceUntilIdle()
    }

    @Test
    fun disconnect_reconnect_fixture_replays_across_two_sessions_without_widening_parser_scope() = runTest {
        val fixture = LxSanitizedCaptureFixtureLoader.load("lxnav_s100_disconnect_reconnect.fixture")
        val repository = LxExternalRuntimeRepository(
            transport = FixtureReplayBluetoothTransport(fixture),
            clock = FakeClock(monoMs = 25L),
            externalAirspeedWritePort = TestExternalAirspeedWritePort(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        repository.connect(TEST_DEVICE)
        advanceUntilIdle()

        assertEquals(
            BluetoothConnectionState.Error(TEST_DEVICE, BluetoothConnectionError.STREAM_CLOSED),
            repository.runtimeSnapshot.value.connectionState
        )
        assertNull(repository.runtimeSnapshot.value.pressureAltitudeM)

        repository.connect(TEST_DEVICE)
        advanceUntilIdle()

        assertEquals(2L, repository.runtimeSnapshot.value.sessionOrdinal)
        assertEquals(710.0, repository.runtimeSnapshot.value.pressureAltitudeM?.value ?: Double.NaN, 1e-6)
        assertEquals(1.6, repository.runtimeSnapshot.value.totalEnergyVarioMps?.value ?: Double.NaN, 1e-6)
        assertEquals(1, repository.runtimeSnapshot.value.diagnostics.acceptedSentenceCount)
        assertEquals(0, repository.runtimeSnapshot.value.diagnostics.rejectedSentenceCount)
        assertNull(repository.runtimeSnapshot.value.diagnostics.lastTransportError)

        repository.disconnect()
        advanceUntilIdle()
    }

    private class FixtureReplayBluetoothTransport(
        fixture: LxSanitizedCaptureFixture
    ) : BluetoothTransport {
        private val sessions = ArrayDeque(fixture.sessions)
        private var activeCloseSignal: CompletableDeferred<Unit>? = null
        private val mutableConnectionState =
            MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)

        override val connectionState: StateFlow<BluetoothConnectionState> = mutableConnectionState

        override suspend fun listBondedDevices(): List<BondedBluetoothDevice> = listOf(TEST_DEVICE)

        override fun open(device: BondedBluetoothDevice): Flow<com.example.xcpro.variometer.bluetooth.BluetoothReadChunk> = flow {
            val session = sessions.removeFirst()
            val closeSignal = CompletableDeferred<Unit>()
            activeCloseSignal = closeSignal
            mutableConnectionState.value = BluetoothConnectionState.Connecting(device)
            mutableConnectionState.value = BluetoothConnectionState.Connected(device)
            try {
                session.chunks.forEach { emit(it) }
                val terminalError = session.terminalError
                if (terminalError != null) {
                    mutableConnectionState.value = BluetoothConnectionState.Error(device, terminalError)
                } else {
                    closeSignal.await()
                }
            } finally {
                if (activeCloseSignal === closeSignal) {
                    activeCloseSignal = null
                }
            }
        }

        override suspend fun close() {
            activeCloseSignal?.complete(Unit)
            mutableConnectionState.value = BluetoothConnectionState.Disconnected
        }
    }

    private companion object {
        val TEST_DEVICE = BondedBluetoothDevice(
            address = "AA:BB:CC:DD:EE:FF",
            displayName = "LXNAV S100"
        )
    }
}
