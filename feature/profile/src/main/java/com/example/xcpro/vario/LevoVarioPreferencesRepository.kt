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
private val KEY_AUTO_MC_ENABLED = booleanPreferencesKey("auto_mc_enabled")
private val KEY_TE_COMPENSATION_ENABLED = booleanPreferencesKey("te_compensation_enabled")
private val KEY_SHOW_WIND_SPEED_ON_VARIO = booleanPreferencesKey("show_wind_speed_on_vario")
private val KEY_SHOW_HAWK_CARD = booleanPreferencesKey("show_hawk_card")
private val KEY_ENABLE_HAWK_UI = booleanPreferencesKey("enable_hawk_ui")
private val KEY_AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
private val KEY_AUDIO_VOLUME = floatPreferencesKey("audio_volume")
private val KEY_AUDIO_LIFT_THRESHOLD = doublePreferencesKey("audio_lift_threshold")
private val KEY_AUDIO_SINK_SILENCE_THRESHOLD = doublePreferencesKey("audio_sink_silence_threshold")
private val KEY_AUDIO_DUTY_CYCLE = doublePreferencesKey("audio_duty_cycle")
private val KEY_AUDIO_DEADBAND_MIN = doublePreferencesKey("audio_deadband_min")
private val KEY_AUDIO_DEADBAND_MAX = doublePreferencesKey("audio_deadband_max")
private val KEY_HAWK_NEEDLE_OMEGA_MIN_HZ = doublePreferencesKey("hawk_needle_omega_min_hz")
private val KEY_HAWK_NEEDLE_OMEGA_MAX_HZ = doublePreferencesKey("hawk_needle_omega_max_hz")
private val KEY_HAWK_NEEDLE_TARGET_TAU_SEC = doublePreferencesKey("hawk_needle_target_tau_sec")
private val KEY_HAWK_NEEDLE_DRIFT_TAU_MIN_SEC = doublePreferencesKey("hawk_needle_drift_tau_min_sec")
private val KEY_HAWK_NEEDLE_DRIFT_TAU_MAX_SEC = doublePreferencesKey("hawk_needle_drift_tau_max_sec")

data class LevoVarioConfig(
    val macCready: Double = 0.0,
    val macCreadyRisk: Double = 0.0,
    val autoMcEnabled: Boolean = true,
    val teCompensationEnabled: Boolean = true,
    val showWindSpeedOnVario: Boolean = true,
    val showHawkCard: Boolean = false,
    val enableHawkUi: Boolean = false,
    val audioSettings: VarioAudioSettings = VarioAudioSettings(),
    val hawkNeedleOmegaMinHz: Double = 0.9,
    val hawkNeedleOmegaMaxHz: Double = 2.0,
    val hawkNeedleTargetTauSec: Double = 0.8,
    val hawkNeedleDriftTauMinSec: Double = 3.5,
    val hawkNeedleDriftTauMaxSec: Double = 8.0
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
            autoMcEnabled = prefs[KEY_AUTO_MC_ENABLED] ?: true,
            teCompensationEnabled = prefs[KEY_TE_COMPENSATION_ENABLED] ?: true,
            showWindSpeedOnVario = prefs[KEY_SHOW_WIND_SPEED_ON_VARIO] ?: true,
            showHawkCard = prefs[KEY_SHOW_HAWK_CARD] ?: false,
            enableHawkUi = prefs[KEY_ENABLE_HAWK_UI] ?: false,
            audioSettings = audioSettings,
            hawkNeedleOmegaMinHz = prefs[KEY_HAWK_NEEDLE_OMEGA_MIN_HZ] ?: 0.9,
            hawkNeedleOmegaMaxHz = prefs[KEY_HAWK_NEEDLE_OMEGA_MAX_HZ] ?: 2.0,
            hawkNeedleTargetTauSec = prefs[KEY_HAWK_NEEDLE_TARGET_TAU_SEC] ?: 0.8,
            hawkNeedleDriftTauMinSec = prefs[KEY_HAWK_NEEDLE_DRIFT_TAU_MIN_SEC] ?: 3.5,
            hawkNeedleDriftTauMaxSec = prefs[KEY_HAWK_NEEDLE_DRIFT_TAU_MAX_SEC] ?: 8.0
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

    suspend fun setAutoMcEnabled(enabled: Boolean) {
        context.levoVarioDataStore.edit { prefs ->
            prefs[KEY_AUTO_MC_ENABLED] = enabled
        }
    }

    suspend fun setTeCompensationEnabled(enabled: Boolean) {
        context.levoVarioDataStore.edit { prefs ->
            prefs[KEY_TE_COMPENSATION_ENABLED] = enabled
        }
    }

    suspend fun setShowWindSpeedOnVario(enabled: Boolean) {
        context.levoVarioDataStore.edit { prefs ->
            prefs[KEY_SHOW_WIND_SPEED_ON_VARIO] = enabled
        }
    }

    suspend fun setShowHawkCard(enabled: Boolean) {
        context.levoVarioDataStore.edit { prefs ->
            prefs[KEY_SHOW_HAWK_CARD] = enabled
        }
    }

    suspend fun setEnableHawkUi(enabled: Boolean) {
        context.levoVarioDataStore.edit { prefs ->
            prefs[KEY_ENABLE_HAWK_UI] = enabled
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

    suspend fun setHawkNeedleOmegaMinHz(value: Double) {
        context.levoVarioDataStore.edit { prefs ->
            prefs[KEY_HAWK_NEEDLE_OMEGA_MIN_HZ] = value
        }
    }

    suspend fun setHawkNeedleOmegaMaxHz(value: Double) {
        context.levoVarioDataStore.edit { prefs ->
            prefs[KEY_HAWK_NEEDLE_OMEGA_MAX_HZ] = value
        }
    }

    suspend fun setHawkNeedleTargetTauSec(value: Double) {
        context.levoVarioDataStore.edit { prefs ->
            prefs[KEY_HAWK_NEEDLE_TARGET_TAU_SEC] = value
        }
    }

    suspend fun setHawkNeedleDriftTauMinSec(value: Double) {
        context.levoVarioDataStore.edit { prefs ->
            prefs[KEY_HAWK_NEEDLE_DRIFT_TAU_MIN_SEC] = value
        }
    }

    suspend fun setHawkNeedleDriftTauMaxSec(value: Double) {
        context.levoVarioDataStore.edit { prefs ->
            prefs[KEY_HAWK_NEEDLE_DRIFT_TAU_MAX_SEC] = value
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
