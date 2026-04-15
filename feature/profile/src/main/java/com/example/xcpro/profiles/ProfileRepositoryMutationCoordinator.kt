package com.example.xcpro.profiles

import com.example.xcpro.core.time.Clock

internal class ProfileRepositoryMutationCoordinator(
    private val profileScopedDataCleaner: ProfileScopedDataCleaner,
    private val clock: Clock,
    private val profileIdGenerator: ProfileIdGenerator
) {
    suspend fun completeFirstLaunch(
        aircraftType: AircraftType,
        currentProfiles: List<UserProfile>,
        persistState: suspend (List<UserProfile>, String?) -> Unit,
        commitState: (List<UserProfile>, UserProfile?) -> Unit
    ): UserProfile {
        val canonicalCurrentProfiles = normalizeProfilesForPersistence(currentProfiles).profiles
        require(canonicalCurrentProfiles.isEmpty()) {
            "First-launch setup is only available before any profiles exist"
        }

        val defaultProfile = buildDefaultProfile(clock, aircraftType)
        persistState(listOf(defaultProfile), defaultProfile.id)
        commitState(listOf(defaultProfile), defaultProfile)
        return defaultProfile
    }

    suspend fun createProfile(
        request: ProfileCreationRequest,
        currentProfiles: List<UserProfile>,
        currentActiveProfileId: String?,
        persistState: suspend (List<UserProfile>, String?) -> Unit,
        commitState: (List<UserProfile>, UserProfile?) -> Unit
    ): UserProfile {
        val canonicalCurrentProfiles = normalizeProfilesForPersistence(currentProfiles).profiles
        val normalizedName = request.name.trim()
        require(normalizedName.isNotBlank()) { "Profile name cannot be blank" }
        val sourceProfile = request.copyFromProfile?.let { source ->
            canonicalCurrentProfiles.find { it.id == source.id }
                ?: error("Source profile for copy not found")
        }

        val createdAt = clock.nowWallMs()
        val newProfile = UserProfile(
            id = profileIdGenerator.newId(),
            name = normalizedName,
            aircraftType = request.aircraftType.canonicalForPersistence(),
            aircraftModel = request.aircraftModel ?: sourceProfile?.aircraftModel,
            description = request.description ?: sourceProfile?.description,
            preferences = sourceProfile?.preferences ?: ProfilePreferences(),
            createdAt = createdAt,
            lastUsed = createdAt,
            polar = sourceProfile?.polar ?: ProfilePolarSettings()
        )

        val updatedProfiles = canonicalCurrentProfiles + newProfile
        val onlyDefaultBeforeCreate = canonicalCurrentProfiles.size == 1 &&
            ProfileIdResolver.isCanonicalDefault(canonicalCurrentProfiles.first().id)
        val resolvedActive = when {
            canonicalCurrentProfiles.isEmpty() -> newProfile
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
        val canonicalCurrentProfiles = normalizeProfilesForPersistence(currentProfiles).profiles
        val canonicalProfile = profile.normalizedForPersistence()
        val existing = canonicalCurrentProfiles.find { it.id == canonicalProfile.id }
        val updatedProfiles = if (existing == null) {
            (canonicalCurrentProfiles + canonicalProfile).distinctBy { it.id }
        } else {
            canonicalCurrentProfiles
        }
        val resolvedActive = updatedProfiles.find { it.id == canonicalProfile.id } ?: canonicalProfile

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
        val canonicalCurrentProfiles = normalizeProfilesForPersistence(currentProfiles).profiles
        val canonicalUpdatedProfile = updatedProfile.normalizedForPersistence()
        val normalizedName = canonicalUpdatedProfile.name.trim()
        require(normalizedName.isNotBlank()) { "Profile name cannot be blank" }
        val index = canonicalCurrentProfiles.indexOfFirst { it.id == canonicalUpdatedProfile.id }
        if (index < 0) {
            error("Profile not found")
        }
        val existing = canonicalCurrentProfiles[index]
        val metadataOnlyUpdate = existing.copy(
            name = normalizedName,
            aircraftType = canonicalUpdatedProfile.aircraftType,
            aircraftModel = canonicalUpdatedProfile.aircraftModel,
            description = canonicalUpdatedProfile.description
        )
        val updatedProfiles = canonicalCurrentProfiles.toMutableList().apply {
            this[index] = metadataOnlyUpdate
        }
        val updatedActive = if (currentActiveProfileId == canonicalUpdatedProfile.id) {
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
        val canonicalCurrentProfiles = normalizeProfilesForPersistence(currentProfiles).profiles
        if (ProfileIdResolver.isCanonicalDefault(profileId)) {
            error("Cannot delete the default profile")
        }
        if (canonicalCurrentProfiles.size <= 1) {
            error("Cannot delete the last profile")
        }
        val remaining = canonicalCurrentProfiles.filter { it.id != profileId }
        if (remaining.size == canonicalCurrentProfiles.size) {
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
        } ?: buildDefaultProfile(clock)
        val normalizedProfiles = dedupeProfilesById(
            listOf(defaultProfile) + recoveredProfiles.filterNot {
                ProfileIdResolver.isCanonicalDefault(it.id)
            }
        )

        persistState(normalizedProfiles, defaultProfile.id)
        commitState(normalizedProfiles, defaultProfile)
    }
}
