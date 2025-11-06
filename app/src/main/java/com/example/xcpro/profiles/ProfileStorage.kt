package com.example.xcpro.profiles

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATA_STORE_NAME = "profile_preferences"
private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)
private val KEY_PROFILES_JSON = stringPreferencesKey("profiles_json")
private val KEY_ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")

interface ProfileStorage {
    val profilesJsonFlow: Flow<String?>
    val activeProfileIdFlow: Flow<String?>
    suspend fun writeProfilesJson(json: String?)
    suspend fun writeActiveProfileId(id: String?)
}

@Singleton
class DataStoreProfileStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : ProfileStorage {

    override val profilesJsonFlow: Flow<String?> =
        context.profileDataStore.data.map { it[KEY_PROFILES_JSON] }

    override val activeProfileIdFlow: Flow<String?> =
        context.profileDataStore.data.map { it[KEY_ACTIVE_PROFILE_ID] }

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
}
