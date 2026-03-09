package com.example.xcpro.adsb.metadata.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val METADATA_SYNC_DATASTORE = "adsb_metadata_sync_checkpoint"
private val Context.metadataSyncDataStore: DataStore<Preferences> by preferencesDataStore(
    name = METADATA_SYNC_DATASTORE
)

data class AircraftMetadataSyncCheckpoint(
    val sourceKey: String?,
    val etag: String?,
    val lastSuccessWallMs: Long?,
    val lastAttemptWallMs: Long?,
    val lastError: String?
)

@Singleton
class AircraftMetadataSyncCheckpointStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val checkpointFlow: Flow<AircraftMetadataSyncCheckpoint> = context.metadataSyncDataStore.data
        .map { preferences ->
            AircraftMetadataSyncCheckpoint(
                sourceKey = preferences[KEY_SOURCE_KEY],
                etag = preferences[KEY_ETAG],
                lastSuccessWallMs = preferences[KEY_LAST_SUCCESS_WALL_MS],
                lastAttemptWallMs = preferences[KEY_LAST_ATTEMPT_WALL_MS],
                lastError = preferences[KEY_LAST_ERROR]
            )
        }

    suspend fun snapshot(): AircraftMetadataSyncCheckpoint = checkpointFlow.first()

    suspend fun markScheduledAttempt(nowWallMs: Long) {
        context.metadataSyncDataStore.edit { preferences ->
            preferences[KEY_LAST_ATTEMPT_WALL_MS] = nowWallMs
        }
    }

    suspend fun markSuccess(
        sourceKey: String,
        etag: String?,
        nowWallMs: Long
    ) {
        context.metadataSyncDataStore.edit { preferences ->
            preferences[KEY_SOURCE_KEY] = sourceKey
            if (!etag.isNullOrBlank()) {
                preferences[KEY_ETAG] = etag
            } else {
                preferences.remove(KEY_ETAG)
            }
            preferences[KEY_LAST_SUCCESS_WALL_MS] = nowWallMs
            preferences[KEY_LAST_ATTEMPT_WALL_MS] = nowWallMs
            preferences.remove(KEY_LAST_ERROR)
        }
    }

    suspend fun markFailure(reason: String, nowWallMs: Long) {
        context.metadataSyncDataStore.edit { preferences ->
            preferences[KEY_LAST_ATTEMPT_WALL_MS] = nowWallMs
            preferences[KEY_LAST_ERROR] = reason
        }
    }

    private companion object {
        val KEY_SOURCE_KEY = stringPreferencesKey("adsb_metadata_source_key")
        val KEY_ETAG = stringPreferencesKey("adsb_metadata_source_etag")
        val KEY_LAST_SUCCESS_WALL_MS = longPreferencesKey("adsb_metadata_last_success_wall_ms")
        val KEY_LAST_ATTEMPT_WALL_MS = longPreferencesKey("adsb_metadata_last_attempt_wall_ms")
        val KEY_LAST_ERROR = stringPreferencesKey("adsb_metadata_last_error")
    }
}
