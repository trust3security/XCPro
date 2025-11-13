package com.example.xcpro.vario

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "levo_vario_preferences"
private val Context.levoVarioDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)
private val KEY_IMU_ASSIST_ENABLED = booleanPreferencesKey("imu_assist_enabled")

data class LevoVarioConfig(
    val imuAssistEnabled: Boolean = true
)

@Singleton
class LevoVarioPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val config: Flow<LevoVarioConfig> = context.levoVarioDataStore.data.map { prefs ->
        LevoVarioConfig(
            imuAssistEnabled = prefs[KEY_IMU_ASSIST_ENABLED] ?: true
        )
    }

    suspend fun setImuAssistEnabled(enabled: Boolean) {
        context.levoVarioDataStore.edit { prefs ->
            prefs[KEY_IMU_ASSIST_ENABLED] = enabled
        }
    }
}
