// Role: Persist trail settings and expose a flow for map state hydration.
// Invariants: Stored values are always valid enums; defaults apply on parse failure.
package com.trust3.xcpro.map.trail

import android.content.Context
import android.content.SharedPreferences
import com.trust3.xcpro.core.common.profiles.ProfileSettingsProfileIds
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * SharedPreferences-backed storage for trail settings.
 */
@Singleton
class MapTrailPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val activeProfileId = MutableStateFlow(DEFAULT_PROFILE_ID)

    private val preferenceChanges: Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null) return@OnSharedPreferenceChangeListener
            if (!key.contains(KEY_LENGTH) &&
                !key.contains(KEY_TYPE) &&
                !key.contains(KEY_WIND_DRIFT) &&
                !key.contains(KEY_SCALING)
            ) return@OnSharedPreferenceChangeListener
            trySend(Unit)
        }
        trySend(Unit)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val settingsFlow: Flow<TrailSettings> = combine(
        activeProfileId,
        preferenceChanges
    ) { profileId, _ ->
        readSettings(profileId)
    }.distinctUntilChanged()

    fun setActiveProfileId(profileId: String) {
        val resolved = ProfileSettingsProfileIds.canonicalOrDefault(profileId)
        if (activeProfileId.value != resolved) {
            activeProfileId.value = resolved
        }
    }

    fun getSettings(): TrailSettings = readSettings(activeProfileId.value)

    fun readProfileSettings(profileId: String): TrailSettings =
        readSettings(ProfileSettingsProfileIds.canonicalOrDefault(profileId))

    fun setTrailLength(length: TrailLength) {
        val profileId = activeProfileId.value
        prefs.edit()
            .putString(scopedKey(profileId, KEY_LENGTH), length.name)
            .also { editor ->
                if (isLegacyFallbackEligible(profileId)) {
                    editor.putString(KEY_LENGTH, length.name)
                }
            }
            .apply()
    }

    fun setTrailType(type: TrailType) {
        val profileId = activeProfileId.value
        prefs.edit()
            .putString(scopedKey(profileId, KEY_TYPE), type.name)
            .also { editor ->
                if (isLegacyFallbackEligible(profileId)) {
                    editor.putString(KEY_TYPE, type.name)
                }
            }
            .apply()
    }

    fun setWindDriftEnabled(enabled: Boolean) {
        val profileId = activeProfileId.value
        prefs.edit()
            .putBoolean(scopedKey(profileId, KEY_WIND_DRIFT), enabled)
            .also { editor ->
                if (isLegacyFallbackEligible(profileId)) {
                    editor.putBoolean(KEY_WIND_DRIFT, enabled)
                }
            }
            .apply()
    }

    fun setScalingEnabled(enabled: Boolean) {
        val profileId = activeProfileId.value
        prefs.edit()
            .putBoolean(scopedKey(profileId, KEY_SCALING), enabled)
            .also { editor ->
                if (isLegacyFallbackEligible(profileId)) {
                    editor.putBoolean(KEY_SCALING, enabled)
                }
            }
            .apply()
    }

    fun setSettings(settings: TrailSettings) {
        writeProfileSettings(activeProfileId.value, settings)
    }

    fun writeProfileSettings(profileId: String, settings: TrailSettings) {
        val resolvedProfileId = ProfileSettingsProfileIds.canonicalOrDefault(profileId)
        prefs.edit()
            .putString(scopedKey(resolvedProfileId, KEY_LENGTH), settings.length.name)
            .putString(scopedKey(resolvedProfileId, KEY_TYPE), settings.type.name)
            .putBoolean(scopedKey(resolvedProfileId, KEY_WIND_DRIFT), settings.windDriftEnabled)
            .putBoolean(scopedKey(resolvedProfileId, KEY_SCALING), settings.scalingEnabled)
            .also { editor ->
                if (isLegacyFallbackEligible(resolvedProfileId)) {
                    editor.putString(KEY_LENGTH, settings.length.name)
                    editor.putString(KEY_TYPE, settings.type.name)
                    editor.putBoolean(KEY_WIND_DRIFT, settings.windDriftEnabled)
                    editor.putBoolean(KEY_SCALING, settings.scalingEnabled)
                }
            }
            .apply()
    }

    fun resetToDefaults() {
        writeProfileSettings(activeProfileId.value, TrailSettings())
    }

    fun clearProfile(profileId: String) {
        val resolvedProfileId = ProfileSettingsProfileIds.canonicalOrDefault(profileId)
        prefs.edit()
            .remove(scopedKey(resolvedProfileId, KEY_LENGTH))
            .remove(scopedKey(resolvedProfileId, KEY_TYPE))
            .remove(scopedKey(resolvedProfileId, KEY_WIND_DRIFT))
            .remove(scopedKey(resolvedProfileId, KEY_SCALING))
            .also { editor ->
                if (isLegacyFallbackEligible(resolvedProfileId)) {
                    editor.remove(KEY_LENGTH)
                    editor.remove(KEY_TYPE)
                    editor.remove(KEY_WIND_DRIFT)
                    editor.remove(KEY_SCALING)
                }
            }
            .apply()
    }

    private fun readSettings(profileId: String): TrailSettings = TrailSettings(
        length = readLength(profileId),
        type = readType(profileId),
        windDriftEnabled = readBoolean(
            profileId = profileId,
            key = KEY_WIND_DRIFT,
            defaultValue = DEFAULT_WIND_DRIFT
        ),
        scalingEnabled = readBoolean(
            profileId = profileId,
            key = KEY_SCALING,
            defaultValue = DEFAULT_SCALING
        )
    )

    private fun readLength(profileId: String): TrailLength {
        val stored = readString(
            profileId = profileId,
            key = KEY_LENGTH,
            defaultValue = DEFAULT_LENGTH.name
        ) ?: DEFAULT_LENGTH.name
        if (stored == "NONE") {
            return TrailLength.OFF
        }
        return runCatching { TrailLength.valueOf(stored) }.getOrDefault(DEFAULT_LENGTH)
    }

    private fun readType(profileId: String): TrailType {
        val stored = readString(
            profileId = profileId,
            key = KEY_TYPE,
            defaultValue = DEFAULT_TYPE.name
        ) ?: DEFAULT_TYPE.name
        return runCatching { TrailType.valueOf(stored) }.getOrDefault(DEFAULT_TYPE)
    }

    private fun readString(profileId: String, key: String, defaultValue: String): String? {
        val scoped = prefs.getString(scopedKey(profileId, key), null)
        if (scoped != null) return scoped
        if (isLegacyFallbackEligible(profileId)) {
            return prefs.getString(key, defaultValue)
        }
        return defaultValue
    }

    private fun readBoolean(profileId: String, key: String, defaultValue: Boolean): Boolean {
        val scoped = scopedKey(profileId, key)
        if (prefs.contains(scoped)) {
            return prefs.getBoolean(scoped, defaultValue)
        }
        if (isLegacyFallbackEligible(profileId)) {
            return prefs.getBoolean(key, defaultValue)
        }
        return defaultValue
    }

    private fun scopedKey(profileId: String, key: String): String =
        "profile_${ProfileSettingsProfileIds.canonicalOrDefault(profileId)}_$key"

    private fun isLegacyFallbackEligible(profileId: String): Boolean {
        return profileId == DEFAULT_PROFILE_ID
    }

    companion object {
        private const val PREFS_NAME = "map_trail_prefs"
        private const val KEY_LENGTH = "trail_length"
        private const val KEY_TYPE = "trail_type"
        private const val KEY_WIND_DRIFT = "trail_wind_drift_enabled"
        private const val KEY_SCALING = "trail_scaling_enabled"
        private val DEFAULT_PROFILE_ID = ProfileSettingsProfileIds.CANONICAL_DEFAULT_PROFILE_ID

        private val DEFAULT_LENGTH = TrailLength.LONG
        private val DEFAULT_TYPE = TrailType.VARIO_1
        private const val DEFAULT_WIND_DRIFT = true
        private const val DEFAULT_SCALING = true
    }
}
