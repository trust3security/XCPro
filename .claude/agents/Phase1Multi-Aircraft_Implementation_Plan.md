# Multi-Aircraft Support Implementation Plan
## Detailed Development Strategy

### Document Information
- **Version**: 1.0
- **Date**: September 2025
- **Based on**: Multi-Aircraft Support PRD v1.0
- **Status**: Implementation Ready

---

## 🎯 Implementation Overview

This plan provides a step-by-step implementation guide for adding multi-aircraft support to the gliding app. The approach extends the current robust profile system rather than replacing it, ensuring minimal disruption to existing functionality.

### Implementation Approach
- **Evolutionary**: Extend existing systems gradually
- **Backward Compatible**: Legacy profiles continue working during transition
- **Risk Minimized**: Each phase is independently testable and rollback-capable
- **Data Safe**: Complete backup and migration validation at every step

---

## 📋 Phase 1: Data Layer Foundation (Weeks 1-2)

### 1.1 Core Data Models

#### Create New Data Classes

**File**: `app/src/main/java/com/example/baseui1/aircraft/models/`

**`Pilot.kt`**
```kotlin
package com.example.xcpro.aircraft.models

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class Pilot(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),

    @SerializedName("name")
    val name: String,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("preferences")
    val preferences: PilotPreferences = PilotPreferences(),

    @SerializedName("activeAircraftId")
    val activeAircraftId: String? = null,

    @SerializedName("aircraftIds")
    val aircraftIds: List<String> = emptyList(),

    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @SerializedName("lastUsed")
    val lastUsed: Long = System.currentTimeMillis()
) {
    fun getDisplayName(): String = name

    fun getActiveAircraftCount(): Int = aircraftIds.size

    fun hasMultipleAircraft(): Boolean = aircraftIds.size > 1
}

data class PilotPreferences(
    @SerializedName("units")
    val units: UnitSystem = UnitSystem.METRIC,

    @SerializedName("theme")
    val theme: AppTheme = AppTheme.SYSTEM,

    @SerializedName("language")
    val language: String = "en",

    @SerializedName("safetySettings")
    val safetySettings: SafetySettings = SafetySettings(),

    @SerializedName("logbookSettings")
    val logbookSettings: LogbookSettings = LogbookSettings()
)

enum class UnitSystem {
    @SerializedName("metric") METRIC,
    @SerializedName("imperial") IMPERIAL
}

enum class AppTheme {
    @SerializedName("light") LIGHT,
    @SerializedName("dark") DARK,
    @SerializedName("system") SYSTEM
}

data class SafetySettings(
    @SerializedName("altitudeWarnings")
    val altitudeWarnings: Boolean = true,

    @SerializedName("speedWarnings")
    val speedWarnings: Boolean = true,

    @SerializedName("terrainWarnings")
    val terrainWarnings: Boolean = true
)

data class LogbookSettings(
    @SerializedName("autoLogFlights")
    val autoLogFlights: Boolean = true,

    @SerializedName("minFlightDuration")
    val minFlightDuration: Long = 300000 // 5 minutes
)
```

