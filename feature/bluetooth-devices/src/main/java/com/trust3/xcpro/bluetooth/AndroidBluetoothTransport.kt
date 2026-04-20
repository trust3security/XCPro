package com.trust3.xcpro.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.core.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AndroidBluetoothTransport internal constructor(
    private val platform: AndroidBluetoothPlatform,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BluetoothTransport {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        clock: Clock,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(
        platform = RealAndroidBluetoothPlatform(context),
        clock = clock,
        ioDispatcher = ioDispatcher
    )

    private val sessionMutex = Mutex()
    private var activeSession: ActiveSession? = null

    private val mutableConnectionState =
        MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)

    override val connectionState: StateFlow<BluetoothConnectionState> = mutableConnectionState

    override suspend fun listBondedDevices(): List<BondedBluetoothDevice> {
        if (!platform.hasConnectPermission()) return emptyList()
        if (!platform.isAdapterAvailable()) return emptyList()
        return platform.bondedDevices().map { device ->
            BondedBluetoothDevice(
                address = device.address,
                displayName = device.displayName
            )
        }
    }

    override fun open(device: BondedBluetoothDevice): Flow<BluetoothReadChunk> = flow {
        if (!platform.hasConnectPermission()) {
            publishPreSessionError(device, BluetoothConnectionError.PERMISSION_REQUIRED)
            return@flow
        }
        if (!platform.isAdapterAvailable()) {
            publishPreSessionError(device, BluetoothConnectionError.CONNECT_FAILED)
            return@flow
        }

        val bondedDevice = platform.bondedDevices().firstOrNull { it.address == device.address }
        if (bondedDevice == null) {
            publishPreSessionError(device, BluetoothConnectionError.DEVICE_NOT_BONDED)
            return@flow
        }

        val session = tryStartSession(device) ?: return@flow
        var terminalError: BluetoothConnectionError? = null

        try {
            val socket = platform.createSocket(
                deviceAddress = bondedDevice.address,
                uuid = SERIAL_PORT_PROFILE_UUID
            )
            if (socket == null) {
                terminalError = BluetoothConnectionError.CONNECT_FAILED
                return@flow
            }

            session.socket = socket
            socket.connect()
            session.connected.set(true)
            updateStateIfActive(session, BluetoothConnectionState.Connected(device))

            val buffer = ByteArray(READ_BUFFER_SIZE_BYTES)
            while (currentCoroutineContext().isActive) {
                val readCount = socket.read(buffer)
                if (readCount < 0) {
                    terminalError = BluetoothConnectionError.STREAM_CLOSED
                    break
                }
                if (readCount == 0) continue

                emit(
                    BluetoothReadChunk(
                        bytes = buffer.copyOf(readCount),
                        receivedMonoMs = clock.nowMonoMs()
                    )
                )
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (_: SecurityException) {
            terminalError = BluetoothConnectionError.PERMISSION_REQUIRED
        } catch (_: IOException) {
            terminalError = when {
                session.closeRequested.get() -> null
                session.connected.get() -> BluetoothConnectionError.READ_FAILED
                else -> BluetoothConnectionError.CONNECT_FAILED
            }
        } finally {
            finishSession(session, terminalError)
        }
    }.flowOn(ioDispatcher)

    override suspend fun close() {
        val session = sessionMutex.withLock {
            val current = activeSession ?: return
            current.closeRequested.set(true)
            activeSession = null
            mutableConnectionState.value = BluetoothConnectionState.Disconnected
            current
        }

        closeSocketOnce(session)
    }

    private suspend fun publishPreSessionError(
        device: BondedBluetoothDevice,
        error: BluetoothConnectionError
    ) {
        sessionMutex.withLock {
            if (activeSession == null) {
                mutableConnectionState.value = BluetoothConnectionState.Error(
                    device = device,
                    error = error
                )
            }
        }
    }

    private suspend fun tryStartSession(device: BondedBluetoothDevice): ActiveSession? =
        sessionMutex.withLock {
            if (activeSession != null) return null

            ActiveSession(device).also { session ->
                activeSession = session
                mutableConnectionState.value = BluetoothConnectionState.Connecting(device)
            }
        }

    private suspend fun updateStateIfActive(
        session: ActiveSession,
        newState: BluetoothConnectionState
    ) {
        sessionMutex.withLock {
            if (activeSession === session) {
                mutableConnectionState.value = newState
            }
        }
    }

    private suspend fun finishSession(
        session: ActiveSession,
        terminalError: BluetoothConnectionError?
    ) {
        closeSocketOnce(session)

        sessionMutex.withLock {
            if (activeSession !== session) return

            activeSession = null
            mutableConnectionState.value = when {
                session.closeRequested.get() -> BluetoothConnectionState.Disconnected
                terminalError != null -> BluetoothConnectionState.Error(
                    device = session.device,
                    error = terminalError
                )
                else -> BluetoothConnectionState.Disconnected
            }
        }
    }

    private fun closeSocketOnce(session: ActiveSession) {
        if (session.socketClosed.compareAndSet(false, true)) {
            runCatching { session.socket?.close() }
        }
    }

    private class ActiveSession(
        val device: BondedBluetoothDevice
    ) {
        val closeRequested = AtomicBoolean(false)
        val connected = AtomicBoolean(false)
        val socketClosed = AtomicBoolean(false)
        var socket: PlatformBluetoothSocket? = null
    }

    companion object {
        internal val SERIAL_PORT_PROFILE_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        internal const val READ_BUFFER_SIZE_BYTES: Int = 1024
    }
}

internal interface AndroidBluetoothPlatform {
    fun hasConnectPermission(): Boolean
    fun isAdapterAvailable(): Boolean
    fun bondedDevices(): List<PlatformBondedDevice>
    fun createSocket(deviceAddress: String, uuid: UUID): PlatformBluetoothSocket?
}

internal data class PlatformBondedDevice(
    val address: String,
    val displayName: String?
)

internal interface PlatformBluetoothSocket {
    @Throws(IOException::class)
    fun connect()

    @Throws(IOException::class)
    fun read(buffer: ByteArray): Int

    @Throws(IOException::class)
    fun close()
}

private class RealAndroidBluetoothPlatform(
    private val context: Context
) : AndroidBluetoothPlatform {

    override fun hasConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    override fun isAdapterAvailable(): Boolean = bluetoothManager()?.adapter != null

    @SuppressLint("MissingPermission")
    override fun bondedDevices(): List<PlatformBondedDevice> {
        val adapter = bluetoothManager()?.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices.orEmpty().map { device ->
                PlatformBondedDevice(
                    address = device.address,
                    displayName = device.name
                )
            }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    override fun createSocket(deviceAddress: String, uuid: UUID): PlatformBluetoothSocket? {
        val adapter = bluetoothManager()?.adapter ?: return null
        val device = runCatching {
            adapter.bondedDevices.orEmpty().firstOrNull { it.address == deviceAddress }
        }.getOrNull() ?: return null

        return runCatching {
            AndroidPlatformBluetoothSocket(
                socket = device.createRfcommSocketToServiceRecord(uuid)
            )
        }.getOrNull()
    }

    private fun bluetoothManager(): BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)
}

private class AndroidPlatformBluetoothSocket(
    private val socket: BluetoothSocket
) : PlatformBluetoothSocket {

    private val inputStream by lazy { socket.inputStream }

    override fun connect() {
        socket.connect()
    }

    override fun read(buffer: ByteArray): Int = inputStream.read(buffer)

    override fun close() {
        socket.close()
    }
}
