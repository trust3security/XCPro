package com.trust3.xcpro.profiles

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

private const val DATA_STORE_NAME = "profile_preferences"
private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)
private val KEY_PROFILES_JSON = stringPreferencesKey("profiles_json")
private val KEY_ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")

enum class ProfileStorageReadStatus {
    OK,
    IO_ERROR,
    UNKNOWN_ERROR
}

data class ProfileStorageSnapshot(
    val profilesJson: String?,
    val activeProfileId: String?,
    val readStatus: ProfileStorageReadStatus
)

interface ProfileStorage {
    val snapshotFlow: Flow<ProfileStorageSnapshot>
    val profilesJsonFlow: Flow<String?>
        get() = snapshotFlow.map { it.profilesJson }
    val activeProfileIdFlow: Flow<String?>
        get() = snapshotFlow.map { it.activeProfileId }
    suspend fun writeProfilesJson(json: String?)
    suspend fun writeActiveProfileId(id: String?)
    suspend fun writeState(profilesJson: String?, activeProfileId: String?)
}

@Singleton
class DataStoreProfileStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : ProfileStorage {

    companion object {
        private const val TAG = "DataStoreProfileStorage"
    }

    override val snapshotFlow: Flow<ProfileStorageSnapshot> =
        context.profileDataStore.data
            .map { preferences ->
                ProfileStorageSnapshot(
                    profilesJson = preferences[KEY_PROFILES_JSON],
                    activeProfileId = preferences[KEY_ACTIVE_PROFILE_ID],
                    readStatus = ProfileStorageReadStatus.OK
                )
            }
            .retryWhen { error, attempt ->
                if (error is CancellationException) {
                    false
                } else {
                    val status = when (error) {
                        is IOException -> ProfileStorageReadStatus.IO_ERROR
                        else -> ProfileStorageReadStatus.UNKNOWN_ERROR
                    }
                    runCatching {
                        Log.e(TAG, "Failed to read profile snapshot from DataStore (attempt=$attempt)", error)
                    }
                    emit(
                        ProfileStorageSnapshot(
                            profilesJson = null,
                            activeProfileId = null,
                            readStatus = status
                        )
                    )
                    delay(500)
                    true
                }
            }

    override suspend fun writeProfilesJson(json: String?) {
        context.profileDataStore.edit { prefs ->
            if (json == null) {
                prefs.remove(KEY_PROFILES_JSON)
            } else {
                prefs[KEY_PROFILES_JSON] = json
            }
        }
    }

    override suspend fun writeActiveProfileId(id: String?) {
        context.profileDataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(KEY_ACTIVE_PROFILE_ID)
            } else {
                prefs[KEY_ACTIVE_PROFILE_ID] = id
            }
        }
    }

    override suspend fun writeState(profilesJson: String?, activeProfileId: String?) {
        context.profileDataStore.edit { prefs ->
            if (profilesJson == null) {
                prefs.remove(KEY_PROFILES_JSON)
            } else {
                prefs[KEY_PROFILES_JSON] = profilesJson
            }
            if (activeProfileId == null) {
                prefs.remove(KEY_ACTIVE_PROFILE_ID)
            } else {
                prefs[KEY_ACTIVE_PROFILE_ID] = activeProfileId
            }
        }
    }
}
