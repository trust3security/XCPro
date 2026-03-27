package com.example.xcpro.vario

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

private val TEST_KEY_AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
private val TEST_KEY_AUDIO_VOLUME = floatPreferencesKey("audio_volume")
private val TEST_KEY_AUDIO_LIFT_START_THRESHOLD = doublePreferencesKey("audio_lift_start_threshold")
private val TEST_KEY_AUDIO_SINK_START_THRESHOLD = doublePreferencesKey("audio_sink_start_threshold")
private val TEST_KEY_AUDIO_DUTY_CYCLE = doublePreferencesKey("audio_duty_cycle")
private val TEST_LEGACY_KEY_AUDIO_LIFT_THRESHOLD = doublePreferencesKey("audio_lift_threshold")
private val TEST_LEGACY_KEY_AUDIO_SINK_SILENCE_THRESHOLD = doublePreferencesKey("audio_sink_silence_threshold")
private val TEST_LEGACY_KEY_AUDIO_DEADBAND_MIN = doublePreferencesKey("audio_deadband_min")
private val TEST_LEGACY_KEY_AUDIO_DEADBAND_MAX = doublePreferencesKey("audio_deadband_max")

@OptIn(ExperimentalCoroutinesApi::class)
class LevoVarioPreferencesRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun config_reads_legacy_raw_audio_keys_as_canonical_thresholds() = runTest {
        val dataStore = FakePreferencesDataStore(
            mutablePreferencesOf(
                TEST_KEY_AUDIO_ENABLED to false,
                TEST_KEY_AUDIO_VOLUME to 0.33f,
                TEST_LEGACY_KEY_AUDIO_LIFT_THRESHOLD to 0.8,
                TEST_LEGACY_KEY_AUDIO_SINK_SILENCE_THRESHOLD to -0.5,
                TEST_KEY_AUDIO_DUTY_CYCLE to 0.55,
                TEST_LEGACY_KEY_AUDIO_DEADBAND_MIN to -1.5,
                TEST_LEGACY_KEY_AUDIO_DEADBAND_MAX to 0.2
            )
        )

        val repository = LevoVarioPreferencesRepository(dataStore)
        val audioSettings = repository.config.first().audioSettings

        assertEquals(false, audioSettings.enabled)
        assertEquals(0.33f, audioSettings.volume)
        assertEquals(0.8, audioSettings.liftStartThreshold, 0.0)
        assertEquals(-1.5, audioSettings.sinkStartThreshold, 0.0)
        assertEquals(0.55, audioSettings.dutyCycle, 0.0)
    }

    @Test
    fun update_audio_settings_writes_canonical_keys_and_removes_legacy_keys() = runTest {
        val dataStore = FakePreferencesDataStore(
            mutablePreferencesOf(
                TEST_LEGACY_KEY_AUDIO_LIFT_THRESHOLD to 0.8,
                TEST_LEGACY_KEY_AUDIO_SINK_SILENCE_THRESHOLD to -0.5,
                TEST_LEGACY_KEY_AUDIO_DEADBAND_MIN to -1.5,
                TEST_LEGACY_KEY_AUDIO_DEADBAND_MAX to 0.2
            )
        )

        val repository = LevoVarioPreferencesRepository(dataStore)
        repository.updateAudioSettings {
            it.copy(
                enabled = false,
                volume = 0.44f,
                liftStartThreshold = 0.6,
                sinkStartThreshold = -1.2,
                dutyCycle = 0.5
            )
        }

        val prefs = dataStore.data.first()
        assertEquals(0.6, prefs[TEST_KEY_AUDIO_LIFT_START_THRESHOLD]!!, 0.0)
        assertEquals(-1.2, prefs[TEST_KEY_AUDIO_SINK_START_THRESHOLD]!!, 0.0)
        assertEquals(0.5, prefs[TEST_KEY_AUDIO_DUTY_CYCLE]!!, 0.0)
        assertNull(prefs[TEST_LEGACY_KEY_AUDIO_LIFT_THRESHOLD])
        assertNull(prefs[TEST_LEGACY_KEY_AUDIO_SINK_SILENCE_THRESHOLD])
        assertNull(prefs[TEST_LEGACY_KEY_AUDIO_DEADBAND_MIN])
        assertNull(prefs[TEST_LEGACY_KEY_AUDIO_DEADBAND_MAX])
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
