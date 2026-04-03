package com.example.xcpro.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
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
    val isFirstLaunchSetupRequired: Boolean = false,
    val bootstrapError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val importResult: ProfileImportResult? = null,
    val bundleImportResult: ProfileBundleImportResult? = null
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
                    bootstrapError = bootstrapError,
                    isFirstLaunchSetupRequired = bootstrapComplete &&
                        profiles.isEmpty() &&
                        activeProfile == null &&
                        bootstrapError.isNullOrBlank()
                )
            }.collect { snapshot ->
                _uiState.value = _uiState.value.copy(
                    profiles = snapshot.profiles,
                    activeProfile = snapshot.activeProfile,
                    isHydrated = snapshot.bootstrapComplete,
                    isFirstLaunchSetupRequired = snapshot.isFirstLaunchSetupRequired,
                    bootstrapError = snapshot.bootstrapError
                )
            }
        }
    }

    fun completeFirstLaunch(aircraftType: AircraftType) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                importResult = null,
                bundleImportResult = null
            )
            useCase.completeFirstLaunch(aircraftType)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to complete first launch: ${error.message}"
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
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                importResult = null,
                bundleImportResult = null
            )
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

    fun duplicateProfile(profile: UserProfile) {
        createProfile(
            ProfileCreationRequest(
                name = buildDuplicateProfileName(profile.name),
                aircraftType = profile.aircraftType,
                aircraftModel = profile.aircraftModel,
                description = profile.description,
                copyFromProfile = profile
            )
        )
    }

    fun importProfiles(
        profiles: List<UserProfile>,
        keepCurrentActive: Boolean = true,
        nameCollisionPolicy: ProfileNameCollisionPolicy = ProfileNameCollisionPolicy.KEEP_BOTH_SUFFIX
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                importResult = null,
                bundleImportResult = null
            )
            useCase.importProfiles(
                ProfileImportRequest(
                    profiles = profiles,
                    keepCurrentActive = keepCurrentActive,
                    nameCollisionPolicy = nameCollisionPolicy
                )
            ).onSuccess { result ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    importResult = result,
                    bundleImportResult = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to import profiles: ${error.message}"
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

    fun recoverWithDefaultProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            useCase.recoverWithDefaultProfile()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to recover with default profile: ${error.message}"
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

    fun clearImportResult() {
        _uiState.value = _uiState.value.copy(importResult = null)
    }

    fun clearBundleImportResult() {
        _uiState.value = _uiState.value.copy(
            importResult = null,
            bundleImportResult = null
        )
    }

    fun importBundle(
        json: String,
        keepCurrentActive: Boolean = true,
        nameCollisionPolicy: ProfileNameCollisionPolicy = ProfileNameCollisionPolicy.KEEP_BOTH_SUFFIX,
        settingsImportScope: ProfileSettingsImportScope =
            ProfileSettingsImportScope.PROFILE_SCOPED_SETTINGS,
        strictSettingsRestore: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                importResult = null,
                bundleImportResult = null
            )
            useCase.importBundle(
                ProfileBundleImportRequest(
                    json = json,
                    keepCurrentActive = keepCurrentActive,
                    nameCollisionPolicy = nameCollisionPolicy,
                    settingsImportScope = settingsImportScope,
                    strictSettingsRestore = strictSettingsRestore
                )
            ).onSuccess { bundleResult ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    importResult = bundleResult.profileImportResult,
                    bundleImportResult = bundleResult,
                    error = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to import bundle: ${error.message}"
                )
            }
        }
    }

    suspend fun previewBundle(json: String): Result<ProfileBundlePreview> {
        return useCase.previewBundle(json)
    }

    suspend fun exportBundle(profileIds: Set<String>? = null): Result<ProfileBundleExportArtifact> {
        return useCase.exportBundle(profileIds)
    }

    fun needsProfileSelection(): Boolean =
        _uiState.value.isHydrated &&
            _uiState.value.profiles.isNotEmpty() &&
            _uiState.value.activeProfile == null

    private data class ProfileBootstrapSnapshot(
        val profiles: List<UserProfile>,
        val activeProfile: UserProfile?,
        val bootstrapComplete: Boolean,
        val bootstrapError: String?,
        val isFirstLaunchSetupRequired: Boolean
    )

    private fun buildDuplicateProfileName(baseName: String): String {
        val existingNames = _uiState.value.profiles
            .map { it.name.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .toSet()
        val normalizedBase = baseName.trim().ifBlank { "Profile" }
        var suffix = 1
        while (true) {
            val candidate = if (suffix == 1) {
                "$normalizedBase Copy"
            } else {
                "$normalizedBase Copy $suffix"
            }
            if (!existingNames.contains(candidate.lowercase(Locale.ROOT))) {
                return candidate
            }
            suffix++
        }
    }
}

