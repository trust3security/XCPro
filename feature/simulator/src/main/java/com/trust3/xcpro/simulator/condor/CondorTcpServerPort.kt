package com.trust3.xcpro.simulator.condor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal enum class CondorTcpServerError {
    BIND_FAILED,
    ACCEPT_FAILED,
    READ_FAILED,
    STREAM_CLOSED
}

internal sealed interface CondorTcpServerState {
    data object Disconnected : CondorTcpServerState

    data class Listening(
        val port: Int
    ) : CondorTcpServerState

    data class Connected(
        val port: Int,
        val remoteAddress: String?
    ) : CondorTcpServerState

    data class Error(
        val port: Int,
        val error: CondorTcpServerError,
        val detail: String?
    ) : CondorTcpServerState
}

internal interface CondorTcpServerPort {
    val connectionState: StateFlow<CondorTcpServerState>

    fun open(port: Int): Flow<CondorReadChunk>

    suspend fun close()
}
