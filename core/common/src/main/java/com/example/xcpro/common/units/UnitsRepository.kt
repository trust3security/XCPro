package com.example.xcpro.common.units

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

private val Context.unitsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "units_preferences"
)

/**
 * Persistence layer for user-selected display units.
 *
 * The app keeps all calculations in SI; this repository simply stores preferred output units.
 */
class UnitsRepository(private val context: Context) {

    private companion object {
        private const val DEFAULT_PROFILE_ID = "default-profile"
        private const val LEGACY_DEFAULT_ALIAS = "default"
        private const val LEGACY_DF_ALIAS = "__default_profile__"
    }

    private object LegacyKeys {
        val ALTITUDE = stringPreferencesKey("units.altitude")
        val VERTICAL_SPEED = stringPreferencesKey("units.vertical_speed")
        val SPEED = stringPreferencesKey("units.speed")
        val DISTANCE = stringPreferencesKey("units.distance")
        val PRESSURE = stringPreferencesKey("units.pressure")
        val TEMPERATURE = stringPreferencesKey("units.temperature")
    }

    private object ScopedKeySuffixes {
        const val ALTITUDE = "units.altitude"
        const val VERTICAL_SPEED = "units.vertical_speed"
        const val SPEED = "units.speed"
        const val DISTANCE = "units.distance"
        const val PRESSURE = "units.pressure"
        const val TEMPERATURE = "units.temperature"
    }

    private val activeProfileId = MutableStateFlow(DEFAULT_PROFILE_ID)

    /**
     * Reactive stream of the current units selection. Downstream consumers should collect this
     * and feed it into [UnitsFormatter].
     */
    val unitsFlow: Flow<UnitsPreferences> = combine(
        activeProfileId,
        context.unitsDataStore.data
    ) { profileId, preferences ->
        readUnits(preferences = preferences, profileId = profileId)
    }.distinctUntilChanged()

    fun setActiveProfileId(profileId: String) {
        val resolved = resolveProfileId(profileId)
        if (activeProfileId.value != resolved) {
            activeProfileId.value = resolved
        }
    }

    suspend fun setUnits(preferences: UnitsPreferences) {
        val profileId = activeProfileId.value
        context.unitsDataStore.edit { store ->
            writeUnits(
                store = store,
                profileId = profileId,
                preferences = preferences
            )
        }
    }

    suspend fun readProfileUnits(profileId: String): UnitsPreferences {
        val resolved = resolveProfileId(profileId)
        val preferences = context.unitsDataStore.data.first()
        return readUnits(preferences = preferences, profileId = resolved)
    }

    suspend fun writeProfileUnits(profileId: String, preferences: UnitsPreferences) {
        val resolved = resolveProfileId(profileId)
        context.unitsDataStore.edit { store ->
            writeUnits(
                store = store,
                profileId = resolved,
                preferences = preferences
            )
        }
    }

    suspend fun clearProfile(profileId: String) {
        val resolved = resolveProfileId(profileId)
        context.unitsDataStore.edit { store ->
            removeScopedUnits(store, resolved)
            if (resolved == DEFAULT_PROFILE_ID) {
                removeLegacyUnits(store)
            }
        }
    }

    suspend fun setAltitude(unit: AltitudeUnit) = setScopedValue(ScopedKeySuffixes.ALTITUDE, unit.name)
    suspend fun setVerticalSpeed(unit: VerticalSpeedUnit) = setScopedValue(ScopedKeySuffixes.VERTICAL_SPEED, unit.name)
    suspend fun setSpeed(unit: SpeedUnit) = setScopedValue(ScopedKeySuffixes.SPEED, unit.name)
    suspend fun setDistance(unit: DistanceUnit) = setScopedValue(ScopedKeySuffixes.DISTANCE, unit.name)
    suspend fun setPressure(unit: PressureUnit) = setScopedValue(ScopedKeySuffixes.PRESSURE, unit.name)
    suspend fun setTemperature(unit: TemperatureUnit) = setScopedValue(ScopedKeySuffixes.TEMPERATURE, unit.name)

    suspend fun update(transform: (UnitsPreferences) -> UnitsPreferences) {
        val profileId = activeProfileId.value
        context.unitsDataStore.edit { store ->
            val current = readUnits(preferences = store, profileId = profileId)
            val updated = transform(current)
            writeUnits(
                store = store,
                profileId = profileId,
                preferences = updated
            )
        }
    }

    private suspend fun setScopedValue(keySuffix: String, value: String) {
        val profileId = activeProfileId.value
        context.unitsDataStore.edit { preferences ->
            preferences[scopedKey(profileId, keySuffix)] = value
        }
    }

    private fun resolveProfileId(profileId: String?): String {
        val normalized = profileId?.trim().orEmpty()
        if (normalized.isBlank()) return DEFAULT_PROFILE_ID
        return when (normalized) {
            DEFAULT_PROFILE_ID,
            LEGACY_DEFAULT_ALIAS,
            LEGACY_DF_ALIAS -> DEFAULT_PROFILE_ID
            else -> normalized
        }
    }

    private fun scopedKey(profileId: String, keySuffix: String): Preferences.Key<String> =
        stringPreferencesKey("profile.${resolveProfileId(profileId)}.$keySuffix")

