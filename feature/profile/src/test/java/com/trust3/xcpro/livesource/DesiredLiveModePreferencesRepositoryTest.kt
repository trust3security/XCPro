package com.trust3.xcpro.livesource

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private val TEST_KEY_DESIRED_LIVE_MODE = stringPreferencesKey("desired_live_mode")

@OptIn(ExperimentalCoroutinesApi::class)
class DesiredLiveModePreferencesRepositoryTest {

    @Test
    fun desiredLiveMode_defaults_to_phone_only() = runTest {
        val repository = DesiredLiveModePreferencesRepository(
            dataStore = FakePreferencesDataStore(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        advanceUntilIdle()

        assertEquals(DesiredLiveMode.PHONE_ONLY, repository.desiredLiveMode.value)
    }

    @Test
    fun setDesiredLiveMode_persists_condor2_full() = runTest {
        val dataStore = FakePreferencesDataStore()
        val repository = DesiredLiveModePreferencesRepository(
            dataStore = dataStore,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        repository.setDesiredLiveMode(DesiredLiveMode.CONDOR2_FULL)
        advanceUntilIdle()

        assertEquals(DesiredLiveMode.CONDOR2_FULL, repository.desiredLiveMode.value)
        assertEquals(DesiredLiveMode.CONDOR2_FULL.name, dataStore.data.first()[TEST_KEY_DESIRED_LIVE_MODE])
    }

    @Test
    fun invalidStoredValue_falls_back_to_phone_only() = runTest {
        val repository = DesiredLiveModePreferencesRepository(
            dataStore = FakePreferencesDataStore(
                mutablePreferencesOf(TEST_KEY_DESIRED_LIVE_MODE to "BROKEN_MODE")
            ),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        advanceUntilIdle()

        assertEquals(DesiredLiveMode.PHONE_ONLY, repository.desiredLiveMode.value)
    }

    private class FakePreferencesDataStore(
        initialPreferences: Preferences = emptyPreferences()
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initialPreferences)

        override val data: Flow<Preferences> = flow {
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
