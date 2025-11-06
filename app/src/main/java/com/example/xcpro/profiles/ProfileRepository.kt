package com.example.xcpro.profiles

import androidx.compose.runtime.mutableStateOf
import com.example.dfcards.FlightTemplates
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.GsonBuilder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class ProfileRepository @Inject constructor(
    private val storage: ProfileStorage
) {

    private val gson = GsonBuilder()
        .setExclusionStrategies(object : ExclusionStrategy {
            override fun shouldSkipField(f: FieldAttributes?): Boolean =
                f?.declaredClass == androidx.compose.ui.graphics.vector.ImageVector::class.java

            override fun shouldSkipClass(clazz: Class<*>?): Boolean =
                clazz == androidx.compose.ui.graphics.vector.ImageVector::class.java
        })
        .create()

    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val profiles: StateFlow<List<UserProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<UserProfile?>(null)
    val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()

    private val activeProfileIdState = MutableStateFlow<String?>(null)

    init {
        internalScope.launch {
            storage.profilesJsonFlow.collect { json ->
                val loaded = parseProfiles(json)
                _profiles.value = loaded
                applyActiveProfile(activeProfileIdState.value, loaded)
            }
        }

        internalScope.launch {
            storage.activeProfileIdFlow.collect { id ->
                activeProfileIdState.value = id
                applyActiveProfile(id, _profiles.value)
            }
        }
    }

    private fun parseProfiles(json: String?): List<UserProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : com.google.gson.reflect.TypeToken<List<UserProfile>>() {}.type
            gson.fromJson<List<UserProfile>>(json, type)
        }.onFailure {
            android.util.Log.e("ProfileRepository", "Failed to parse profiles JSON", it)
        }.getOrDefault(emptyList())
    }

    private fun applyActiveProfile(id: String?, profiles: List<UserProfile>) {
        _activeProfile.value = id?.let { targetId -> profiles.find { it.id == targetId } }
    }

    private suspend fun persistProfiles() {
        val json = gson.toJson(_profiles.value)
        storage.writeProfilesJson(json)
    }

    private suspend fun persistActiveProfileId(id: String?) {
        storage.writeActiveProfileId(id)
    }

    suspend fun createProfile(request: ProfileCreationRequest): Result<UserProfile> {
        return runCatching {
            val newProfile = UserProfile(
                name = request.name,
                aircraftType = request.aircraftType,
                aircraftModel = request.aircraftModel,
                description = request.description,
                flightTemplateIds = getDefaultTemplateIdsForAircraft(request.aircraftType),
                cardConfigurations = getDefaultCardConfigurations(request.aircraftType)
            )

            val updatedProfiles = _profiles.value + newProfile
            _profiles.value = updatedProfiles
            if (_profiles.value.size == 1) {
                _activeProfile.value = newProfile
                activeProfileIdState.value = newProfile.id
                persistActiveProfileId(newProfile.id)
            }
            persistProfiles()
            newProfile
        }
    }

    suspend fun setActiveProfile(profile: UserProfile): Result<Unit> = runCatching {
        val existing = _profiles.value.find { it.id == profile.id } ?: run {
            val merged = (_profiles.value + profile).distinctBy { it.id }
            _profiles.value = merged
            persistProfiles()
            profile
        }
        _activeProfile.value = existing
        activeProfileIdState.value = existing.id
        persistActiveProfileId(existing.id)
    }

    suspend fun updateProfile(updatedProfile: UserProfile): Result<Unit> {
        return runCatching {
            val updatedProfiles = _profiles.value.map {
                if (it.id == updatedProfile.id) updatedProfile else it
            }
            _profiles.value = updatedProfiles
            if (_activeProfile.value?.id == updatedProfile.id) {
                _activeProfile.value = updatedProfile
            }
            persistProfiles()
        }
    }

    suspend fun deleteProfile(profileId: String): Result<Unit> {
        return runCatching {
            if (_profiles.value.size <= 1) {
                error("Cannot delete the last profile")
            }
            val remaining = _profiles.value.filter { it.id != profileId }
            if (remaining.size == _profiles.value.size) {
                error("Profile not found")
            }
            _profiles.value = remaining
            if (_activeProfile.value?.id == profileId) {
                val fallback = remaining.firstOrNull()
                _activeProfile.value = fallback
                activeProfileIdState.value = fallback?.id
                persistActiveProfileId(fallback?.id)
            }
            persistProfiles()
        }
    }

    fun hasProfiles(): Boolean = _profiles.value.isNotEmpty()

    fun hasActiveProfile(): Boolean = _activeProfile.value != null

    fun getCurrentProfileCardConfiguration(flightMode: com.example.xcpro.FlightMode): List<String> {
        val active = _activeProfile.value
        return active?.cardConfigurations?.get(flightMode)
            ?: ProfileAwareTemplates.getCardConfigurationForMode(
                active?.aircraftType ?: AircraftType.GLIDER,
                flightMode
            )
    }

    fun getDefaultTemplateIdsForAircraft(type: AircraftType): List<String> {
        return when (type) {
            AircraftType.GLIDER -> listOf("essential", "thermal")
            AircraftType.PARAGLIDER -> listOf("paraglider_essential")
            AircraftType.HANG_GLIDER -> listOf("hangglider_essential")
            AircraftType.SAILPLANE -> listOf("sailplane_essential")
        }
    }

    fun getDefaultCardConfigurations(type: AircraftType): Map<com.example.xcpro.FlightMode, List<String>> {
        return com.example.xcpro.FlightMode.entries.associateWith { mode ->
            ProfileAwareTemplates.getCardConfigurationForMode(type, mode)
        }
    }

    suspend fun saveProfileCardConfiguration(
        profileId: String,
        flightMode: com.example.xcpro.FlightMode,
        templateId: String
    ): Result<Unit> {
        return runCatching {
            val existingProfile = _profiles.value.find { it.id == profileId } ?: error("Profile not found")
            val updatedConfigurations = existingProfile.cardConfigurations.toMutableMap()
            updatedConfigurations[flightMode] = listOf(templateId)
            val updatedProfile = existingProfile.copy(cardConfigurations = updatedConfigurations)
            updateProfile(updatedProfile).getOrThrow()
        }
    }
}
