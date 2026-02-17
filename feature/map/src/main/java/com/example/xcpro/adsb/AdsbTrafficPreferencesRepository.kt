package com.example.xcpro.adsb

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
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
private val KEY_ADSB_MAX_DISTANCE_KM = intPreferencesKey("adsb_max_distance_km")
private val KEY_ADSB_VERTICAL_ABOVE_M = doublePreferencesKey("adsb_vertical_above_m")
private val KEY_ADSB_VERTICAL_BELOW_M = doublePreferencesKey("adsb_vertical_below_m")

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

    val maxDistanceKmFlow: Flow<Int> = context.adsbTrafficDataStore.data
        .map { preferences ->
            clampAdsbMaxDistanceKm(
                preferences[KEY_ADSB_MAX_DISTANCE_KM] ?: ADSB_MAX_DISTANCE_DEFAULT_KM
            )
        }
        .distinctUntilChanged()

    val verticalAboveMetersFlow: Flow<Double> = context.adsbTrafficDataStore.data
        .map { preferences ->
            clampAdsbVerticalFilterMeters(
                preferences[KEY_ADSB_VERTICAL_ABOVE_M] ?: ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
            )
        }
        .distinctUntilChanged()

    val verticalBelowMetersFlow: Flow<Double> = context.adsbTrafficDataStore.data
        .map { preferences ->
            clampAdsbVerticalFilterMeters(
                preferences[KEY_ADSB_VERTICAL_BELOW_M] ?: ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
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

    suspend fun setMaxDistanceKm(maxDistanceKm: Int) {
        val clamped = clampAdsbMaxDistanceKm(maxDistanceKm)
        context.adsbTrafficDataStore.edit { preferences ->
            preferences[KEY_ADSB_MAX_DISTANCE_KM] = clamped
        }
    }

    suspend fun setVerticalAboveMeters(aboveMeters: Double) {
        val clamped = clampAdsbVerticalFilterMeters(aboveMeters)
        context.adsbTrafficDataStore.edit { preferences ->
            preferences[KEY_ADSB_VERTICAL_ABOVE_M] = clamped
        }
    }

    suspend fun setVerticalBelowMeters(belowMeters: Double) {
        val clamped = clampAdsbVerticalFilterMeters(belowMeters)
        context.adsbTrafficDataStore.edit { preferences ->
            preferences[KEY_ADSB_VERTICAL_BELOW_M] = clamped
        }
    }
}