    private fun readUnits(
        preferences: Preferences,
        profileId: String
    ): UnitsPreferences {
        return UnitsPreferences(
            altitude = readScopedOrLegacyValue(
                preferences = preferences,
                profileId = profileId,
                scopedSuffix = ScopedKeySuffixes.ALTITUDE,
                legacyKey = LegacyKeys.ALTITUDE
            )?.toAltitudeUnit() ?: AltitudeUnit.METERS,
            verticalSpeed = readScopedOrLegacyValue(
                preferences = preferences,
                profileId = profileId,
                scopedSuffix = ScopedKeySuffixes.VERTICAL_SPEED,
                legacyKey = LegacyKeys.VERTICAL_SPEED
            )?.toVerticalSpeedUnit() ?: VerticalSpeedUnit.METERS_PER_SECOND,
            speed = readScopedOrLegacyValue(
                preferences = preferences,
                profileId = profileId,
                scopedSuffix = ScopedKeySuffixes.SPEED,
                legacyKey = LegacyKeys.SPEED
            )?.toSpeedUnit() ?: SpeedUnit.KILOMETERS_PER_HOUR,
            distance = readScopedOrLegacyValue(
                preferences = preferences,
                profileId = profileId,
                scopedSuffix = ScopedKeySuffixes.DISTANCE,
                legacyKey = LegacyKeys.DISTANCE
            )?.toDistanceUnit() ?: DistanceUnit.KILOMETERS,
            pressure = readScopedOrLegacyValue(
                preferences = preferences,
                profileId = profileId,
                scopedSuffix = ScopedKeySuffixes.PRESSURE,
                legacyKey = LegacyKeys.PRESSURE
            )?.toPressureUnit() ?: PressureUnit.HECTOPASCAL,
            temperature = readScopedOrLegacyValue(
                preferences = preferences,
                profileId = profileId,
                scopedSuffix = ScopedKeySuffixes.TEMPERATURE,
                legacyKey = LegacyKeys.TEMPERATURE
            )?.toTemperatureUnit() ?: TemperatureUnit.CELSIUS
        )
    }

    private fun writeUnits(
        store: MutablePreferences,
        profileId: String,
        preferences: UnitsPreferences
    ) {
        store[scopedKey(profileId, ScopedKeySuffixes.ALTITUDE)] = preferences.altitude.name
        store[scopedKey(profileId, ScopedKeySuffixes.VERTICAL_SPEED)] = preferences.verticalSpeed.name
        store[scopedKey(profileId, ScopedKeySuffixes.SPEED)] = preferences.speed.name
        store[scopedKey(profileId, ScopedKeySuffixes.DISTANCE)] = preferences.distance.name
        store[scopedKey(profileId, ScopedKeySuffixes.PRESSURE)] = preferences.pressure.name
        store[scopedKey(profileId, ScopedKeySuffixes.TEMPERATURE)] = preferences.temperature.name
    }

    private fun removeScopedUnits(store: MutablePreferences, profileId: String) {
        store.remove(scopedKey(profileId, ScopedKeySuffixes.ALTITUDE))
        store.remove(scopedKey(profileId, ScopedKeySuffixes.VERTICAL_SPEED))
        store.remove(scopedKey(profileId, ScopedKeySuffixes.SPEED))
        store.remove(scopedKey(profileId, ScopedKeySuffixes.DISTANCE))
        store.remove(scopedKey(profileId, ScopedKeySuffixes.PRESSURE))
        store.remove(scopedKey(profileId, ScopedKeySuffixes.TEMPERATURE))
    }

    private fun removeLegacyUnits(store: MutablePreferences) {
        store.remove(LegacyKeys.ALTITUDE)
        store.remove(LegacyKeys.VERTICAL_SPEED)
        store.remove(LegacyKeys.SPEED)
        store.remove(LegacyKeys.DISTANCE)
        store.remove(LegacyKeys.PRESSURE)
        store.remove(LegacyKeys.TEMPERATURE)
    }

    private fun readScopedOrLegacyValue(
        preferences: Preferences,
        profileId: String,
        scopedSuffix: String,
        legacyKey: Preferences.Key<String>
    ): String? {
        val scoped = preferences[scopedKey(profileId, scopedSuffix)]
        return scoped ?: preferences[legacyKey]
    }

    private fun String.toAltitudeUnit(): AltitudeUnit =
        runCatching { AltitudeUnit.valueOf(this) }.getOrDefault(AltitudeUnit.METERS)

    private fun String.toVerticalSpeedUnit(): VerticalSpeedUnit =
        runCatching { VerticalSpeedUnit.valueOf(this) }.getOrDefault(VerticalSpeedUnit.METERS_PER_SECOND)

    private fun String.toSpeedUnit(): SpeedUnit =
        runCatching { SpeedUnit.valueOf(this) }.getOrDefault(SpeedUnit.KILOMETERS_PER_HOUR)

    private fun String.toDistanceUnit(): DistanceUnit =
        runCatching { DistanceUnit.valueOf(this) }.getOrDefault(DistanceUnit.KILOMETERS)

    private fun String.toPressureUnit(): PressureUnit =
        runCatching { PressureUnit.valueOf(this) }.getOrDefault(PressureUnit.HECTOPASCAL)

    private fun String.toTemperatureUnit(): TemperatureUnit =
        runCatching { TemperatureUnit.valueOf(this) }.getOrDefault(TemperatureUnit.CELSIUS)
}
