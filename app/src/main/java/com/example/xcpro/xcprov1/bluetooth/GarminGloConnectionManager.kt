package com.example.xcpro.xcprov1.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.UUID

/**
 * Maintains a classic Bluetooth SPP connection to a Garmin GLO 2 receiver.
 *
 * The manager exposes a high-rate GPS fix stream parsed from the NMEA output.
 */
class GarminGloConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "GarminGloConnection"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        private const val READ_CHARSET = "US-ASCII"
        private const val MIN_FIX_INTERVAL_MS = 100L
    }

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val parser = GarminNmeaParser()

    private val _status = MutableStateFlow<GloStatus>(GloStatus.Idle)
    val status: StateFlow<GloStatus> = _status

    private val _fixFlow = MutableStateFlow<GloGpsFix?>(null)
    val fixFlow: StateFlow<GloGpsFix?> = _fixFlow

    private var connectJob: Job? = null
    private var readJob: Job? = null
    private var socket: BluetoothSocket? = null

    fun connectToAddress(address: String) {
        val adaptor = adapter ?: run {
            _status.value = GloStatus.Error("Bluetooth not available on this device")
            return
        }
        val device = adaptor.getRemoteDevice(address)
        connectToDevice(device)
    }

    fun connectToDevice(device: BluetoothDevice) {
        connectJob?.cancel()
        connectJob = scope.launch(Dispatchers.IO) {
            try {
                _status.value = GloStatus.Connecting(device.name ?: device.address)
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                adapter?.cancelDiscovery()
                socket.connect()
                this@GarminGloConnectionManager.socket = socket
                _status.value = GloStatus.Connected(device.name ?: device.address)
                startReader(socket)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to connect to Garmin GLO 2", ex)
                _status.value = GloStatus.Error("Failed to connect: ${ex.message}", ex)
                disconnect()
            }
        }
    }

    suspend fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        readJob?.cancel()
        readJob = null
        withContext(Dispatchers.IO) {
            try {
                socket?.close()
            } catch (ex: Exception) {
                Log.w(TAG, "Error closing GLO socket", ex)
            } finally {
                socket = null
            }
        }
        _status.value = GloStatus.Disconnected(reason = null)
    }

    fun stop() {
        scope.launch {
            disconnect()
        }
    }

    private fun startReader(socket: BluetoothSocket) {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(
                    InputStreamReader(socket.inputStream, Charset.forName(READ_CHARSET))
                )
                var line: String?
                while (true) {
                    line = reader.readLine() ?: break
                    val fix = parser.consume(line)
                    if (fix != null && parser.ageMillis() <= MIN_FIX_INTERVAL_MS * 5) {
                        _fixFlow.value = fix
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error reading NMEA stream", ex)
                _status.value = GloStatus.Error("Bluetooth read error: ${ex.message}", ex)
            } finally {
                withContext(Dispatchers.Main) {
                    _status.value = GloStatus.Disconnected(reason = "Stream closed")
                }
            }
        }
    }

    suspend fun awaitDisconnect() {
        readJob?.cancelAndJoin()
        disconnect()
    }
}
