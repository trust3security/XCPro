package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.core.time.Clock
import java.io.IOException
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
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

@Singleton
internal class AndroidCondorTcpServer @Inject constructor(
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CondorTcpServerPort {
    private val sessionMutex = Mutex()
    private var activeSession: ActiveSession? = null

    private val mutableConnectionState =
        MutableStateFlow<CondorTcpServerState>(CondorTcpServerState.Disconnected)

    override val connectionState: StateFlow<CondorTcpServerState> = mutableConnectionState

    override fun open(port: Int): Flow<CondorReadChunk> = flow {
        require(CondorTcpPortSpec.isValid(port)) { "TCP listen port out of range: $port" }

        val session = tryStartSession(port) ?: return@flow
        var terminalError: CondorTcpServerState.Error? = null

        try {
            val serverSocket = ServerSocket()
            serverSocket.reuseAddress = true
            serverSocket.bind(InetSocketAddress(port))
            session.serverSocket = serverSocket
            updateStateIfActive(session, CondorTcpServerState.Listening(port))

            val clientSocket = serverSocket.accept()
            session.clientSocket = clientSocket
            session.connected.set(true)
            updateStateIfActive(
                session,
                CondorTcpServerState.Connected(
                    port = port,
                    remoteAddress = clientSocket.inetAddress?.hostAddress
                )
            )

            val buffer = ByteArray(READ_BUFFER_SIZE_BYTES)
            val inputStream = clientSocket.getInputStream()
            while (currentCoroutineContext().isActive) {
                val readCount = inputStream.read(buffer)
                if (readCount < 0) {
                    terminalError = CondorTcpServerState.Error(
                        port = port,
                        error = CondorTcpServerError.STREAM_CLOSED,
                        detail = null
                    )
                    break
                }
                if (readCount == 0) continue

                emit(
                    CondorReadChunk(
                        bytes = buffer.copyOf(readCount),
                        receivedMonoMs = clock.nowMonoMs()
                    )
                )
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (_: BindException) {
            terminalError = CondorTcpServerState.Error(
                port = port,
                error = CondorTcpServerError.BIND_FAILED,
                detail = "Could not listen on port $port. Check that the port is free on this device."
            )
        } catch (_: IOException) {
            terminalError = when {
                session.closeRequested.get() -> null
                session.connected.get() -> CondorTcpServerState.Error(
                    port = port,
                    error = CondorTcpServerError.READ_FAILED,
                    detail = "TCP connection failed while reading data."
                )

                else -> CondorTcpServerState.Error(
                    port = port,
                    error = CondorTcpServerError.ACCEPT_FAILED,
                    detail = "TCP listener stopped before a client connected."
                )
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
            mutableConnectionState.value = CondorTcpServerState.Disconnected
            current
        }

        closeSessionOnce(session)
    }

    private suspend fun tryStartSession(port: Int): ActiveSession? =
        sessionMutex.withLock {
            if (activeSession != null) return null

            ActiveSession(port).also { session ->
                activeSession = session
                mutableConnectionState.value = CondorTcpServerState.Listening(port)
            }
        }

    private suspend fun updateStateIfActive(
        session: ActiveSession,
        newState: CondorTcpServerState
    ) {
        sessionMutex.withLock {
            if (activeSession === session) {
                mutableConnectionState.value = newState
            }
        }
    }

    private suspend fun finishSession(
        session: ActiveSession,
        terminalError: CondorTcpServerState.Error?
    ) {
        closeSessionOnce(session)

        sessionMutex.withLock {
            if (activeSession !== session) return

            activeSession = null
            mutableConnectionState.value = when {
                session.closeRequested.get() -> CondorTcpServerState.Disconnected
                terminalError != null -> terminalError
                else -> CondorTcpServerState.Disconnected
            }
        }
    }

    private fun closeSessionOnce(session: ActiveSession) {
        if (session.closed.compareAndSet(false, true)) {
            runCatching { session.clientSocket?.close() }
            runCatching { session.serverSocket?.close() }
        }
    }

    private class ActiveSession(
        val port: Int
    ) {
        val closeRequested = AtomicBoolean(false)
        val connected = AtomicBoolean(false)
        val closed = AtomicBoolean(false)
        var serverSocket: ServerSocket? = null
        var clientSocket: Socket? = null
    }

    private companion object {
        const val READ_BUFFER_SIZE_BYTES: Int = 1024
    }
}
