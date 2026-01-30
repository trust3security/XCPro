package com.example.xcpro.vario

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.xcpro.audio.VarioAudioSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "levo_vario_preferences"
private val Context.levoVarioDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)
private val KEY_MACCREADY = doublePreferencesKey("maccready_value")
private val KEY_MACCREADY_RISK = doublePreferencesKey("maccready_risk_value")
private val KEY_SHOW_WIND_SPEED_ON_VARIO = booleanPreferencesKey("show_wind_speed_on_vario")
private val KEY_AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
private val KEY_AUDIO_VOLUME = floatPreferencesKey("audio_volume")
private val KEY_AUDIO_LIFT_THRESHOLD = doublePreferencesKey("audio_lift_threshold")
private val KEY_AUDIO_SINK_SILENCE_THRESHOLD = doublePreferencesKey("audio_sink_silence_threshold")
private val KEY_AUDIO_DUTY_CYCLE = doublePreferencesKey("audio_duty_cycle")
private val KEY_AUDIO_DEADBAND_MIN = doublePreferencesKey("audio_deadband_min")
private val KEY_AUDIO_DEADBAND_MAX = doublePreferencesKey("audio_deadband_max")

data class LevoVarioConfig(
    val macCready: Double = 0.0,
    val macCreadyRisk: Double = 0.0,
    val showWindSpeedOnVario: Boolean = true,
    val audioSettings: VarioAudioSettings = VarioAudioSettings()
)

@Singleton
class LevoVarioPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val config: Flow<LevoVarioConfig> = context.levoVarioDataStore.data.map { prefs ->
        val mac = prefs[KEY_MACCREADY] ?: 0.0
        val audioSettings = readAudioSettings(prefs)
        LevoVarioConfig(
            macCready = mac,
            macCreadyRisk = prefs[KEY_MACCREADY_RISK] ?: mac,
            showWindSpeedOnVario = prefs[KEY_SHOW_WIND_SPEED_ON_VARIO] ?: true,
            audioSettings = audioSettings
        )
    }

    suspend fun setMacCready(value: Double) {
        context.levoVarioDataStore.edit { prefs ->
            prefs[KEY_MACCREADY] = value
        }
    }

    suspend fun setMacCreadyRisk(value: Double) {
        context.levoVarioDataStore.edit { prefs ->
            prefs[KEY_MACCREADY_RISK] = value
        }
    }

    suspend fun setShowWindSpeedOnVario(enabled: Boolean) {
        context.levoVarioDataStore.edit { prefs ->
            prefs[KEY_SHOW_WIND_SPEED_ON_VARIO] = enabled
        }
    }

    suspend fun updateAudioSettings(transform: (VarioAudioSettings) -> VarioAudioSettings) {
        context.levoVarioDataStore.edit { prefs ->
            val current = readAudioSettings(prefs)
            val updated = transform(current)
            prefs[KEY_AUDIO_ENABLED] = updated.enabled
            prefs[KEY_AUDIO_VOLUME] = updated.volume
            prefs[KEY_AUDIO_LIFT_THRESHOLD] = updated.liftThreshold
            prefs[KEY_AUDIO_SINK_SILENCE_THRESHOLD] = updated.sinkSilenceThreshold
            prefs[KEY_AUDIO_DUTY_CYCLE] = updated.dutyCycle
            prefs[KEY_AUDIO_DEADBAND_MIN] = updated.deadbandMin
            prefs[KEY_AUDIO_DEADBAND_MAX] = updated.deadbandMax
        }
    }

    private fun readAudioSettings(prefs: Preferences): VarioAudioSettings {
        val defaults = VarioAudioSettings()
        return VarioAudioSettings(
            enabled = prefs[KEY_AUDIO_ENABLED] ?: defaults.enabled,
            volume = prefs[KEY_AUDIO_VOLUME] ?: defaults.volume,
            liftThreshold = prefs[KEY_AUDIO_LIFT_THRESHOLD] ?: defaults.liftThreshold,
            sinkSilenceThreshold = prefs[KEY_AUDIO_SINK_SILENCE_THRESHOLD] ?: defaults.sinkSilenceThreshold,
            dutyCycle = prefs[KEY_AUDIO_DUTY_CYCLE] ?: defaults.dutyCycle,
            deadbandMin = prefs[KEY_AUDIO_DEADBAND_MIN] ?: defaults.deadbandMin,
            deadbandMax = prefs[KEY_AUDIO_DEADBAND_MAX] ?: defaults.deadbandMax
        )
    }
}
