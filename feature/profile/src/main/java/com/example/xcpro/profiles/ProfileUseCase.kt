package com.example.xcpro.profiles

import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ProfileUseCase @Inject constructor(
    private val repository: ProfileRepository
) {
    val profiles: StateFlow<List<UserProfile>> = repository.profiles
    val activeProfile: StateFlow<UserProfile?> = repository.activeProfile
    val bootstrapComplete: StateFlow<Boolean> = repository.bootstrapComplete
    val bootstrapError: StateFlow<String?> = repository.bootstrapError

    suspend fun setActiveProfile(profile: UserProfile): Result<Unit> =
        repository.setActiveProfile(profile)

    suspend fun createProfile(request: ProfileCreationRequest): Result<UserProfile> =
        repository.createProfile(request)

    suspend fun updateProfile(profile: UserProfile): Result<Unit> =
        repository.updateProfile(profile)

    suspend fun deleteProfile(profileId: String): Result<Unit> =
        repository.deleteProfile(profileId)
}
