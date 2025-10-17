package com.example.xcpro.common.units

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.unitsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "units_preferences"
)

/**
 * Persistence layer for user-selected display units.
 *
 * The app keeps all calculations in SI; this repository simply stores preferred output units.
 */
class UnitsRepository(private val context: Context) {

    private object Keys {
        val ALTITUDE = stringPreferencesKey("units.altitude")
        val VERTICAL_SPEED = stringPreferencesKey("units.vertical_speed")
        val SPEED = stringPreferencesKey("units.speed")
        val DISTANCE = stringPreferencesKey("units.distance")
        val PRESSURE = stringPreferencesKey("units.pressure")
        val TEMPERATURE = stringPreferencesKey("units.temperature")
    }

    /**
     * Reactive stream of the current units selection. Downstream consumers should collect this
     * and feed it into [UnitsFormatter].
     */
    val unitsFlow: Flow<UnitsPreferences> = context.unitsDataStore.data.map { preferences ->
        UnitsPreferences(
            altitude = preferences[Keys.ALTITUDE]?.toAltitudeUnit() ?: AltitudeUnit.METERS,
            verticalSpeed = preferences[Keys.VERTICAL_SPEED]?.toVerticalSpeedUnit()
                ?: VerticalSpeedUnit.METERS_PER_SECOND,
            speed = preferences[Keys.SPEED]?.toSpeedUnit() ?: SpeedUnit.KILOMETERS_PER_HOUR,
            distance = preferences[Keys.DISTANCE]?.toDistanceUnit() ?: DistanceUnit.KILOMETERS,
            pressure = preferences[Keys.PRESSURE]?.toPressureUnit() ?: PressureUnit.HECTOPASCAL,
            temperature = preferences[Keys.TEMPERATURE]?.toTemperatureUnit()
                ?: TemperatureUnit.CELSIUS
        )
    }

    suspend fun setUnits(preferences: UnitsPreferences) {
        context.unitsDataStore.edit { store ->
            store[Keys.ALTITUDE] = preferences.altitude.name
            store[Keys.VERTICAL_SPEED] = preferences.verticalSpeed.name
            store[Keys.SPEED] = preferences.speed.name
            store[Keys.DISTANCE] = preferences.distance.name
            store[Keys.PRESSURE] = preferences.pressure.name
            store[Keys.TEMPERATURE] = preferences.temperature.name
        }
    }

    suspend fun setAltitude(unit: AltitudeUnit) = setValue(Keys.ALTITUDE, unit.name)
    suspend fun setVerticalSpeed(unit: VerticalSpeedUnit) = setValue(Keys.VERTICAL_SPEED, unit.name)
    suspend fun setSpeed(unit: SpeedUnit) = setValue(Keys.SPEED, unit.name)
    suspend fun setDistance(unit: DistanceUnit) = setValue(Keys.DISTANCE, unit.name)
    suspend fun setPressure(unit: PressureUnit) = setValue(Keys.PRESSURE, unit.name)
    suspend fun setTemperature(unit: TemperatureUnit) = setValue(Keys.TEMPERATURE, unit.name)

    suspend fun update(transform: (UnitsPreferences) -> UnitsPreferences) {
        context.unitsDataStore.edit { store ->
            val current = UnitsPreferences(
                altitude = store[Keys.ALTITUDE]?.toAltitudeUnit() ?: AltitudeUnit.METERS,
                verticalSpeed = store[Keys.VERTICAL_SPEED]?.toVerticalSpeedUnit()
                    ?: VerticalSpeedUnit.METERS_PER_SECOND,
                speed = store[Keys.SPEED]?.toSpeedUnit() ?: SpeedUnit.KILOMETERS_PER_HOUR,
                distance = store[Keys.DISTANCE]?.toDistanceUnit() ?: DistanceUnit.KILOMETERS,
                pressure = store[Keys.PRESSURE]?.toPressureUnit() ?: PressureUnit.HECTOPASCAL,
                temperature = store[Keys.TEMPERATURE]?.toTemperatureUnit()
                    ?: TemperatureUnit.CELSIUS
            )

            val updated = transform(current)
            store[Keys.ALTITUDE] = updated.altitude.name
            store[Keys.VERTICAL_SPEED] = updated.verticalSpeed.name
            store[Keys.SPEED] = updated.speed.name
            store[Keys.DISTANCE] = updated.distance.name
            store[Keys.PRESSURE] = updated.pressure.name
            store[Keys.TEMPERATURE] = updated.temperature.name
        }
    }

    private suspend fun setValue(key: Preferences.Key<String>, value: String) {
        context.unitsDataStore.edit { preferences ->
            preferences[key] = value
        }
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
