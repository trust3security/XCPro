package com.trust3.xcpro.map

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trust3.xcpro.core.common.profiles.ProfileSettingsProfileIds
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

private const val DATASTORE_NAME = "qnh_preferences"
private val Context.qnhDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)
private val KEY_QNH_HPA = doublePreferencesKey("qnh_hpa")
private val KEY_QNH_CAPTURED_AT_WALL_MS = longPreferencesKey("qnh_captured_at_wall_ms")
private val KEY_QNH_SOURCE = stringPreferencesKey("qnh_source")

data class QnhManualPreference(
    val qnhHpa: Double,
    val capturedAtWallMs: Long?,
    val source: String?
)

@Singleton
class QnhPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activeProfileId = MutableStateFlow(DEFAULT_PROFILE_ID)

    val qnhHpaFlow: Flow<Double?> = combine(
        activeProfileId,
        context.qnhDataStore.data
    ) { profileId, preferences ->
        readProfileManualQnh(profileId, preferences)?.qnhHpa
    }.distinctUntilChanged()

    val manualPreferenceFlow: Flow<QnhManualPreference?> = combine(
        activeProfileId,
        context.qnhDataStore.data
    ) { profileId, preferences ->
        readProfileManualQnh(profileId, preferences)
    }.distinctUntilChanged()

    fun setActiveProfileId(profileId: String) {
        val resolved = ProfileSettingsProfileIds.canonicalOrDefault(profileId)
        if (activeProfileId.value != resolved) {
            activeProfileId.value = resolved
        }
    }

    suspend fun setManualQnh(
        qnhHpa: Double,
        capturedAtWallMs: Long? = null,
        source: String? = null
    ) {
        writeProfileManualQnh(
            profileId = activeProfileId.value,
            qnhHpa = qnhHpa,
            capturedAtWallMs = capturedAtWallMs,
            source = source
        )
    }

    suspend fun clearManualQnh() {
        clearProfile(activeProfileId.value)
    }

    suspend fun writeProfileManualQnh(
        profileId: String,
        qnhHpa: Double?,
        capturedAtWallMs: Long?,
        source: String?
    ) {
        val resolvedProfileId = ProfileSettingsProfileIds.canonicalOrDefault(profileId)
        context.qnhDataStore.edit { preferences ->
            val qnhKey = scopedDoubleKey(resolvedProfileId, KEY_QNH_HPA.name)
            val capturedAtKey = scopedLongKey(resolvedProfileId, KEY_QNH_CAPTURED_AT_WALL_MS.name)
            val sourceKey = scopedStringKey(resolvedProfileId, KEY_QNH_SOURCE.name)
            if (qnhHpa == null) {
                preferences.remove(qnhKey)
                preferences.remove(capturedAtKey)
                preferences.remove(sourceKey)
            } else {
                preferences[qnhKey] = qnhHpa
                if (capturedAtWallMs != null) {
                    preferences[capturedAtKey] = capturedAtWallMs
                } else {
                    preferences.remove(capturedAtKey)
                }
                val normalizedSource = source?.trim()
                if (normalizedSource.isNullOrEmpty()) {
                    preferences.remove(sourceKey)
                } else {
                    preferences[sourceKey] = normalizedSource
                }
            }
            if (isLegacyFallbackEligible(resolvedProfileId)) {
                if (qnhHpa == null) {
                    preferences.remove(KEY_QNH_HPA)
                    preferences.remove(KEY_QNH_CAPTURED_AT_WALL_MS)
                    preferences.remove(KEY_QNH_SOURCE)
                } else {
                    preferences[KEY_QNH_HPA] = qnhHpa
                    if (capturedAtWallMs != null) {
                        preferences[KEY_QNH_CAPTURED_AT_WALL_MS] = capturedAtWallMs
                    } else {
                        preferences.remove(KEY_QNH_CAPTURED_AT_WALL_MS)
                    }
                    val normalizedSource = source?.trim()
                    if (normalizedSource.isNullOrEmpty()) {
                        preferences.remove(KEY_QNH_SOURCE)
                    } else {
                        preferences[KEY_QNH_SOURCE] = normalizedSource
                    }
                }
            }
        }
    }

    suspend fun readProfileManualQnh(profileId: String): QnhManualPreference? {
        val resolvedProfileId = ProfileSettingsProfileIds.canonicalOrDefault(profileId)
        val preferences = context.qnhDataStore.data.first()
        return readProfileManualQnh(resolvedProfileId, preferences)
    }

    suspend fun readActiveManualQnh(): QnhManualPreference? {
        val preferences = context.qnhDataStore.data.first()
        return readProfileManualQnh(activeProfileId.value, preferences)
    }

    suspend fun clearProfile(profileId: String) {
        val resolvedProfileId = ProfileSettingsProfileIds.canonicalOrDefault(profileId)
        context.qnhDataStore.edit { preferences ->
            clearProfileFromStore(preferences, resolvedProfileId)
        }
    }

    private fun clearProfileFromStore(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        profileId: String
    ) {
        preferences.remove(scopedDoubleKey(profileId, KEY_QNH_HPA.name))
        preferences.remove(scopedLongKey(profileId, KEY_QNH_CAPTURED_AT_WALL_MS.name))
        preferences.remove(scopedStringKey(profileId, KEY_QNH_SOURCE.name))
        if (isLegacyFallbackEligible(profileId)) {
            preferences.remove(KEY_QNH_HPA)
            preferences.remove(KEY_QNH_CAPTURED_AT_WALL_MS)
            preferences.remove(KEY_QNH_SOURCE)
        }
    }

    private fun readProfileManualQnh(
        profileId: String,
        preferences: Preferences
    ): QnhManualPreference? {
        val resolvedProfileId = ProfileSettingsProfileIds.canonicalOrDefault(profileId)
        val scopedQnh = preferences[scopedDoubleKey(resolvedProfileId, KEY_QNH_HPA.name)]
        val qnh = scopedQnh ?: if (isLegacyFallbackEligible(resolvedProfileId)) {
            preferences[KEY_QNH_HPA]
        } else {
            null
        }
        if (qnh == null) return null
        val capturedAt = preferences[scopedLongKey(resolvedProfileId, KEY_QNH_CAPTURED_AT_WALL_MS.name)]
            ?: if (isLegacyFallbackEligible(resolvedProfileId)) {
                preferences[KEY_QNH_CAPTURED_AT_WALL_MS]
            } else {
                null
            }
        val source = preferences[scopedStringKey(resolvedProfileId, KEY_QNH_SOURCE.name)]
            ?: if (isLegacyFallbackEligible(resolvedProfileId)) {
                preferences[KEY_QNH_SOURCE]
            } else {
                null
            }
        return QnhManualPreference(
            qnhHpa = qnh,
            capturedAtWallMs = capturedAt,
            source = source
        )
    }

    private fun isLegacyFallbackEligible(profileId: String): Boolean {
        return profileId == DEFAULT_PROFILE_ID
    }

    private fun scopedDoubleKey(profileId: String, suffix: String): Preferences.Key<Double> =
        doublePreferencesKey("profile.${ProfileSettingsProfileIds.canonicalOrDefault(profileId)}.$suffix")

    private fun scopedLongKey(profileId: String, suffix: String): Preferences.Key<Long> =
        longPreferencesKey("profile.${ProfileSettingsProfileIds.canonicalOrDefault(profileId)}.$suffix")

    private fun scopedStringKey(profileId: String, suffix: String): Preferences.Key<String> =
        stringPreferencesKey("profile.${ProfileSettingsProfileIds.canonicalOrDefault(profileId)}.$suffix")

    private companion object {
        private val DEFAULT_PROFILE_ID = ProfileSettingsProfileIds.CANONICAL_DEFAULT_PROFILE_ID
    }
}