**`Aircraft.kt`**
```kotlin
package com.example.xcpro.aircraft.models

import com.example.xcpro.profiles.AircraftType
import com.google.gson.annotations.SerializedName
import java.util.UUID

data class Aircraft(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),

    @SerializedName("pilotId")
    val pilotId: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("type")
    val type: AircraftType,

    @SerializedName("model")
    val model: String? = null,

    @SerializedName("registration")
    val registration: String? = null,

    @SerializedName("competitionNumber")
    val competitionNumber: String? = null,

    @SerializedName("performanceData")
    val performanceData: AircraftPerformance? = null,

    @SerializedName("isActive")
    val isActive: Boolean = false,

    @SerializedName("lastUsed")
    val lastUsed: Long = System.currentTimeMillis(),

    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getDisplayName(): String {
        return when {
            registration != null && model != null -> "$model - $registration"
            registration != null -> "$name - $registration"
            model != null -> "$model - $name"
            else -> name
        }
    }

    fun getShortName(): String {
        return registration ?: model ?: name
    }

    fun getAvailableFlightModes(): List<FlightModeSelection> {
        return when (type) {
            AircraftType.PARAGLIDER, AircraftType.HANG_GLIDER ->
                listOf(FlightModeSelection.CRUISE, FlightModeSelection.THERMAL)
            AircraftType.SAILPLANE, AircraftType.GLIDER ->
                listOf(FlightModeSelection.CRUISE, FlightModeSelection.THERMAL, FlightModeSelection.FINAL_GLIDE)
        }
    }
}

data class AircraftPerformance(
    @SerializedName("maxLD")
    val maxLD: Double? = null,

    @SerializedName("stallSpeed")
    val stallSpeed: Double? = null,

    @SerializedName("bestGlideSpeed")
    val bestGlideSpeed: Double? = null,

    @SerializedName("maxRoughAirSpeed")
    val maxRoughAirSpeed: Double? = null,

    @SerializedName("wingArea")
    val wingArea: Double? = null,

    @SerializedName("emptyWeight")
    val emptyWeight: Double? = null,

    @SerializedName("maxWeight")
    val maxWeight: Double? = null
)
```

### 1.2 Repository Implementation

#### Create Repository Interfaces

**File**: `app/src/main/java/com/example/baseui1/aircraft/repository/`

**`PilotRepository.kt`**
```kotlin
package com.example.xcpro.aircraft.repository

import com.example.xcpro.aircraft.models.Pilot
import kotlinx.coroutines.flow.Flow

interface PilotRepository {
    suspend fun createPilot(pilot: Pilot): Result<Pilot>
    suspend fun getAllPilots(): Flow<List<Pilot>>
    suspend fun getPilotById(pilotId: String): Flow<Pilot?>
    suspend fun getActivePilot(): Flow<Pilot?>
    suspend fun setActivePilot(pilotId: String): Result<Unit>
    suspend fun updatePilot(pilot: Pilot): Result<Unit>
    suspend fun deletePilot(pilotId: String): Result<Unit>
    suspend fun addAircraftToPilot(pilotId: String, aircraftId: String): Result<Unit>
    suspend fun removeAircraftFromPilot(pilotId: String, aircraftId: String): Result<Unit>
}
```

**`AircraftRepository.kt`**
```kotlin
package com.example.xcpro.aircraft.repository

import com.example.xcpro.aircraft.models.Aircraft
import kotlinx.coroutines.flow.Flow

interface AircraftRepository {
    suspend fun createAircraft(aircraft: Aircraft): Result<Aircraft>
    suspend fun getAircraftForPilot(pilotId: String): Flow<List<Aircraft>>
    suspend fun getAircraftById(aircraftId: String): Flow<Aircraft?>
    suspend fun getActiveAircraft(pilotId: String): Flow<Aircraft?>
    suspend fun setActiveAircraft(pilotId: String, aircraftId: String): Result<Unit>
    suspend fun updateAircraft(aircraft: Aircraft): Result<Unit>
    suspend fun deleteAircraft(aircraftId: String): Result<Unit>
}
```

#### Implementation Classes

