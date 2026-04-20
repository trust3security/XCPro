package com.trust3.xcpro.variometer.bluetooth.lxnav.runtime

import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.external.ExternalInstrumentFlightSnapshot
import com.trust3.xcpro.bluetooth.BluetoothConnectionError
import com.trust3.xcpro.bluetooth.BluetoothConnectionState
import com.trust3.xcpro.bluetooth.BluetoothReadChunk
import com.trust3.xcpro.bluetooth.BluetoothTransport
import com.trust3.xcpro.bluetooth.BondedBluetoothDevice
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LxExternalRuntimeRepositoryTest {

    @Test
    fun diagnostics_update_from_chunks_outcomes_and_reset_on_disconnect() = runTest {
        val clock = FakeClock(monoMs = 10L)
        val transport = FakeBluetoothTransport()
        val repository = repository(
            transport = transport,
            clock = clock,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        transport.enqueue(
            SessionScript.AwaitClose(
                chunks = listOf(
                    chunk("\$LXWP1,S100,SN123,1.2.3,2.0", 100L),
                    chunk("\$PLXVF,1,2,3", 200L),
                    chunk("\$PGRMZ,1,2,3", 300L),
                    chunk("\$LXWP0,LOGGER,120.0,1234.0,2.5*00", 400L)
                )
            )
        )

        repository.connect(TEST_DEVICE_A)
        advanceUntilIdle()

        val diagnostics = repository.runtimeSnapshot.value.diagnostics
        assertEquals(10L, diagnostics.sessionStartMonoMs)
        assertEquals(400L, diagnostics.lastReceivedMonoMs)
        assertEquals(1, diagnostics.acceptedSentenceCount)
        assertEquals(3, diagnostics.rejectedSentenceCount)
        assertEquals(1, diagnostics.checksumFailureCount)
        assertEquals(1, diagnostics.parseFailureCount)
        assertEquals(4.0, diagnostics.rollingSentenceRatePerSecond, 1e-6)

        repository.disconnect()
        advanceUntilIdle()

        val cleared = repository.runtimeSnapshot.value.diagnostics
        assertNull(cleared.sessionStartMonoMs)
        assertNull(cleared.lastReceivedMonoMs)
        assertEquals(0, cleared.acceptedSentenceCount)
        assertEquals(0, cleared.rejectedSentenceCount)
        assertEquals(0, cleared.checksumFailureCount)
        assertEquals(0, cleared.parseFailureCount)
        assertEquals(0.0, cleared.rollingSentenceRatePerSecond, 0.0)
    }

    @Test
    fun accepted_lxwp0_updates_timed_fields_with_per_field_timestamps_from_outcomes() = runTest {
        val transport = FakeBluetoothTransport()
        val repository = repository(
            transport = transport,
            clock = FakeClock(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        transport.enqueue(
            SessionScript.AwaitClose(
                chunks = listOf(
                    chunk("\$LXWP0,LOGGER,120.0,1234.0,2.5", 100L),
                    chunk("\$LXWP0,LOGGER,,,1.5", 250L)
                )
            )
        )

        repository.connect(TEST_DEVICE_A)
        advanceUntilIdle()

        val snapshot = repository.runtimeSnapshot.value
        assertEquals(120.0, snapshot.airspeedKph?.value ?: Double.NaN, 1e-6)
        assertEquals(100L, snapshot.airspeedKph?.receivedMonoMs ?: -1L)
        assertEquals(1234.0, snapshot.pressureAltitudeM?.value ?: Double.NaN, 1e-6)
        assertEquals(100L, snapshot.pressureAltitudeM?.receivedMonoMs ?: -1L)
        assertEquals(1.5, snapshot.totalEnergyVarioMps?.value ?: Double.NaN, 1e-6)
        assertEquals(250L, snapshot.totalEnergyVarioMps?.receivedMonoMs ?: -1L)
        assertEquals(250L, snapshot.lastAcceptedMonoMs ?: -1L)

        repository.disconnect()
        advanceUntilIdle()
    }

    @Test
    fun accepted_lxwp1_updates_device_metadata_only() = runTest {
        val transport = FakeBluetoothTransport()
        val repository = repository(
            transport = transport,
            clock = FakeClock(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        transport.enqueue(
            SessionScript.AwaitClose(
                chunks = listOf(chunk("\$LXWP1,S100,SN123,1.2.3,2.0", 300L))
            )
        )

        repository.connect(TEST_DEVICE_A)
        advanceUntilIdle()

        val snapshot = repository.runtimeSnapshot.value
        assertNotNull(snapshot.deviceInfo)
        assertEquals("S100", snapshot.deviceInfo?.product)
        assertEquals("SN123", snapshot.deviceInfo?.serial)
        assertNull(snapshot.pressureAltitudeM)
        assertNull(snapshot.totalEnergyVarioMps)
        assertNull(snapshot.airspeedKph)
        assertEquals(300L, snapshot.lastAcceptedMonoMs ?: -1L)

        repository.disconnect()
        advanceUntilIdle()
    }

    @Test
    fun unsupported_unknown_and_rejected_outcomes_do_not_mutate_runtime_state() = runTest {
        val transport = FakeBluetoothTransport()
        val repository = repository(
            transport = transport,
            clock = FakeClock(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        transport.enqueue(
            SessionScript.AwaitClose(
                chunks = listOf(
                    chunk("\$PLXVF,1,2,3", 100L),
                    chunk("\$GPRMC,1,2,3", 200L),
                    chunk("\$LXWP0,LOGGER,120.0,1234.0,2.5*00", 300L)
                )
            )
        )

        repository.connect(TEST_DEVICE_A)
        advanceUntilIdle()

        assertEquals(1L, repository.runtimeSnapshot.value.sessionOrdinal)
        assertNull(repository.runtimeSnapshot.value.pressureAltitudeM)
        assertNull(repository.runtimeSnapshot.value.totalEnergyVarioMps)
        assertNull(repository.runtimeSnapshot.value.airspeedKph)
        assertNull(repository.runtimeSnapshot.value.deviceInfo)
        assertNull(repository.runtimeSnapshot.value.lastAcceptedMonoMs)
        assertEquals(ExternalInstrumentFlightSnapshot(), repository.externalFlightSnapshot.value)

        repository.disconnect()
        advanceUntilIdle()
    }

    @Test
    fun external_flight_snapshot_exposes_only_pressure_altitude_and_total_energy_vario() = runTest {
        val transport = FakeBluetoothTransport()
        val externalAirspeedWritePort = TestExternalAirspeedWritePort()
        val repository = repository(
            transport = transport,
            clock = FakeClock(),
            externalAirspeedWritePort = externalAirspeedWritePort,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        transport.enqueue(
            SessionScript.AwaitClose(
                chunks = listOf(
                    chunk("\$LXWP0,LOGGER,118.0,1300.0,3.0", 500L),
                    chunk("\$LXWP1,S100,SN123,1.2.3,2.0", 600L)
                )
            )
        )

        repository.connect(TEST_DEVICE_A)
        advanceUntilIdle()

        val runtimeSnapshot = repository.runtimeSnapshot.value
        val externalSnapshot = repository.externalFlightSnapshot.value
        assertEquals(118.0, runtimeSnapshot.airspeedKph?.value ?: Double.NaN, 1e-6)
        assertNotNull(runtimeSnapshot.deviceInfo)
        assertEquals(1300.0, externalSnapshot.pressureAltitudeM?.value ?: Double.NaN, 1e-6)
        assertEquals(500L, externalSnapshot.pressureAltitudeM?.receivedMonoMs ?: -1L)
        assertEquals(3.0, externalSnapshot.totalEnergyVarioMps?.value ?: Double.NaN, 1e-6)
        assertEquals(500L, externalSnapshot.totalEnergyVarioMps?.receivedMonoMs ?: -1L)
        assertEquals(118.0 / 3.6, externalAirspeedWritePort.latestSample?.trueMs ?: Double.NaN, 1e-6)
        assertTrue(externalAirspeedWritePort.latestSample?.indicatedMs?.isNaN() == true)
        assertEquals(500L, externalAirspeedWritePort.latestSample?.clockMillis ?: -1L)

        repository.disconnect()
        advanceUntilIdle()
    }

    @Test
    fun explicit_disconnect_clears_runtime_fields_and_narrow_read_port_snapshot() = runTest {
        val transport = FakeBluetoothTransport()
        val externalAirspeedWritePort = TestExternalAirspeedWritePort()
        val repository = repository(
            transport = transport,
            clock = FakeClock(),
            externalAirspeedWritePort = externalAirspeedWritePort,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        transport.enqueue(
            SessionScript.AwaitClose(
                chunks = listOf(
                    chunk("\$LXWP0,LOGGER,115.0,1250.0,2.0", 100L),
                    chunk("\$LXWP1,S100,SN123,1.2.3,2.0", 120L)
                )
            )
        )

        repository.connect(TEST_DEVICE_A)
        advanceUntilIdle()
        assertNotNull(repository.runtimeSnapshot.value.pressureAltitudeM)
        assertNotNull(externalAirspeedWritePort.latestSample)

        repository.disconnect()
        advanceUntilIdle()

        assertEquals(BluetoothConnectionState.Disconnected, repository.runtimeSnapshot.value.connectionState)
        assertNull(repository.runtimeSnapshot.value.activeDeviceAddress)
        assertNull(repository.runtimeSnapshot.value.pressureAltitudeM)
        assertNull(repository.runtimeSnapshot.value.totalEnergyVarioMps)
        assertNull(repository.runtimeSnapshot.value.airspeedKph)
        assertNull(repository.runtimeSnapshot.value.deviceInfo)
        assertNull(repository.runtimeSnapshot.value.lastAcceptedMonoMs)
        assertEquals(ExternalInstrumentFlightSnapshot(), repository.externalFlightSnapshot.value)
        assertNull(externalAirspeedWritePort.latestSample)
    }

    @Test
    fun eof_clears_runtime_fields_and_narrow_read_port_snapshot() = runTest {
        val transport = FakeBluetoothTransport()
        val repository = repository(
            transport = transport,
            clock = FakeClock(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        transport.enqueue(
            SessionScript.EmitThenTerminalState(
                chunks = listOf(chunk("\$LXWP0,LOGGER,115.0,1250.0,2.0", 100L)),
                error = BluetoothConnectionError.STREAM_CLOSED
            )
        )

        repository.connect(TEST_DEVICE_A)
        advanceUntilIdle()

        assertEquals(
            BluetoothConnectionState.Error(TEST_DEVICE_A, BluetoothConnectionError.STREAM_CLOSED),
            repository.runtimeSnapshot.value.connectionState
        )
        assertNull(repository.runtimeSnapshot.value.pressureAltitudeM)
        assertEquals(
            BluetoothConnectionError.STREAM_CLOSED,
            repository.runtimeSnapshot.value.diagnostics.lastTransportError
        )
        assertEquals(ExternalInstrumentFlightSnapshot(), repository.externalFlightSnapshot.value)
    }

    @Test
    fun transport_error_clears_runtime_fields_and_narrow_read_port_snapshot() = runTest {
        val transport = FakeBluetoothTransport()
        val repository = repository(
            transport = transport,
            clock = FakeClock(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        transport.enqueue(
            SessionScript.EmitThenTerminalState(
                chunks = listOf(chunk("\$LXWP0,LOGGER,115.0,1250.0,2.0", 100L)),
                error = BluetoothConnectionError.READ_FAILED
            )
        )

        repository.connect(TEST_DEVICE_A)
        advanceUntilIdle()

        assertEquals(
            BluetoothConnectionState.Error(TEST_DEVICE_A, BluetoothConnectionError.READ_FAILED),
            repository.runtimeSnapshot.value.connectionState
        )
        assertNull(repository.runtimeSnapshot.value.pressureAltitudeM)
        assertEquals(
            BluetoothConnectionError.READ_FAILED,
            repository.runtimeSnapshot.value.diagnostics.lastTransportError
        )
        assertEquals(ExternalInstrumentFlightSnapshot(), repository.externalFlightSnapshot.value)
    }

    @Test
    fun same_device_reconnect_starts_empty_repopulates_and_increments_session_ordinal() = runTest {
        val transport = FakeBluetoothTransport()
        val repository = repository(
            transport = transport,
            clock = FakeClock(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        transport.enqueue(
            SessionScript.EmitThenTerminalState(
                chunks = listOf(chunk("\$LXWP0,LOGGER,120.0,1234.0,2.5", 100L)),
                error = BluetoothConnectionError.STREAM_CLOSED
            )
        )
        val secondGate = CompletableDeferred<Unit>()
        transport.enqueue(
            SessionScript.AwaitGateThenAwaitClose(
                gate = secondGate,
                chunks = listOf(chunk("\$LXWP0,LOGGER,121.0,1240.0,2.7", 200L))
            )
        )

        repository.connect(TEST_DEVICE_A)
        advanceUntilIdle()
        assertEquals(1L, repository.runtimeSnapshot.value.sessionOrdinal)
        assertNull(repository.runtimeSnapshot.value.pressureAltitudeM)

        repository.connect(TEST_DEVICE_A)
        advanceUntilIdle()
        assertEquals(2L, repository.runtimeSnapshot.value.sessionOrdinal)
        assertNull(repository.runtimeSnapshot.value.pressureAltitudeM)
        assertEquals(TEST_DEVICE_A.address, repository.runtimeSnapshot.value.activeDeviceAddress)

        secondGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1240.0, repository.runtimeSnapshot.value.pressureAltitudeM?.value ?: Double.NaN, 1e-6)

        repository.disconnect()
        advanceUntilIdle()
    }

    @Test
    fun different_device_reconnect_clears_prior_metadata_and_measurements_before_repopulating() = runTest {
        val transport = FakeBluetoothTransport()
        val repository = repository(
            transport = transport,
            clock = FakeClock(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        transport.enqueue(
            SessionScript.EmitThenTerminalState(
                chunks = listOf(
                    chunk("\$LXWP0,LOGGER,120.0,1234.0,2.5", 100L),
                    chunk("\$LXWP1,S100,SN123,1.2.3,2.0", 120L)
                ),
                error = BluetoothConnectionError.STREAM_CLOSED
            )
        )
        val secondGate = CompletableDeferred<Unit>()
        transport.enqueue(
            SessionScript.AwaitGateThenAwaitClose(
                gate = secondGate,
                chunks = listOf(chunk("\$LXWP0,LOGGER,110.0,1100.0,1.5", 300L))
            )
        )

        repository.connect(TEST_DEVICE_A)
        advanceUntilIdle()

        repository.connect(TEST_DEVICE_B)
        advanceUntilIdle()

        assertEquals(2L, repository.runtimeSnapshot.value.sessionOrdinal)
        assertEquals(TEST_DEVICE_B.address, repository.runtimeSnapshot.value.activeDeviceAddress)
        assertNull(repository.runtimeSnapshot.value.pressureAltitudeM)
        assertNull(repository.runtimeSnapshot.value.deviceInfo)

        secondGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1100.0, repository.runtimeSnapshot.value.pressureAltitudeM?.value ?: Double.NaN, 1e-6)

        repository.disconnect()
        advanceUntilIdle()
    }

    private fun repository(
        transport: FakeBluetoothTransport,
        clock: FakeClock,
        externalAirspeedWritePort: TestExternalAirspeedWritePort = TestExternalAirspeedWritePort(),
        dispatcher: CoroutineDispatcher
    ): LxExternalRuntimeRepository {
        return LxExternalRuntimeRepository(
            transport = transport,
            clock = clock,
            externalAirspeedWritePort = externalAirspeedWritePort,
            dispatcher = dispatcher
        )
    }

    private fun chunk(text: String, receivedMonoMs: Long): BluetoothReadChunk =
        BluetoothReadChunk(
            bytes = "$text\n".toByteArray(StandardCharsets.US_ASCII),
            receivedMonoMs = receivedMonoMs
        )

    private class FakeBluetoothTransport : BluetoothTransport {
        private val sessionScripts = ArrayDeque<SessionScript>()
        private var activeCloseSignal: CompletableDeferred<Unit>? = null

        private val mutableConnectionState =
            MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)

        override val connectionState: StateFlow<BluetoothConnectionState> = mutableConnectionState

        fun enqueue(script: SessionScript) {
            sessionScripts.addLast(script)
        }

        override suspend fun listBondedDevices(): List<BondedBluetoothDevice> = emptyList()

        override fun open(device: BondedBluetoothDevice): Flow<BluetoothReadChunk> = flow {
            val script = sessionScripts.removeFirst()
            val closeSignal = CompletableDeferred<Unit>()
            activeCloseSignal = closeSignal
            mutableConnectionState.value = BluetoothConnectionState.Connecting(device)
            mutableConnectionState.value = BluetoothConnectionState.Connected(device)
            try {
                script.run(
                    device = device,
                    collector = this,
                    closeSignal = closeSignal,
                    mutableConnectionState = mutableConnectionState
                )
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

    private sealed interface SessionScript {
        suspend fun run(
            device: BondedBluetoothDevice,
            collector: FlowCollector<BluetoothReadChunk>,
            closeSignal: CompletableDeferred<Unit>,
            mutableConnectionState: MutableStateFlow<BluetoothConnectionState>
        )

        data class AwaitClose(
            val chunks: List<BluetoothReadChunk>
        ) : SessionScript {
            override suspend fun run(
                device: BondedBluetoothDevice,
                collector: FlowCollector<BluetoothReadChunk>,
                closeSignal: CompletableDeferred<Unit>,
                mutableConnectionState: MutableStateFlow<BluetoothConnectionState>
            ) {
                chunks.forEach { collector.emit(it) }
                closeSignal.await()
            }
        }

        data class AwaitGateThenAwaitClose(
            val gate: CompletableDeferred<Unit>,
            val chunks: List<BluetoothReadChunk>
        ) : SessionScript {
            override suspend fun run(
                device: BondedBluetoothDevice,
                collector: FlowCollector<BluetoothReadChunk>,
                closeSignal: CompletableDeferred<Unit>,
                mutableConnectionState: MutableStateFlow<BluetoothConnectionState>
            ) {
                gate.await()
                chunks.forEach { collector.emit(it) }
                closeSignal.await()
            }
        }

        data class EmitThenTerminalState(
            val chunks: List<BluetoothReadChunk>,
            val error: BluetoothConnectionError
        ) : SessionScript {
            override suspend fun run(
                device: BondedBluetoothDevice,
                collector: FlowCollector<BluetoothReadChunk>,
                closeSignal: CompletableDeferred<Unit>,
                mutableConnectionState: MutableStateFlow<BluetoothConnectionState>
            ) {
                chunks.forEach { collector.emit(it) }
                mutableConnectionState.value = BluetoothConnectionState.Error(device, error)
            }
        }
    }

    private companion object {
        val TEST_DEVICE_A = BondedBluetoothDevice("AA:BB:CC:DD:EE:01", "LXNAV S100 A")
        val TEST_DEVICE_B = BondedBluetoothDevice("AA:BB:CC:DD:EE:02", "LXNAV S100 B")
    }
}

