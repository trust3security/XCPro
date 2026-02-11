package com.example.xcpro.adsb

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val ADSB_DATASTORE_NAME = "adsb_traffic_preferences"
private val Context.adsbTrafficDataStore: DataStore<Preferences> by preferencesDataStore(
    name = ADSB_DATASTORE_NAME
)
private val KEY_ADSB_TRAFFIC_ENABLED = booleanPreferencesKey("adsb_traffic_enabled")
private val KEY_ADSB_ICON_SIZE_PX = intPreferencesKey("adsb_icon_size_px")

@Singleton
class AdsbTrafficPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val enabledFlow: Flow<Boolean> = context.adsbTrafficDataStore.data
        .map { preferences -> preferences[KEY_ADSB_TRAFFIC_ENABLED] ?: false }
        .distinctUntilChanged()

    val iconSizePxFlow: Flow<Int> = context.adsbTrafficDataStore.data
        .map { preferences ->
            clampAdsbIconSizePx(
                preferences[KEY_ADSB_ICON_SIZE_PX] ?: ADSB_ICON_SIZE_DEFAULT_PX
            )
        }
        .distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        context.adsbTrafficDataStore.edit { preferences ->
            preferences[KEY_ADSB_TRAFFIC_ENABLED] = enabled
        }
    }

    suspend fun setIconSizePx(iconSizePx: Int) {
        val clamped = clampAdsbIconSizePx(iconSizePx)
        context.adsbTrafficDataStore.edit { preferences ->
            preferences[KEY_ADSB_ICON_SIZE_PX] = clamped
        }
    }
}
