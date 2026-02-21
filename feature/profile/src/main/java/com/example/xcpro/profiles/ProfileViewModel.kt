package com.example.xcpro.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profiles: List<UserProfile> = emptyList(),
    val activeProfile: UserProfile? = null,
    val isHydrated: Boolean = false,
    val bootstrapError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateDialog: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val useCase: ProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                useCase.profiles,
                useCase.activeProfile,
                useCase.bootstrapComplete,
                useCase.bootstrapError
            ) { profiles, activeProfile, bootstrapComplete, bootstrapError ->
                ProfileBootstrapSnapshot(
                    profiles = profiles,
                    activeProfile = activeProfile,
                    bootstrapComplete = bootstrapComplete,
                    bootstrapError = bootstrapError
                )
            }.collect { snapshot ->
                _uiState.value = _uiState.value.copy(
                    profiles = snapshot.profiles,
                    activeProfile = snapshot.activeProfile,
                    isHydrated = snapshot.bootstrapComplete,
                    bootstrapError = snapshot.bootstrapError
                )
            }
        }
    }

    fun selectProfile(profile: UserProfile) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            useCase.setActiveProfile(profile)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to select profile: ${error.message}"
                    )
                }
        }
    }

    fun createProfile(request: ProfileCreationRequest) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            useCase.createProfile(request)
                .onSuccess { newProfile ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        showCreateDialog = false
                    )
                    selectProfile(newProfile)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to create profile: ${error.message}"
                    )
                }
        }
    }

    fun updateProfile(profile: UserProfile) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            useCase.updateProfile(profile)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to update profile: ${error.message}"
                    )
                }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            useCase.deleteProfile(profileId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to delete profile: ${error.message}"
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

    fun needsProfileSelection(): Boolean =
        _uiState.value.isHydrated &&
            _uiState.value.profiles.isNotEmpty() &&
            _uiState.value.activeProfile == null

    private data class ProfileBootstrapSnapshot(
        val profiles: List<UserProfile>,
        val activeProfile: UserProfile?,
        val bootstrapComplete: Boolean,
        val bootstrapError: String?
    )
}

