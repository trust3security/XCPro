package com.trust3.xcpro.profiles

import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ProfileUseCase @Inject constructor(
    private val repository: ProfileRepository
) {
    val profiles: StateFlow<List<UserProfile>> = repository.profiles
    val activeProfile: StateFlow<UserProfile?> = repository.activeProfile
    val bootstrapComplete: StateFlow<Boolean> = repository.bootstrapComplete
    val bootstrapError: StateFlow<String?> = repository.bootstrapError

    suspend fun completeFirstLaunch(aircraftType: AircraftType): Result<UserProfile> =
        repository.completeFirstLaunch(aircraftType)

    suspend fun setActiveProfile(profile: UserProfile): Result<Unit> =
        repository.setActiveProfile(profile)

    suspend fun createProfile(request: ProfileCreationRequest): Result<UserProfile> =
        repository.createProfile(request)

    suspend fun importProfiles(request: ProfileImportRequest): Result<ProfileImportResult> =
        repository.importProfiles(request)

    suspend fun exportBundle(profileIds: Set<String>? = null): Result<ProfileBundleExportArtifact> =
        repository.exportBundle(profileIds)

    suspend fun previewBundle(json: String): Result<ProfileBundlePreview> =
        repository.previewBundle(json)

    suspend fun importBundle(request: ProfileBundleImportRequest): Result<ProfileBundleImportResult> =
        repository.importBundle(request)

    suspend fun updateProfile(profile: UserProfile): Result<Unit> =
        repository.updateProfile(profile)

    suspend fun deleteProfile(profileId: String): Result<Unit> =
        repository.deleteProfile(profileId)

    suspend fun recoverWithDefaultProfile(): Result<Unit> =
        repository.recoverWithDefaultProfile()
}
