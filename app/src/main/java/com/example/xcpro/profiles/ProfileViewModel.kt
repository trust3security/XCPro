package com.example.xcpro.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profiles: List<UserProfile> = emptyList(),
    val activeProfile: UserProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateDialog: Boolean = false
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = ProfileRepository.getInstance(application)
    
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            repository.profiles.collect { profiles ->
                _uiState.value = _uiState.value.copy(profiles = profiles)
            }
        }
        
        viewModelScope.launch {
            repository.activeProfile.collect { activeProfile ->
                _uiState.value = _uiState.value.copy(activeProfile = activeProfile)
            }
        }
    }
    
    fun selectProfile(profile: UserProfile) {
        android.util.Log.d("ProfileViewModel", "=== SELECTING PROFILE ===")
        android.util.Log.d("ProfileViewModel", "Requested profile: ${profile.name} (ID: ${profile.id})")
        android.util.Log.d("ProfileViewModel", "Current active profile: ${_uiState.value.activeProfile?.name} (ID: ${_uiState.value.activeProfile?.id})")
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            repository.setActiveProfile(profile)
                .onSuccess {
                    android.util.Log.d("ProfileViewModel", "✅ Profile selection SUCCESS")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { exception ->
                    android.util.Log.e("ProfileViewModel", "❌ Profile selection FAILED: ${exception.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to select profile: ${exception.message}"
                    )
                }
        }
    }
    
    fun createProfile(request: ProfileCreationRequest) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            repository.createProfile(request)
                .onSuccess { newProfile ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        showCreateDialog = false
                    )
                    // Automatically select the new profile
                    selectProfile(newProfile)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to create profile: ${exception.message}"
                    )
                }
        }
    }
    
    fun updateProfile(profile: UserProfile) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            repository.updateProfile(profile)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to update profile: ${exception.message}"
                    )
                }
        }
    }
    
    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            repository.deleteProfile(profileId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to delete profile: ${exception.message}"
                    )
                }
        }
    }
    
    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }
    
    fun hideCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun hasProfiles(): Boolean = repository.hasProfiles()
    
    fun hasActiveProfile(): Boolean = repository.hasActiveProfile()
    
    fun needsProfileSelection(): Boolean = hasProfiles() && !hasActiveProfile()
    
    fun saveProfileCardConfiguration(profileId: String, flightMode: com.example.xcpro.FlightMode, templateId: String) {
        viewModelScope.launch {
            repository.saveProfileCardConfiguration(profileId, flightMode, templateId)
                .onSuccess {
                    android.util.Log.d("ProfileViewModel", "✅ Card configuration saved for profile $profileId")
                }
                .onFailure { exception ->
                    android.util.Log.e("ProfileViewModel", "❌ Failed to save card configuration: ${exception.message}")
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to save card configuration: ${exception.message}"
                    )
                }
        }
    }
    
    fun getCurrentProfileCardConfiguration(flightMode: com.example.xcpro.FlightMode): List<String> {
        return repository.getCurrentProfileCardConfiguration(flightMode)
    }
}