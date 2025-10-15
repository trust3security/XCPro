package com.example.xcpro.profiles

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import com.example.dfcards.FlightTemplates
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.gson.*
import java.lang.reflect.Type
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProfileRepository private constructor(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val gson = GsonBuilder()
        .setExclusionStrategies(object : ExclusionStrategy {
            override fun shouldSkipField(f: FieldAttributes?): Boolean {
                return f?.declaredClass == ImageVector::class.java
            }
            override fun shouldSkipClass(clazz: Class<*>?): Boolean {
                return clazz == ImageVector::class.java
            }
        })
        .create()
    
    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val profiles: StateFlow<List<UserProfile>> = _profiles.asStateFlow()
    
    private val _activeProfile = MutableStateFlow<UserProfile?>(null)
    val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()
    
    init {
        android.util.Log.d("ProfileRepository", "Initializing ProfileRepository")
        loadProfiles()
        android.util.Log.d("ProfileRepository", "ProfileRepository initialized with ${_profiles.value.size} profiles")
    }
    
    companion object {
        @Volatile
        private var INSTANCE: ProfileRepository? = null
        
        fun getInstance(context: Context): ProfileRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProfileRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private fun loadProfiles() {
        val profilesJson = prefs.getString("user_profiles", null)
        val activeProfileId = prefs.getString("active_profile_id", null)
        
        android.util.Log.d("ProfileRepository", "Loading profiles - JSON: $profilesJson")
        android.util.Log.d("ProfileRepository", "Loading profiles - ActiveID: $activeProfileId")
        
        val loadedProfiles = if (profilesJson != null) {
            try {
                val type = object : TypeToken<List<UserProfile>>() {}.type
                gson.fromJson<List<UserProfile>>(profilesJson, type)
            } catch (e: Exception) {
                android.util.Log.e("ProfileRepository", "Error loading profiles", e)
                emptyList()
            }
        } else {
            android.util.Log.d("ProfileRepository", "No profiles found in SharedPreferences")
            emptyList()
        }
        
        android.util.Log.d("ProfileRepository", "Loaded ${loadedProfiles.size} profiles")
        _profiles.value = loadedProfiles
        
        val foundActiveProfile = loadedProfiles.find { it.id == activeProfileId }
        _activeProfile.value = foundActiveProfile
        android.util.Log.d("ProfileRepository", "Active profile loaded: ${foundActiveProfile?.name} (ID: ${foundActiveProfile?.id})")
    }
    
    private fun saveProfiles() {
        val profilesJson = gson.toJson(_profiles.value)
        android.util.Log.d("ProfileRepository", "Saving ${_profiles.value.size} profiles")
        android.util.Log.d("ProfileRepository", "JSON: $profilesJson")
        android.util.Log.d("ProfileRepository", "Active profile ID: ${_activeProfile.value?.id}")
        
        val success = prefs.edit()
            .putString("user_profiles", profilesJson)
            .putString("active_profile_id", _activeProfile.value?.id)
            .commit() // Use commit() instead of apply() for immediate save
            
        android.util.Log.d("ProfileRepository", "Save success: $success")
    }
    
    suspend fun createProfile(request: ProfileCreationRequest): Result<UserProfile> {
        return try {
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
            saveProfiles()
            
            Result.success(newProfile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun setActiveProfile(profile: UserProfile): Result<Unit> {
        return try {
            android.util.Log.d("ProfileRepository", "Setting active profile: ${profile.name} (${profile.id})")
            android.util.Log.d("ProfileRepository", "Current active profile: ${_activeProfile.value?.name}")
            
            // Find the profile in our current list to make sure it exists
            val existingProfile = _profiles.value.find { it.id == profile.id }
            if (existingProfile == null) {
                android.util.Log.e("ProfileRepository", "Profile not found: ${profile.id}")
                return Result.failure(Exception("Profile not found"))
            }
            
            // Simply set the active profile - don't modify the profiles list
            _activeProfile.value = existingProfile
            saveProfiles()
            
            android.util.Log.d("ProfileRepository", "✅ Active profile successfully set to: ${_activeProfile.value?.name}")
            android.util.Log.d("ProfileRepository", "Active profile ID: ${_activeProfile.value?.id}")
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ProfileRepository", "❌ Error setting active profile", e)
            Result.failure(e)
        }
    }
    
    suspend fun updateProfile(updatedProfile: UserProfile): Result<Unit> {
        return try {
            val updatedProfiles = _profiles.value.map { 
                if (it.id == updatedProfile.id) updatedProfile else it 
            }
            
            _profiles.value = updatedProfiles
            
            if (_activeProfile.value?.id == updatedProfile.id) {
                _activeProfile.value = updatedProfile
            }
            
            saveProfiles()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteProfile(profileId: String): Result<Unit> {
        return try {
            val profileToDelete = _profiles.value.find { it.id == profileId }
                ?: return Result.failure(Exception("Profile not found"))
            
            if (_profiles.value.size <= 1) {
                return Result.failure(Exception("Cannot delete the last profile"))
            }
            
            val updatedProfiles = _profiles.value.filter { it.id != profileId }
            _profiles.value = updatedProfiles
            
            if (_activeProfile.value?.id == profileId) {
                _activeProfile.value = updatedProfiles.firstOrNull()
            }
            
            saveProfiles()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun hasProfiles(): Boolean = _profiles.value.isNotEmpty()
    
    fun hasActiveProfile(): Boolean = _activeProfile.value != null
    
    // Debug function to test save/load
    fun testSaveLoad() {
        android.util.Log.d("ProfileRepository", "=== TESTING SAVE/LOAD ===")
        val testProfile = UserProfile(
            name = "Test Profile",
            aircraftType = AircraftType.PARAGLIDER,
            flightTemplateIds = listOf("essential", "thermal")
        )
        _profiles.value = listOf(testProfile)
        saveProfiles()
        
        // Clear and reload
        _profiles.value = emptyList()
        loadProfiles()
        android.util.Log.d("ProfileRepository", "After reload: ${_profiles.value.size} profiles")
    }
    
    private fun getDefaultTemplateIdsForAircraft(aircraftType: AircraftType): List<String> =
        ProfileAwareTemplates.getTemplatesForAircraft(aircraftType).map { it.id }
    
    private fun getDefaultCardConfigurations(aircraftType: AircraftType) = 
        aircraftType.defaultModes.associateWith { mode ->
            ProfileAwareTemplates.getCardConfigurationForMode(aircraftType, mode)
        }

    suspend fun saveProfileCardConfiguration(profileId: String, flightMode: com.example.xcpro.FlightMode, templateId: String): Result<Unit> {
        return try {
            android.util.Log.d("ProfileRepository", "Saving card configuration: profile=$profileId, mode=${flightMode.name}, template=$templateId")
            
            val profile = _profiles.value.find { it.id == profileId }
                ?: return Result.failure(Exception("Profile not found"))
            
            val updatedCardConfigs = profile.cardConfigurations.toMutableMap()
            val existingConfigList = updatedCardConfigs[flightMode]?.toMutableList() ?: mutableListOf()
            
            // Update or add template ID for this flight mode
            if (existingConfigList.isEmpty()) {
                existingConfigList.add(templateId)
            } else {
                existingConfigList[0] = templateId // Replace first template (primary)
            }
            updatedCardConfigs[flightMode] = existingConfigList
            
            val updatedProfile = profile.copy(cardConfigurations = updatedCardConfigs)
            
            val updatedProfiles = _profiles.value.map { 
                if (it.id == profileId) updatedProfile else it 
            }
            
            _profiles.value = updatedProfiles
            
            // Update active profile if this is the current active one
            if (_activeProfile.value?.id == profileId) {
                _activeProfile.value = updatedProfile
            }
            
            saveProfiles()
            
            android.util.Log.d("ProfileRepository", "✅ Card configuration saved successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ProfileRepository", "❌ Error saving card configuration", e)
            Result.failure(e)
        }
    }
    
    fun getProfileCardConfiguration(profileId: String, flightMode: com.example.xcpro.FlightMode): List<String> {
        android.util.Log.d("ProfileRepository", "Getting card configuration: profile=$profileId, mode=${flightMode.name}")
        
        val profile = _profiles.value.find { it.id == profileId }
        val cardConfig = profile?.cardConfigurations?.get(flightMode) ?: emptyList()
        
        android.util.Log.d("ProfileRepository", "Retrieved card config: $cardConfig")
        return cardConfig
    }
    
    fun getCurrentProfileCardConfiguration(flightMode: com.example.xcpro.FlightMode): List<String> {
        val activeProfile = _activeProfile.value
        return if (activeProfile != null) {
            getProfileCardConfiguration(activeProfile.id, flightMode)
        } else {
            android.util.Log.d("ProfileRepository", "No active profile, returning empty card configuration")
            emptyList()
        }
    }
}