**`SharedPreferencesPilotRepository.kt`**
```kotlin
package com.example.xcpro.aircraft.repository.impl

import android.content.Context
import android.content.SharedPreferences
import com.example.xcpro.aircraft.models.Pilot
import com.example.xcpro.aircraft.repository.PilotRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPreferencesPilotRepository @Inject constructor(
    context: Context,
    private val gson: Gson
) : PilotRepository {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("pilot_preferences", Context.MODE_PRIVATE)

    private val pilotsFlow = MutableStateFlow<List<Pilot>>(emptyList())

    init {
        loadPilots()
    }

    private fun loadPilots() {
        val pilotsJson = sharedPreferences.getString(KEY_ALL_PILOTS, null)
        if (pilotsJson != null) {
            val type = object : TypeToken<List<Pilot>>() {}.type
            val pilots = gson.fromJson<List<Pilot>>(pilotsJson, type)
            pilotsFlow.value = pilots
        }
    }

    private suspend fun savePilots(pilots: List<Pilot>) {
        val pilotsJson = gson.toJson(pilots)
        sharedPreferences.edit()
            .putString(KEY_ALL_PILOTS, pilotsJson)
            .apply()
        pilotsFlow.value = pilots
    }

    override suspend fun createPilot(pilot: Pilot): Result<Pilot> {
        return try {
            val currentPilots = pilotsFlow.value.toMutableList()
            currentPilots.add(pilot)
            savePilots(currentPilots)

            // Set as active if first pilot
            if (currentPilots.size == 1) {
                setActivePilot(pilot.id)
            }

            Result.success(pilot)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllPilots(): Flow<List<Pilot>> = pilotsFlow

    override suspend fun getPilotById(pilotId: String): Flow<Pilot?> {
        return pilotsFlow.map { pilots -> pilots.find { it.id == pilotId } }
    }

    override suspend fun getActivePilot(): Flow<Pilot?> {
        val activePilotId = sharedPreferences.getString(KEY_ACTIVE_PILOT, null)
        return if (activePilotId != null) {
            getPilotById(activePilotId)
        } else {
            pilotsFlow.map { it.firstOrNull() } // Default to first pilot
        }
    }

    override suspend fun setActivePilot(pilotId: String): Result<Unit> {
        return try {
            sharedPreferences.edit()
                .putString(KEY_ACTIVE_PILOT, pilotId)
                .apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updatePilot(pilot: Pilot): Result<Unit> {
        return try {
            val currentPilots = pilotsFlow.value.toMutableList()
            val index = currentPilots.indexOfFirst { it.id == pilot.id }
            if (index != -1) {
                currentPilots[index] = pilot
                savePilots(currentPilots)
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Pilot not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deletePilot(pilotId: String): Result<Unit> {
        return try {
            val currentPilots = pilotsFlow.value.toMutableList()
            val removed = currentPilots.removeAll { it.id == pilotId }
            if (removed) {
                savePilots(currentPilots)

                // Clear active pilot if deleted
                val activePilotId = sharedPreferences.getString(KEY_ACTIVE_PILOT, null)
                if (activePilotId == pilotId) {
                    val newActivePilot = currentPilots.firstOrNull()
                    if (newActivePilot != null) {
                        setActivePilot(newActivePilot.id)
                    } else {
                        sharedPreferences.edit().remove(KEY_ACTIVE_PILOT).apply()
                    }
                }

                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Pilot not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addAircraftToPilot(pilotId: String, aircraftId: String): Result<Unit> {
        return try {
            val currentPilots = pilotsFlow.value.toMutableList()
            val pilotIndex = currentPilots.indexOfFirst { it.id == pilotId }
            if (pilotIndex != -1) {
                val pilot = currentPilots[pilotIndex]
                val updatedAircraftIds = pilot.aircraftIds.toMutableList()
                if (!updatedAircraftIds.contains(aircraftId)) {
                    updatedAircraftIds.add(aircraftId)
                    val updatedPilot = pilot.copy(aircraftIds = updatedAircraftIds)
                    currentPilots[pilotIndex] = updatedPilot
                    savePilots(currentPilots)
                }
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Pilot not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeAircraftFromPilot(pilotId: String, aircraftId: String): Result<Unit> {
        return try {
            val currentPilots = pilotsFlow.value.toMutableList()
            val pilotIndex = currentPilots.indexOfFirst { it.id == pilotId }
            if (pilotIndex != -1) {
                val pilot = currentPilots[pilotIndex]
                val updatedAircraftIds = pilot.aircraftIds.toMutableList()
                updatedAircraftIds.remove(aircraftId)

                // Clear active aircraft if removed
                val updatedActiveAircraftId = if (pilot.activeAircraftId == aircraftId) {
                    updatedAircraftIds.firstOrNull()
                } else {
                    pilot.activeAircraftId
                }

                val updatedPilot = pilot.copy(
                    aircraftIds = updatedAircraftIds,
                    activeAircraftId = updatedActiveAircraftId
                )
                currentPilots[pilotIndex] = updatedPilot
                savePilots(currentPilots)
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Pilot not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val KEY_ALL_PILOTS = "all_pilots"
        private const val KEY_ACTIVE_PILOT = "active_pilot_id"
    }
}
```

