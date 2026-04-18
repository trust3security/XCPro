package com.trust3.xcpro.variometer.bluetooth.lxnav.control

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PersistedLxBluetoothDevice(
    val address: String,
    val displayNameSnapshot: String?
)

internal interface LxBluetoothSelectedDeviceStorage {
    fun read(): PersistedLxBluetoothDevice?

    fun write(value: PersistedLxBluetoothDevice)

    fun clear()
}

@Singleton
internal class LxBluetoothSelectedDeviceRepository private constructor(
    private val storage: LxBluetoothSelectedDeviceStorage
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        SharedPreferencesLxBluetoothSelectedDeviceStorage(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
    )

    internal constructor(
        storage: LxBluetoothSelectedDeviceStorage,
        unused: Unit = Unit
    ) : this(storage)

    private val mutableSelectedDevice = MutableStateFlow(storage.read())

    val selectedDevice: StateFlow<PersistedLxBluetoothDevice?> =
        mutableSelectedDevice.asStateFlow()

    suspend fun setSelectedDevice(
        address: String,
        displayNameSnapshot: String?
    ) {
        val persisted = PersistedLxBluetoothDevice(
            address = address,
            displayNameSnapshot = displayNameSnapshot
        )
        storage.write(persisted)
        mutableSelectedDevice.value = persisted
    }

    suspend fun clearSelection() {
        storage.clear()
        mutableSelectedDevice.value = null
    }

    private class SharedPreferencesLxBluetoothSelectedDeviceStorage(
        private val sharedPreferences: SharedPreferences
    ) : LxBluetoothSelectedDeviceStorage {
        override fun read(): PersistedLxBluetoothDevice? {
            val address = sharedPreferences.getString(KEY_SELECTED_DEVICE_ADDRESS, null)
                ?: return null
            val displayNameSnapshot =
                sharedPreferences.getString(KEY_SELECTED_DEVICE_DISPLAY_NAME, null)
            return PersistedLxBluetoothDevice(
                address = address,
                displayNameSnapshot = displayNameSnapshot
            )
        }

        override fun write(value: PersistedLxBluetoothDevice) {
            sharedPreferences.edit()
                .putString(KEY_SELECTED_DEVICE_ADDRESS, value.address)
                .putString(KEY_SELECTED_DEVICE_DISPLAY_NAME, value.displayNameSnapshot)
                .apply()
        }

        override fun clear() {
            sharedPreferences.edit()
                .remove(KEY_SELECTED_DEVICE_ADDRESS)
                .remove(KEY_SELECTED_DEVICE_DISPLAY_NAME)
                .apply()
        }
    }

    private companion object {
        private const val PREFERENCES_NAME = "lx_bluetooth_settings"
        private const val KEY_SELECTED_DEVICE_ADDRESS = "selected_device_address"
        private const val KEY_SELECTED_DEVICE_DISPLAY_NAME = "selected_device_display_name"
    }
}
