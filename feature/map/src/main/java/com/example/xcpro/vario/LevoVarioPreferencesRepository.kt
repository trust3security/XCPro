package com.example.xcpro.vario

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "levo_vario_preferences"
private val Context.levoVarioDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)
private val KEY_MACCREADY = doublePreferencesKey("maccready_value")
private val KEY_MACCREADY_RISK = doublePreferencesKey("maccready_risk_value")

data class LevoVarioConfig(
    val macCready: Double = 0.0,
    val macCreadyRisk: Double = 0.0
)

@Singleton
class LevoVarioPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val config: Flow<LevoVarioConfig> = context.levoVarioDataStore.data.map { prefs ->
        val mac = prefs[KEY_MACCREADY] ?: 0.0
        LevoVarioConfig(
            macCready = mac,
            macCreadyRisk = prefs[KEY_MACCREADY_RISK] ?: mac
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
}