### 1.3 Migration System

#### Create Migration Manager

**`ProfileMigrationManager.kt`**
```kotlin
package com.example.xcpro.aircraft.migration

import android.content.Context
import com.example.xcpro.aircraft.models.Aircraft
import com.example.xcpro.aircraft.models.Pilot
import com.example.xcpro.aircraft.models.PilotPreferences
import com.example.xcpro.aircraft.repository.AircraftRepository
import com.example.xcpro.aircraft.repository.PilotRepository
import com.example.xcpro.profiles.ProfileRepository
import com.example.xcpro.profiles.UserProfile
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileMigrationManager @Inject constructor(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val pilotRepository: PilotRepository,
    private val aircraftRepository: AircraftRepository,
    private val gson: Gson
) {

    suspend fun needsMigration(): Boolean {
        // Check if migration has been completed
        val migrationCompleted = context.getSharedPreferences("migration", Context.MODE_PRIVATE)
            .getBoolean("profiles_migrated", false)

        if (migrationCompleted) return false

        // Check if there are legacy profiles to migrate
        val legacyProfiles = profileRepository.getAllProfiles().first()
        return legacyProfiles.isNotEmpty()
    }

    suspend fun createBackup(): Result<String> {
        return try {
            val legacyProfiles = profileRepository.getAllProfiles().first()
            val backupData = MigrationBackup(
                profiles = legacyProfiles,
                timestamp = System.currentTimeMillis(),
                version = "1.0"
            )

            val backupJson = gson.toJson(backupData)
            val backupFile = File(context.filesDir, "profile_migration_backup.json")
            backupFile.writeText(backupJson)

            Result.success(backupFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun performMigration(): Result<MigrationResult> {
        return try {
            // 1. Create backup
            val backupResult = createBackup()
            if (backupResult.isFailure) {
                return Result.failure(backupResult.exceptionOrNull()!!)
            }

            // 2. Get legacy profiles
            val legacyProfiles = profileRepository.getAllProfiles().first()

            // 3. Group profiles by pilot name (heuristic)
            val groupedProfiles = groupProfilesByPilotName(legacyProfiles)

            // 4. Create pilots and aircraft
            val migrationResults = mutableListOf<PilotMigrationResult>()

            for ((pilotName, profiles) in groupedProfiles) {
                val pilotResult = migratePilotGroup(pilotName, profiles)
                migrationResults.add(pilotResult)
            }

            // 5. Mark migration as complete
            context.getSharedPreferences("migration", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("profiles_migrated", true)
                .putString("migration_backup_path", backupResult.getOrNull())
                .putLong("migration_timestamp", System.currentTimeMillis())
                .apply()

            val result = MigrationResult(
                pilotsCreated = migrationResults.size,
                aircraftCreated = migrationResults.sumOf { it.aircraftCreated },
                profilesMigrated = legacyProfiles.size,
                backupPath = backupResult.getOrNull()!!,
                pilotResults = migrationResults
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun groupProfilesByPilotName(profiles: List<UserProfile>): Map<String, List<UserProfile>> {
        return profiles.groupBy { profile ->
            // Heuristic: Extract pilot name from profile name
            // Examples: "John Smith - ASG29" -> "John Smith"
            //          "John - Discus" -> "John"
            //          "John Smith" -> "John Smith"
            extractPilotName(profile.name)
        }
    }

    private fun extractPilotName(profileName: String): String {
        val separators = listOf(" - ", " | ", "_", "-")

        for (separator in separators) {
            if (profileName.contains(separator)) {
                val parts = profileName.split(separator)
                if (parts.size >= 2) {
                    return parts[0].trim()
                }
            }
        }

        return profileName.trim()
    }

    private suspend fun migratePilotGroup(pilotName: String, profiles: List<UserProfile>): PilotMigrationResult {
        // Create pilot from first profile
        val firstProfile = profiles.first()
        val pilot = Pilot(
            name = pilotName,
            preferences = PilotPreferences(), // Use defaults for now
            createdAt = System.currentTimeMillis()
        )

        val pilotResult = pilotRepository.createPilot(pilot)
        if (pilotResult.isFailure) {
            return PilotMigrationResult(
                pilotName = pilotName,
                success = false,
                error = pilotResult.exceptionOrNull()?.message,
                aircraftCreated = 0
            )
        }

        val createdPilot = pilotResult.getOrNull()!!
        var aircraftCreated = 0

        // Create aircraft from each profile
        for (profile in profiles) {
            val aircraft = Aircraft(
                pilotId = createdPilot.id,
                name = profile.aircraftModel ?: profile.aircraftType.displayName,
                type = profile.aircraftType,
                model = profile.aircraftModel,
                createdAt = System.currentTimeMillis()
            )

            val aircraftResult = aircraftRepository.createAircraft(aircraft)
            if (aircraftResult.isSuccess) {
                val createdAircraft = aircraftResult.getOrNull()!!
                pilotRepository.addAircraftToPilot(createdPilot.id, createdAircraft.id)
                aircraftCreated++

                // Set first aircraft as active
                if (aircraftCreated == 1) {
                    aircraftRepository.setActiveAircraft(createdPilot.id, createdAircraft.id)
                }

                // TODO: Migrate screen configurations from profile to aircraft
                migrateScreenConfigurations(profile, createdAircraft)
            }
        }

        return PilotMigrationResult(
            pilotName = pilotName,
            success = true,
            aircraftCreated = aircraftCreated
        )
    }

    private suspend fun migrateScreenConfigurations(profile: UserProfile, aircraft: Aircraft) {
        // TODO: Implement screen configuration migration
        // This will be implemented in Phase 3 when screen configuration integration is ready
    }

    suspend fun rollbackMigration(): Result<Unit> {
        return try {
            val migrationPrefs = context.getSharedPreferences("migration", Context.MODE_PRIVATE)
            val backupPath = migrationPrefs.getString("migration_backup_path", null)
                ?: return Result.failure(IllegalStateException("No backup found"))

            val backupFile = File(backupPath)
            if (!backupFile.exists()) {
                return Result.failure(IllegalStateException("Backup file not found"))
            }

            val backupJson = backupFile.readText()
            val backupData = gson.fromJson(backupJson, MigrationBackup::class.java)

            // Clear migrated data
            // TODO: Clear all pilots and aircraft

            // Restore legacy profiles
            for (profile in backupData.profiles) {
                profileRepository.createProfile(profile)
            }

            // Clear migration flag
            migrationPrefs.edit()
                .putBoolean("profiles_migrated", false)
                .apply()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class MigrationBackup(
    val profiles: List<UserProfile>,
    val timestamp: Long,
    val version: String
)

data class MigrationResult(
    val pilotsCreated: Int,
    val aircraftCreated: Int,
    val profilesMigrated: Int,
    val backupPath: String,
    val pilotResults: List<PilotMigrationResult>
)

data class PilotMigrationResult(
    val pilotName: String,
    val success: Boolean,
    val error: String? = null,
    val aircraftCreated: Int = 0
)
```

