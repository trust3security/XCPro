package com.trust3.xcpro.vario

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.trust3.xcpro.audio.LEGACY_DEFAULT_DEADBAND_MAX
import com.trust3.xcpro.audio.LEGACY_DEFAULT_DEADBAND_MIN
import com.trust3.xcpro.audio.LEGACY_DEFAULT_LIFT_THRESHOLD
import com.trust3.xcpro.audio.LEGACY_DEFAULT_SINK_SILENCE_THRESHOLD
import com.trust3.xcpro.audio.VarioAudioSettings
import com.trust3.xcpro.audio.legacyEffectiveLiftStartThreshold
import com.trust3.xcpro.audio.legacyEffectiveSinkStartThreshold
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
private val KEY_AUDIO_LIFT_START_THRESHOLD = doublePreferencesKey("audio_lift_start_threshold")
private val KEY_AUDIO_SINK_START_THRESHOLD = doublePreferencesKey("audio_sink_start_threshold")
private val KEY_AUDIO_DUTY_CYCLE = doublePreferencesKey("audio_duty_cycle")
private val LEGACY_KEY_AUDIO_LIFT_THRESHOLD = doublePreferencesKey("audio_lift_threshold")
private val LEGACY_KEY_AUDIO_SINK_SILENCE_THRESHOLD = doublePreferencesKey("audio_sink_silence_threshold")
private val LEGACY_KEY_AUDIO_DEADBAND_MIN = doublePreferencesKey("audio_deadband_min")
private val LEGACY_KEY_AUDIO_DEADBAND_MAX = doublePreferencesKey("audio_deadband_max")
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
class LevoVarioPreferencesRepository private constructor(
    private val dataStore: DataStore<Preferences>
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.levoVarioDataStore)

    internal constructor(dataStore: DataStore<Preferences>, unused: Unit = Unit) : this(dataStore)

    val config: Flow<LevoVarioConfig> = dataStore.data.map { prefs ->
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
        dataStore.edit { prefs ->
            prefs[KEY_MACCREADY] = value
        }
    }

    suspend fun setMacCreadyRisk(value: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_MACCREADY_RISK] = value
        }
    }

    suspend fun setAutoMcEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_MC_ENABLED] = enabled
        }
    }

    suspend fun setTeCompensationEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_TE_COMPENSATION_ENABLED] = enabled
        }
    }

    suspend fun setShowWindSpeedOnVario(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_WIND_SPEED_ON_VARIO] = enabled
        }
    }

    suspend fun setShowHawkCard(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_HAWK_CARD] = enabled
        }
    }

    suspend fun setEnableHawkUi(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLE_HAWK_UI] = enabled
        }
    }

    suspend fun updateAudioSettings(transform: (VarioAudioSettings) -> VarioAudioSettings) {
        dataStore.edit { prefs ->
            val current = readAudioSettings(prefs)
            val updated = transform(current)
            prefs[KEY_AUDIO_ENABLED] = updated.enabled
            prefs[KEY_AUDIO_VOLUME] = updated.volume
            prefs[KEY_AUDIO_LIFT_START_THRESHOLD] = updated.liftStartThreshold
            prefs[KEY_AUDIO_SINK_START_THRESHOLD] = updated.sinkStartThreshold
            prefs[KEY_AUDIO_DUTY_CYCLE] = updated.dutyCycle
            prefs.remove(LEGACY_KEY_AUDIO_LIFT_THRESHOLD)
            prefs.remove(LEGACY_KEY_AUDIO_SINK_SILENCE_THRESHOLD)
            prefs.remove(LEGACY_KEY_AUDIO_DEADBAND_MIN)
            prefs.remove(LEGACY_KEY_AUDIO_DEADBAND_MAX)
        }
    }

    suspend fun setHawkNeedleOmegaMinHz(value: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_HAWK_NEEDLE_OMEGA_MIN_HZ] = value
        }
    }

    suspend fun setHawkNeedleOmegaMaxHz(value: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_HAWK_NEEDLE_OMEGA_MAX_HZ] = value
        }
    }

    suspend fun setHawkNeedleTargetTauSec(value: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_HAWK_NEEDLE_TARGET_TAU_SEC] = value
        }
    }

    suspend fun setHawkNeedleDriftTauMinSec(value: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_HAWK_NEEDLE_DRIFT_TAU_MIN_SEC] = value
        }
    }

    suspend fun setHawkNeedleDriftTauMaxSec(value: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_HAWK_NEEDLE_DRIFT_TAU_MAX_SEC] = value
        }
    }

    private fun readAudioSettings(prefs: Preferences): VarioAudioSettings {
        val defaults = VarioAudioSettings()
        val legacyDeadbandMin = prefs[LEGACY_KEY_AUDIO_DEADBAND_MIN] ?: LEGACY_DEFAULT_DEADBAND_MIN
        return VarioAudioSettings(
            enabled = prefs[KEY_AUDIO_ENABLED] ?: defaults.enabled,
            volume = prefs[KEY_AUDIO_VOLUME] ?: defaults.volume,
            liftStartThreshold = prefs[KEY_AUDIO_LIFT_START_THRESHOLD]
                ?: legacyEffectiveLiftStartThreshold(
                    liftThreshold = prefs[LEGACY_KEY_AUDIO_LIFT_THRESHOLD]
                        ?: LEGACY_DEFAULT_LIFT_THRESHOLD,
                    deadbandMin = legacyDeadbandMin,
                    deadbandMax = prefs[LEGACY_KEY_AUDIO_DEADBAND_MAX]
                        ?: LEGACY_DEFAULT_DEADBAND_MAX
                ),
            sinkStartThreshold = prefs[KEY_AUDIO_SINK_START_THRESHOLD]
                ?: legacyEffectiveSinkStartThreshold(
                    sinkSilenceThreshold = prefs[LEGACY_KEY_AUDIO_SINK_SILENCE_THRESHOLD]
                        ?: LEGACY_DEFAULT_SINK_SILENCE_THRESHOLD,
                    deadbandMin = legacyDeadbandMin
                ),
            dutyCycle = prefs[KEY_AUDIO_DUTY_CYCLE] ?: defaults.dutyCycle,
        )
    }
}
