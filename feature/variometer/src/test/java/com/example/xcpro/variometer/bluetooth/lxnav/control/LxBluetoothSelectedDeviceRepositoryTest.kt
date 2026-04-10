package com.example.xcpro.variometer.bluetooth.lxnav.control

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LxBluetoothSelectedDeviceRepositoryTest {

    @Test
    fun persist_and_load_selected_device_address() = runTest {
        val storage = FakeSelectedDeviceStorage()
        val repository = LxBluetoothSelectedDeviceRepository(storage, Unit)

        repository.setSelectedDevice("AA:BB", null)

        assertEquals("AA:BB", repository.selectedDevice.value?.address)
        val reloaded = LxBluetoothSelectedDeviceRepository(storage, Unit)
        assertEquals("AA:BB", reloaded.selectedDevice.value?.address)
    }

    @Test
    fun persist_and_load_display_name_snapshot() = runTest {
        val storage = FakeSelectedDeviceStorage()
        val repository = LxBluetoothSelectedDeviceRepository(storage, Unit)

        repository.setSelectedDevice("AA:BB", "LXNAV S100")

        assertEquals("LXNAV S100", repository.selectedDevice.value?.displayNameSnapshot)
        val reloaded = LxBluetoothSelectedDeviceRepository(storage, Unit)
        assertEquals("LXNAV S100", reloaded.selectedDevice.value?.displayNameSnapshot)
    }

    @Test
    fun update_semantics_replace_existing_selection() = runTest {
        val storage = FakeSelectedDeviceStorage()
        val repository = LxBluetoothSelectedDeviceRepository(storage, Unit)

        repository.setSelectedDevice("AA:BB", "Device A")
        repository.setSelectedDevice("CC:DD", "Device B")

        assertEquals("CC:DD", repository.selectedDevice.value?.address)
        assertEquals("Device B", repository.selectedDevice.value?.displayNameSnapshot)
    }

    @Test
    fun clear_semantics_remove_persisted_selection() = runTest {
        val storage = FakeSelectedDeviceStorage()
        val repository = LxBluetoothSelectedDeviceRepository(storage, Unit)
        repository.setSelectedDevice("AA:BB", "Device A")

        repository.clearSelection()

        assertNull(repository.selectedDevice.value)
        val reloaded = LxBluetoothSelectedDeviceRepository(storage, Unit)
        assertNull(reloaded.selectedDevice.value)
    }

    private class FakeSelectedDeviceStorage : LxBluetoothSelectedDeviceStorage {
        private var current: PersistedLxBluetoothDevice? = null

        override fun read(): PersistedLxBluetoothDevice? = current

        override fun write(value: PersistedLxBluetoothDevice) {
            current = value
        }

        override fun clear() {
            current = null
        }
    }
}