### 1.4 Enhanced CardPreferences

#### Extend CardPreferences for Aircraft Support

**Add to existing `CardPreferences.kt`:**
```kotlin
// Add these methods to existing CardPreferences class

// Aircraft-aware template methods
suspend fun saveAircraftFlightModeTemplate(
    aircraftId: String,
    flightMode: String,
    templateId: String
) {
    val key = "aircraft_${aircraftId}_flight_mode_${flightMode}_template"
    dataStore.edit { preferences ->
        preferences[stringPreferencesKey(key)] = templateId
    }
}

fun getAircraftFlightModeTemplate(
    aircraftId: String,
    flightMode: String
): Flow<String?> {
    val key = "aircraft_${aircraftId}_flight_mode_${flightMode}_template"
    return dataStore.data.map { preferences ->
        preferences[stringPreferencesKey(key)]
    }
}

// Aircraft-aware card configuration methods
suspend fun saveAircraftTemplateCards(
    aircraftId: String,
    templateId: String,
    cardIds: List<String>
) {
    val key = "aircraft_${aircraftId}_template_${templateId}_cards"
    dataStore.edit { preferences ->
        preferences[stringPreferencesKey(key)] = cardIds.joinToString(",")
    }
}

fun getAircraftTemplateCards(
    aircraftId: String,
    templateId: String
): Flow<List<String>?> {
    val key = "aircraft_${aircraftId}_template_${templateId}_cards"
    return dataStore.data.map { preferences ->
        preferences[stringPreferencesKey(key)]?.split(",")?.filter { it.isNotEmpty() }
    }
}

// Aircraft-aware position methods
suspend fun saveAircraftCardPositions(
    aircraftId: String,
    flightMode: String,
    cardStates: List<CardState>
) {
    dataStore.edit { preferences ->
        cardStates.forEach { cardState ->
            val xKey = "aircraft_${aircraftId}_${flightMode}_${cardState.id}_x"
            val yKey = "aircraft_${aircraftId}_${flightMode}_${cardState.id}_y"
            preferences[floatPreferencesKey(xKey)] = cardState.x
            preferences[floatPreferencesKey(yKey)] = cardState.y
        }
    }
}

fun getAircraftCardPositions(
    aircraftId: String,
    flightMode: String
): Flow<Map<String, CardPosition>> {
    return dataStore.data.map { preferences ->
        val positions = mutableMapOf<String, CardPosition>()

        // This is a simplified version - in practice, you'd need to iterate through
        // known card IDs or store them separately
        preferences.asMap().forEach { (key, value) ->
            val keyString = key.name
            if (keyString.startsWith("aircraft_${aircraftId}_${flightMode}_") && keyString.endsWith("_x")) {
                val cardId = keyString.substring(
                    "aircraft_${aircraftId}_${flightMode}_".length,
                    keyString.length - 2
                )
                val xKey = floatPreferencesKey("aircraft_${aircraftId}_${flightMode}_${cardId}_x")
                val yKey = floatPreferencesKey("aircraft_${aircraftId}_${flightMode}_${cardId}_y")

                val x = preferences[xKey] ?: 0f
                val y = preferences[yKey] ?: 0f

                positions[cardId] = CardPosition(x, y)
            }
        }

        positions
    }
}

// Aircraft-aware flight mode visibility
suspend fun saveAircraftFlightModeVisibility(
    aircraftId: String,
    flightMode: String,
    isVisible: Boolean
) {
    val key = "aircraft_${aircraftId}_${flightMode}_visible"
    dataStore.edit { preferences ->
        preferences[booleanPreferencesKey(key)] = isVisible
    }
}

fun getAircraftFlightModeVisibility(
    aircraftId: String,
    flightMode: String
): Flow<Boolean> {
    val key = "aircraft_${aircraftId}_${flightMode}_visible"
    return dataStore.data.map { preferences ->
        preferences[booleanPreferencesKey(key)] ?: true // Default to visible
    }
}

// Migration helper methods
suspend fun migrateProfileToAircraftConfig(
    profileId: String,
    aircraftId: String
) {
    // Get all profile-specific configurations
    val profileKeys = mutableListOf<String>()

    dataStore.data.first().asMap().keys.forEach { key ->
        if (key.name.startsWith("profile_${profileId}_")) {
            profileKeys.add(key.name)
        }
    }

    // Convert profile keys to aircraft keys
    dataStore.edit { preferences ->
        profileKeys.forEach { profileKey ->
            val value = preferences.asMap().entries.find { it.key.name == profileKey }?.value

            if (value != null) {
                // Convert profile key to aircraft key
                val aircraftKey = profileKey.replace("profile_${profileId}_", "aircraft_${aircraftId}_")

                when (value) {
                    is String -> preferences[stringPreferencesKey(aircraftKey)] = value
                    is Boolean -> preferences[booleanPreferencesKey(aircraftKey)] = value
                    is Float -> preferences[floatPreferencesKey(aircraftKey)] = value
                    is Int -> preferences[intPreferencesKey(aircraftKey)] = value
                    is Long -> preferences[longPreferencesKey(aircraftKey)] = value
                }
            }
        }
    }
}
```

