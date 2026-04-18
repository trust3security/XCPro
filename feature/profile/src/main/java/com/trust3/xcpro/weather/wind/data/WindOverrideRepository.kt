package com.trust3.xcpro.weather.wind.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.weather.wind.model.WindOverride
import com.trust3.xcpro.weather.wind.model.WindSource
import com.trust3.xcpro.weather.wind.model.WindVector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "wind_preferences"
private val Context.windDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)
private val KEY_WIND_SPEED_MS = doublePreferencesKey("manual_wind_speed_ms")
private val KEY_WIND_DIR_FROM_DEG = doublePreferencesKey("manual_wind_direction_from_deg")
private val KEY_WIND_TIMESTAMP_MS = longPreferencesKey("manual_wind_timestamp_ms")

@Singleton
class WindOverrideRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock
) : WindOverrideSource {
    override val manualWind: Flow<WindOverride?> = context.windDataStore.data
        .map { preferences ->
            val speed = preferences[KEY_WIND_SPEED_MS]
            val direction = preferences[KEY_WIND_DIR_FROM_DEG]
            val timestamp = preferences[KEY_WIND_TIMESTAMP_MS]
            if (speed != null && direction != null && timestamp != null) {
                toOverride(
                    speedMs = speed,
                    directionFromDeg = direction,
                    timestampMillis = timestamp,
                    source = WindSource.MANUAL
                )
            } else {
                null
            }
        }

    private val _externalWind = MutableStateFlow<WindOverride?>(null)
    override val externalWind: Flow<WindOverride?> = _externalWind.asStateFlow()

    suspend fun setManualWind(
        speedMs: Double,
        directionFromDeg: Double
    ): Boolean = setManualWind(speedMs, directionFromDeg, clock.nowWallMs())

    suspend fun setManualWind(
        speedMs: Double,
        directionFromDeg: Double,
        timestampMillis: Long
    ): Boolean {
        toOverride(speedMs, directionFromDeg, timestampMillis, WindSource.MANUAL)
            ?: return false
        context.windDataStore.edit { preferences ->
            preferences[KEY_WIND_SPEED_MS] = speedMs
            preferences[KEY_WIND_DIR_FROM_DEG] = normalizeDirection(directionFromDeg)
            preferences[KEY_WIND_TIMESTAMP_MS] = timestampMillis
        }
        return true
    }

    suspend fun clearManualWind() {
        context.windDataStore.edit { preferences ->
            preferences.remove(KEY_WIND_SPEED_MS)
            preferences.remove(KEY_WIND_DIR_FROM_DEG)
            preferences.remove(KEY_WIND_TIMESTAMP_MS)
        }
    }

    fun updateExternalWind(
        speedMs: Double,
        directionFromDeg: Double
    ): Boolean = updateExternalWind(speedMs, directionFromDeg, clock.nowWallMs())

    fun updateExternalWind(
        speedMs: Double,
        directionFromDeg: Double,
        timestampMillis: Long
    ): Boolean {
        val override = toOverride(speedMs, directionFromDeg, timestampMillis, WindSource.EXTERNAL)
            ?: return false
        _externalWind.value = override
        return true
    }

    fun updateExternalWindVector(vector: WindVector) {
        updateExternalWindVector(vector, clock.nowWallMs())
    }

    fun updateExternalWindVector(
        vector: WindVector,
        timestampMillis: Long
    ) {
        _externalWind.value = WindOverride(
            vector = vector,
            timestampMillis = timestampMillis,
            source = WindSource.EXTERNAL
        )
    }

    fun clearExternalWind() {
        _externalWind.value = null
    }

    private fun toOverride(
        speedMs: Double,
        directionFromDeg: Double,
        timestampMillis: Long,
        source: WindSource
    ): WindOverride? {
        if (!speedMs.isFinite() || speedMs < 0.0) return null
        if (!directionFromDeg.isFinite()) return null
        if (abs(speedMs) < MIN_SPEED_MS) return null
        val normalizedDirection = normalizeDirection(directionFromDeg)
        val vector = WindVector.fromSpeedAndBearing(
            speed = speedMs,
            directionFromRad = Math.toRadians(normalizedDirection)
        )
        return WindOverride(
            vector = vector,
            timestampMillis = timestampMillis,
            source = source
        )
    }

    private fun normalizeDirection(directionDeg: Double): Double =
        ((directionDeg % 360.0) + 360.0) % 360.0

    private companion object {
        private const val MIN_SPEED_MS = 0.1
    }
}
