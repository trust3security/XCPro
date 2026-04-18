package com.trust3.xcpro.thermalling

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ThermallingModePreferencesRepositoryTest {

    @Test
    fun settingsFlow_defaultsWhenNoStoredValues() = runTest {
        val repository = ThermallingModePreferencesRepository(FakePreferencesDataStore())

        val settings = repository.settingsFlow.first()

        assertEquals(ThermallingModeSettings(), settings)
    }

    @Test
    fun setters_clampAndPersistValues() = runTest {
        val repository = ThermallingModePreferencesRepository(FakePreferencesDataStore())

        repository.setEnabled(true)
        repository.setEnterDelaySeconds(999)
        repository.setExitDelaySeconds(-11)
        repository.setThermalZoomLevel(99f)

        val settings = repository.settingsFlow.first()
        assertTrue(settings.enabled)
        assertEquals(THERMALLING_DELAY_MAX_SECONDS, settings.enterDelaySeconds)
        assertEquals(THERMALLING_DELAY_MIN_SECONDS, settings.exitDelaySeconds)
        assertEquals(THERMALLING_ZOOM_LEVEL_MAX, settings.thermalZoomLevel)
    }

    @Test
    fun ioReadFailure_emitsDefaultsAndLaterReadsRecover() = runTest {
        val dataStore = FakePreferencesDataStore(firstReadException = IOException("io error"))
        val repository = ThermallingModePreferencesRepository(dataStore)

        val initial = repository.settingsFlow.first()
        assertEquals(ThermallingModeSettings(), initial)

        repository.setEnabled(true)

        val recovered = repository.settingsFlow.first()
        assertTrue(recovered.enabled)
    }

    @Test
    fun nonIoReadFailure_isRethrown() = runTest {
        val repository = ThermallingModePreferencesRepository(
            FakePreferencesDataStore(firstReadException = IllegalStateException("bad state"))
        )

        try {
            repository.settingsFlow.first()
            fail("Expected non-IO exception to propagate")
        } catch (expected: IllegalStateException) {
            assertEquals("bad state", expected.message)
        }
    }

    private class FakePreferencesDataStore(
        initialPreferences: Preferences = emptyPreferences(),
        private var firstReadException: Throwable? = null
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initialPreferences)

        override val data: Flow<Preferences> = flow {
            val exception = firstReadException
            if (exception != null) {
                firstReadException = null
                throw exception
            }
            emitAll(state)
        }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