---

## 📋 Phase 1 Tasks Summary

### Week 1: Core Data Models and Repositories

#### Day 1-2: Data Models
- [ ] Create `aircraft/models/` package
- [ ] Implement `Pilot.kt` with full data class and utility methods
- [ ] Implement `Aircraft.kt` with performance data support
- [ ] Create supporting enums (`UnitSystem`, `AppTheme`)
- [ ] Add Gson annotations for serialization

#### Day 3-4: Repository Interfaces
- [ ] Create `aircraft/repository/` package
- [ ] Define `PilotRepository` interface with all CRUD operations
- [ ] Define `AircraftRepository` interface with pilot-aircraft relationships
- [ ] Create repository implementation classes

#### Day 5: SharedPreferences Implementation
- [ ] Implement `SharedPreferencesPilotRepository` with JSON serialization
- [ ] Implement `SharedPreferencesAircraftRepository` with relationships
- [ ] Add proper error handling and Result types
- [ ] Implement caching with StateFlow

### Week 2: Migration System and CardPreferences Extension

#### Day 1-2: Migration Manager
- [ ] Create `ProfileMigrationManager` with backup system
- [ ] Implement profile grouping heuristics (name-based)
- [ ] Add migration validation and rollback capabilities
- [ ] Create migration data classes and result types

