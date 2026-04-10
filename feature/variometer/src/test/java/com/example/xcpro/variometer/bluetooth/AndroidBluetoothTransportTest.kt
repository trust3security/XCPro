package com.example.xcpro.variometer.bluetooth

import com.example.xcpro.core.time.FakeClock
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidBluetoothTransportTest {

    private lateinit var ioDispatcher: ExecutorCoroutineDispatcher
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        ioDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        clock = FakeClock()
    }

    @After
    fun tearDown() {
        ioDispatcher.close()
    }

    @Test
    fun bonded_device_enumeration_maps_address_and_name() = runBlocking {
        val platform = FakeAndroidBluetoothPlatform(
            bondedDevices = listOf(
                PlatformBondedDevice(
                    address = TEST_ADDRESS,
                    displayName = TEST_NAME
                )
            )
        )
        val transport = transport(platform)

        val devices = transport.listBondedDevices()

        assertEquals(
            listOf(BondedBluetoothDevice(TEST_ADDRESS, TEST_NAME)),
            devices
        )
    }

    @Test
    fun enumeration_returns_empty_when_permission_missing() = runBlocking {
        val platform = FakeAndroidBluetoothPlatform(
            hasConnectPermission = false,
            bondedDevices = listOf(PlatformBondedDevice(TEST_ADDRESS, TEST_NAME))
        )
        val transport = transport(platform)

        val devices = transport.listBondedDevices()

        assertTrue(devices.isEmpty())
    }

    @Test
    fun open_with_missing_permission_sets_permission_required() = runBlocking {
        val platform = FakeAndroidBluetoothPlatform(hasConnectPermission = false)
        val transport = transport(platform)

        val chunks = collectOpen(transport, TEST_DEVICE)

        assertTrue(chunks.isEmpty())
        assertEquals(
            BluetoothConnectionState.Error(
                device = TEST_DEVICE,
                error = BluetoothConnectionError.PERMISSION_REQUIRED
            ),
            transport.connectionState.value
        )
    }

    @Test
    fun open_with_non_bonded_device_sets_device_not_bonded() = runBlocking {
        val platform = FakeAndroidBluetoothPlatform(
            bondedDevices = listOf(PlatformBondedDevice("11:22", "Other"))
        )
        val transport = transport(platform)

        val chunks = collectOpen(transport, TEST_DEVICE)

        assertTrue(chunks.isEmpty())
        assertEquals(
            BluetoothConnectionState.Error(
                device = TEST_DEVICE,
                error = BluetoothConnectionError.DEVICE_NOT_BONDED
            ),
            transport.connectionState.value
        )
    }

    @Test
    fun successful_connect_transitions_disconnected_to_connecting_to_connected() = runBlocking {
        val allowConnect = CountDownLatch(1)
        val socket = ScriptedSocket(
            connectLatch = allowConnect,
            readActions = ArrayDeque(listOf(ReadAction.BlockUntilClosed))
        )
        val platform = FakeAndroidBluetoothPlatform(
            bondedDevices = listOf(PlatformBondedDevice(TEST_ADDRESS, TEST_NAME)),
            socketFactory = { _, _ -> socket }
        )
        val transport = transport(platform)
        val readJob = launch {
            transport.open(TEST_DEVICE).collect { }
        }

        assertEquals(BluetoothConnectionState.Disconnected, transport.connectionState.value)
        waitFor { transport.connectionState.value == BluetoothConnectionState.Connecting(TEST_DEVICE) }
        allowConnect.countDown()
        waitFor { transport.connectionState.value == BluetoothConnectionState.Connected(TEST_DEVICE) }
        assertEquals(
            BluetoothConnectionState.Connected(TEST_DEVICE),
            transport.connectionState.value
        )

        transport.close()
        readJob.join()
    }

    @Test
    fun successful_read_emits_chunk_with_clock_timestamp() = runBlocking {
        clock.setMonoMs(1_234L)
        val socket = ScriptedSocket(
            readActions = ArrayDeque(
                listOf(
                    ReadAction.Bytes(byteArrayOf(1, 2, 3)),
                    ReadAction.Eof
                )
            )
        )
        val platform = FakeAndroidBluetoothPlatform(
            bondedDevices = listOf(PlatformBondedDevice(TEST_ADDRESS, TEST_NAME)),
            socketFactory = { _, _ -> socket }
        )
        val transport = transport(platform)

        val chunks = collectOpen(transport, TEST_DEVICE)

        assertEquals(1, chunks.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), chunks.single().bytes)
        assertEquals(1_234L, chunks.single().receivedMonoMs)
    }

    @Test
    fun eof_maps_to_stream_closed() = runBlocking {
        val socket = ScriptedSocket(readActions = ArrayDeque(listOf(ReadAction.Eof)))
        val platform = FakeAndroidBluetoothPlatform(
            bondedDevices = listOf(PlatformBondedDevice(TEST_ADDRESS, TEST_NAME)),
            socketFactory = { _, _ -> socket }
        )
        val transport = transport(platform)

        val chunks = collectOpen(transport, TEST_DEVICE)

        assertTrue(chunks.isEmpty())
        assertEquals(
            BluetoothConnectionState.Error(
                device = TEST_DEVICE,
                error = BluetoothConnectionError.STREAM_CLOSED
            ),
            transport.connectionState.value
        )
    }

    @Test
    fun connect_failure_maps_to_connect_failed() = runBlocking {
        val socket = ScriptedSocket(connectFailure = IOException("connect failed"))
        val platform = FakeAndroidBluetoothPlatform(
            bondedDevices = listOf(PlatformBondedDevice(TEST_ADDRESS, TEST_NAME)),
            socketFactory = { _, _ -> socket }
        )
        val transport = transport(platform)

        val chunks = collectOpen(transport, TEST_DEVICE)

        assertTrue(chunks.isEmpty())
        assertEquals(
            BluetoothConnectionState.Error(
                device = TEST_DEVICE,
                error = BluetoothConnectionError.CONNECT_FAILED
            ),
            transport.connectionState.value
        )
    }

    @Test
    fun read_failure_maps_to_read_failed() = runBlocking {
        val socket = ScriptedSocket(
            readActions = ArrayDeque(
                listOf(ReadAction.Throw(IOException("read failed")))
            )
        )
        val platform = FakeAndroidBluetoothPlatform(
            bondedDevices = listOf(PlatformBondedDevice(TEST_ADDRESS, TEST_NAME)),
            socketFactory = { _, _ -> socket }
        )
        val transport = transport(platform)

        val chunks = collectOpen(transport, TEST_DEVICE)

        assertTrue(chunks.isEmpty())
        assertEquals(
            BluetoothConnectionState.Error(
                device = TEST_DEVICE,
                error = BluetoothConnectionError.READ_FAILED
            ),
            transport.connectionState.value
        )
    }

    @Test
    fun explicit_close_closes_socket_once_clears_session_and_sets_disconnected() = runBlocking {
        val socket = ScriptedSocket(readActions = ArrayDeque(listOf(ReadAction.BlockUntilClosed)))
        val platform = FakeAndroidBluetoothPlatform(
            bondedDevices = listOf(PlatformBondedDevice(TEST_ADDRESS, TEST_NAME)),
            socketFactory = { _, _ -> socket }
        )
        val transport = transport(platform)

        val readJob = launch {
            transport.open(TEST_DEVICE).collect { }
        }
        waitFor { transport.connectionState.value == BluetoothConnectionState.Connected(TEST_DEVICE) }

        transport.close()

        waitFor { transport.connectionState.value == BluetoothConnectionState.Disconnected }
        readJob.join()

        assertEquals(1, socket.closeCount.get())
        assertEquals(BluetoothConnectionState.Disconnected, transport.connectionState.value)
    }

    @Test
    fun second_open_is_rejected_without_corrupting_active_session_state() = runBlocking {
        val activeSocket = ScriptedSocket(readActions = ArrayDeque(listOf(ReadAction.BlockUntilClosed)))
        val platform = FakeAndroidBluetoothPlatform(
            bondedDevices = listOf(PlatformBondedDevice(TEST_ADDRESS, TEST_NAME)),
            socketFactory = { _, _ -> activeSocket }
        )
        val transport = transport(platform)

        val firstJob = launch {
            transport.open(TEST_DEVICE).collect { }
        }
        waitFor { transport.connectionState.value == BluetoothConnectionState.Connected(TEST_DEVICE) }

        val secondChunks = collectOpen(transport, TEST_DEVICE)

        assertTrue(secondChunks.isEmpty())
        assertEquals(
            BluetoothConnectionState.Connected(TEST_DEVICE),
            transport.connectionState.value
        )

        transport.close()
        firstJob.join()
    }

    @Test
    fun no_adapter_available_maps_open_to_connect_failed() = runBlocking {
        val platform = FakeAndroidBluetoothPlatform(
            isAdapterAvailable = false,
            bondedDevices = listOf(PlatformBondedDevice(TEST_ADDRESS, TEST_NAME))
        )
        val transport = transport(platform)

        val chunks = collectOpen(transport, TEST_DEVICE)

        assertTrue(chunks.isEmpty())
        assertEquals(
            BluetoothConnectionState.Error(
                device = TEST_DEVICE,
                error = BluetoothConnectionError.CONNECT_FAILED
            ),
            transport.connectionState.value
        )
    }

    private fun transport(platform: FakeAndroidBluetoothPlatform): AndroidBluetoothTransport =
        AndroidBluetoothTransport(
            platform = platform,
            clock = clock,
            ioDispatcher = ioDispatcher
        )

    private suspend fun collectOpen(
        transport: AndroidBluetoothTransport,
        device: BondedBluetoothDevice
    ): List<BluetoothReadChunk> {
        val chunks = mutableListOf<BluetoothReadChunk>()
        transport.open(device).collect { chunk ->
            chunks += chunk
        }
        return chunks
    }

    private suspend fun waitFor(predicate: () -> Boolean) {
        withTimeout(2_000L) {
            while (!predicate()) {
                delay(10L)
            }
        }
    }

    private class FakeAndroidBluetoothPlatform(
        private val hasConnectPermission: Boolean = true,
        private val isAdapterAvailable: Boolean = true,
        private val bondedDevices: List<PlatformBondedDevice> = emptyList(),
        private val socketFactory: (String, UUID) -> PlatformBluetoothSocket? = { _, _ -> null }
    ) : AndroidBluetoothPlatform {

        override fun hasConnectPermission(): Boolean = hasConnectPermission

        override fun isAdapterAvailable(): Boolean = isAdapterAvailable

        override fun bondedDevices(): List<PlatformBondedDevice> =
            if (isAdapterAvailable) bondedDevices else emptyList()

        override fun createSocket(deviceAddress: String, uuid: UUID): PlatformBluetoothSocket? =
            if (isAdapterAvailable) socketFactory(deviceAddress, uuid) else null
    }

    private sealed interface ReadAction {
        data class Bytes(val bytes: ByteArray) : ReadAction
        data object Eof : ReadAction
        data class Throw(val error: IOException) : ReadAction
        data object BlockUntilClosed : ReadAction
    }

    private class ScriptedSocket(
        private val connectFailure: IOException? = null,
        private val connectLatch: CountDownLatch? = null,
        readActions: ArrayDeque<ReadAction> = ArrayDeque()
    ) : PlatformBluetoothSocket {

        private val scriptedReads = readActions
        private val closedLatch = CountDownLatch(1)
        val closeCount = AtomicInteger(0)

        override fun connect() {
            connectFailure?.let { throw it }
            connectLatch?.await(2, TimeUnit.SECONDS)
        }

        override fun read(buffer: ByteArray): Int {
            val action = synchronized(scriptedReads) {
                if (scriptedReads.isEmpty()) {
                    ReadAction.Eof
                } else {
                    scriptedReads.removeFirst()
                }
            }

            return when (action) {
                is ReadAction.Bytes -> {
                    action.bytes.copyInto(buffer, endIndex = action.bytes.size)
                    action.bytes.size
                }

                ReadAction.Eof -> -1

                is ReadAction.Throw -> throw action.error

                ReadAction.BlockUntilClosed -> {
                    closedLatch.await(15, TimeUnit.SECONDS)
                    throw IOException("socket closed")
                }
            }
        }

        override fun close() {
            if (closeCount.compareAndSet(0, 1)) {
                closedLatch.countDown()
            }
        }
    }

    companion object {
        private const val TEST_ADDRESS = "AA:BB:CC:DD:EE:FF"
        private const val TEST_NAME = "LXNAV S100"
        private val TEST_DEVICE = BondedBluetoothDevice(TEST_ADDRESS, TEST_NAME)
    }
}
