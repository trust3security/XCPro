package com.trust3.xcpro.simulator.condor

import android.content.Context
import android.content.SharedPreferences
import com.trust3.xcpro.simulator.CondorTransportKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface CondorTransportPreferencesStorage {
    fun readSelectedTransport(): CondorTransportKind

    fun writeSelectedTransport(value: CondorTransportKind)

    fun readTcpListenPort(): Int

    fun writeTcpListenPort(value: Int)
}

@Singleton
internal class CondorTransportPreferencesRepository private constructor(
    private val storage: CondorTransportPreferencesStorage
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        SharedPreferencesCondorTransportPreferencesStorage(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
    )

    internal constructor(
        storage: CondorTransportPreferencesStorage,
        unused: Unit = Unit
    ) : this(storage)

    private val mutableSelectedTransport =
        MutableStateFlow(storage.readSelectedTransport())
    private val mutableTcpListenPort =
        MutableStateFlow(storage.readTcpListenPort())

    val selectedTransport: StateFlow<CondorTransportKind> =
        mutableSelectedTransport.asStateFlow()

    val tcpListenPort: StateFlow<Int> = mutableTcpListenPort.asStateFlow()

    suspend fun setSelectedTransport(value: CondorTransportKind) {
        storage.writeSelectedTransport(value)
        mutableSelectedTransport.value = value
    }

    suspend fun setTcpListenPort(value: Int) {
        require(CondorTcpPortSpec.isValid(value)) { "TCP listen port out of range: $value" }
        storage.writeTcpListenPort(value)
        mutableTcpListenPort.value = value
    }

    private class SharedPreferencesCondorTransportPreferencesStorage(
        private val sharedPreferences: SharedPreferences
    ) : CondorTransportPreferencesStorage {

        override fun readSelectedTransport(): CondorTransportKind =
            sharedPreferences.getString(KEY_SELECTED_TRANSPORT, null)
                ?.let { stored ->
                    CondorTransportKind.entries.firstOrNull { it.name == stored }
                }
                ?: CondorTransportKind.BLUETOOTH

        override fun writeSelectedTransport(value: CondorTransportKind) {
            sharedPreferences.edit()
                .putString(KEY_SELECTED_TRANSPORT, value.name)
                .apply()
        }

        override fun readTcpListenPort(): Int {
            val storedPort = sharedPreferences.getInt(KEY_TCP_LISTEN_PORT, CondorTcpPortSpec.DEFAULT_PORT)
            return storedPort.takeIf(CondorTcpPortSpec::isValid) ?: CondorTcpPortSpec.DEFAULT_PORT
        }

        override fun writeTcpListenPort(value: Int) {
            sharedPreferences.edit()
                .putInt(KEY_TCP_LISTEN_PORT, value)
                .apply()
        }
    }

    private companion object {
        private const val PREFERENCES_NAME = "condor_bridge_settings"
        private const val KEY_SELECTED_TRANSPORT = "selected_transport"
        private const val KEY_TCP_LISTEN_PORT = "tcp_listen_port"
    }
}
