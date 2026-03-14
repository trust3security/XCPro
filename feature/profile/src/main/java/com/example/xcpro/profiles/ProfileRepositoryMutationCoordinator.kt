package com.example.xcpro.profiles

import com.example.xcpro.core.time.Clock

internal class ProfileRepositoryMutationCoordinator(
    private val profileScopedDataCleaner: ProfileScopedDataCleaner,
    private val clock: Clock,
    private val profileIdGenerator: ProfileIdGenerator
) {
    suspend fun createProfile(
        request: ProfileCreationRequest,
        currentProfiles: List<UserProfile>,
        currentActiveProfileId: String?,
        persistState: suspend (List<UserProfile>, String?) -> Unit,
        commitState: (List<UserProfile>, UserProfile?) -> Unit
    ): UserProfile {
        val normalizedName = request.name.trim()
        require(normalizedName.isNotBlank()) { "Profile name cannot be blank" }
        val sourceProfile = request.copyFromProfile?.let { source ->
            currentProfiles.find { it.id == source.id } ?: error("Source profile for copy not found")
        }

        val createdAt = clock.nowWallMs()
        val newProfile = UserProfile(
            id = profileIdGenerator.newId(),
            name = normalizedName,
            aircraftType = request.aircraftType,
            aircraftModel = request.aircraftModel ?: sourceProfile?.aircraftModel,
            description = request.description ?: sourceProfile?.description,
            preferences = sourceProfile?.preferences ?: ProfilePreferences(),
            createdAt = createdAt,
            lastUsed = createdAt,
            polar = sourceProfile?.polar ?: ProfilePolarSettings()
        )

        val updatedProfiles = currentProfiles + newProfile
        val onlyDefaultBeforeCreate = currentProfiles.size == 1 &&
            ProfileIdResolver.isCanonicalDefault(currentProfiles.first().id)
        val resolvedActive = when {
            currentProfiles.isEmpty() -> newProfile
            onlyDefaultBeforeCreate -> newProfile
            else -> updatedProfiles.find { it.id == currentActiveProfileId } ?: fallbackActiveProfile(updatedProfiles)
        }

        persistState(updatedProfiles, resolvedActive?.id)
        commitState(updatedProfiles, resolvedActive)
        return newProfile
    }

    suspend fun setActiveProfile(
        profile: UserProfile,
        currentProfiles: List<UserProfile>,
        persistState: suspend (List<UserProfile>, String?) -> Unit,
        commitState: (List<UserProfile>, UserProfile?) -> Unit
    ) {
        val existing = currentProfiles.find { it.id == profile.id }
        val updatedProfiles = if (existing == null) {
            (currentProfiles + profile).distinctBy { it.id }
        } else {
            currentProfiles
        }
        val resolvedActive = updatedProfiles.find { it.id == profile.id } ?: profile

        persistState(updatedProfiles, resolvedActive.id)
        commitState(updatedProfiles, resolvedActive)
    }

    suspend fun updateProfile(
        updatedProfile: UserProfile,
        currentProfiles: List<UserProfile>,
        currentActiveProfileId: String?,
        persistState: suspend (List<UserProfile>, String?) -> Unit,
        commitState: (List<UserProfile>, UserProfile?) -> Unit
    ) {
        val normalizedName = updatedProfile.name.trim()
        require(normalizedName.isNotBlank()) { "Profile name cannot be blank" }
        val index = currentProfiles.indexOfFirst { it.id == updatedProfile.id }
        if (index < 0) {
            error("Profile not found")
        }
        val existing = currentProfiles[index]
        val metadataOnlyUpdate = existing.copy(
            name = normalizedName,
            aircraftType = updatedProfile.aircraftType,
            aircraftModel = updatedProfile.aircraftModel,
            description = updatedProfile.description
        )
        val updatedProfiles = currentProfiles.toMutableList().apply { this[index] = metadataOnlyUpdate }
        val updatedActive = if (currentActiveProfileId == updatedProfile.id) {
            metadataOnlyUpdate
        } else {
            updatedProfiles.find { it.id == currentActiveProfileId }
        }

        persistState(updatedProfiles, updatedActive?.id)
        commitState(updatedProfiles, updatedActive)
    }

    suspend fun deleteProfile(
        profileId: String,
        currentProfiles: List<UserProfile>,
        currentActiveProfileId: String?,
        persistState: suspend (List<UserProfile>, String?) -> Unit,
        commitState: (List<UserProfile>, UserProfile?) -> Unit
    ) {
        if (ProfileIdResolver.isCanonicalDefault(profileId)) {
            error("Cannot delete the default profile")
        }
        if (currentProfiles.size <= 1) {
            error("Cannot delete the last profile")
        }
        val remaining = currentProfiles.filter { it.id != profileId }
        if (remaining.size == currentProfiles.size) {
            error("Profile not found")
        }

        val nextActive = if (currentActiveProfileId == profileId) {
            remaining.firstOrNull()
        } else {
            remaining.find { it.id == currentActiveProfileId } ?: remaining.firstOrNull()
        }

        profileScopedDataCleaner.clearProfileData(profileId)
        persistState(remaining, nextActive?.id)
        commitState(remaining, nextActive)
    }

    suspend fun recoverWithDefaultProfile(
        currentProfiles: List<UserProfile>,
        persistState: suspend (List<UserProfile>, String?) -> Unit,
        commitState: (List<UserProfile>, UserProfile?) -> Unit
    ) {
        val recoveredProfiles = ensureBootstrapProfile(currentProfiles, clock).profiles
        val defaultProfile = recoveredProfiles.firstOrNull {
            ProfileIdResolver.isCanonicalDefault(it.id)
        } ?: UserProfile(
            id = ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
            name = "Default",
            aircraftType = AircraftType.PARAGLIDER,
            createdAt = clock.nowWallMs(),
            lastUsed = clock.nowWallMs()
        )
        val normalizedProfiles = dedupeProfilesById(
            listOf(defaultProfile) + recoveredProfiles.filterNot {
                ProfileIdResolver.isCanonicalDefault(it.id)
            }
        )

        persistState(normalizedProfiles, defaultProfile.id)
        commitState(normalizedProfiles, defaultProfile)
    }
}