#### Day 3-4: CardPreferences Extension
- [ ] Add aircraft-aware methods to existing `CardPreferences`
- [ ] Implement aircraft-specific template storage
- [ ] Add aircraft card position management
- [ ] Create profile-to-aircraft migration helpers

#### Day 5: Testing and Integration
- [ ] Create comprehensive unit tests for all repositories
- [ ] Test migration scenarios (single profile, multiple profiles)
- [ ] Validate data integrity and rollback functionality
- [ ] Performance testing with large datasets

### Phase 1 Success Criteria

✅ **Data Layer Complete:**
- All data models defined with proper serialization
- Repository pattern implemented with SharedPreferences backend
- Migration system can backup and convert legacy profiles
- CardPreferences extended for aircraft-specific configurations

✅ **Migration Validated:**
- 100% data preservation during migration
- Rollback capability tested and working
- Performance acceptable for typical user data sizes
- Error handling robust for edge cases

✅ **Ready for Phase 2:**
- Repository interfaces stable for UI integration
- Migration system ready for user-facing wizard
- Data access patterns optimized for UI consumption

---

## 🎯 Next Steps

After Phase 1 completion, the data foundation will be solid for:

**Phase 2: UI Layer Updates**
- Navigation drawer enhancements
- Aircraft management screens
- Migration wizard UI

**Phase 3: Screen Configuration Integration**
- Aircraft-aware flight data management
- Template inheritance system
- Configuration migration completion

This implementation plan provides a solid foundation that extends the existing robust profile system while maintaining backward compatibility and data safety.