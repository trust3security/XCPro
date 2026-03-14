package com.example.xcpro.profiles

import com.example.xcpro.core.time.Clock
import java.util.Locale

internal class ProfileRepositoryImportCoordinator(
    private val clock: Clock,
    private val profileIdGenerator: ProfileIdGenerator
) {
    suspend fun importProfiles(
        request: ProfileImportRequest,
        currentProfiles: List<UserProfile>,
        activeBeforeId: String?,
        persistState: suspend (List<UserProfile>, String?) -> Unit,
        commitState: (List<UserProfile>, UserProfile?) -> Unit
    ): ProfileImportResult {
        if (request.profiles.isEmpty()) {
            return ProfileImportResult(
                requestedCount = 0,
                importedCount = 0,
                skippedCount = 0,
                failures = emptyList(),
                activeProfileBefore = activeBeforeId,
                activeProfileAfter = activeBeforeId
            )
        }

        val failures = mutableListOf<ProfileImportFailure>()
        val knownIds = currentProfiles.map { it.id }.toMutableSet()
        val knownNames = currentProfiles
            .map { it.name.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .toMutableSet()
        val workingProfiles = currentProfiles.toMutableList()
        val importedProfiles = mutableListOf<UserProfile>()
        val importedIdMap = LinkedHashMap<String, String>()

        request.profiles.forEach { incoming ->
            val normalizedName = incoming.name.trim()
            if (normalizedName.isBlank()) {
                failures += ProfileImportFailure(
                    sourceName = incoming.name,
                    reason = ProfileImportFailureReason.INVALID_PROFILE,
                    detail = "Profile name cannot be blank."
                )
                return@forEach
            }

            val replaceTargetIndex = if (
                request.nameCollisionPolicy == ProfileNameCollisionPolicy.REPLACE_EXISTING
            ) {
                workingProfiles.indexOfFirst { existing ->
                    existing.name.trim().equals(normalizedName, ignoreCase = true)
                }
            } else {
                -1
            }
            val preferredIdRaw = incoming.id.trim()
            val preferredId = ProfileIdResolver.normalizeOrNull(preferredIdRaw)
                ?: preferredIdRaw
            val generatedId = if (replaceTargetIndex >= 0) {
                workingProfiles[replaceTargetIndex].id
            } else {
                generateUniqueId(knownIds, preferredId, profileIdGenerator)
            }
            val resolvedName = if (replaceTargetIndex >= 0) {
                normalizedName
            } else {
                resolveImportedName(
                    baseName = normalizedName,
                    knownNames = knownNames,
                    policy = request.nameCollisionPolicy
                )
            }

            val imported = incoming.copy(
                id = generatedId,
                name = resolvedName,
                preferences = if (request.preserveImportedPreferences) {
                    incoming.preferences
                } else {
                    ProfilePreferences()
                },
                isActive = false,
                createdAt = if (request.preserveImportedPreferences) {
                    incoming.createdAt
                } else {
                    clock.nowWallMs()
                },
                lastUsed = if (request.preserveImportedPreferences) {
                    incoming.lastUsed
                } else {
                    0L
                }
            )
            if (replaceTargetIndex >= 0) {
                workingProfiles[replaceTargetIndex] = imported
            } else {
                workingProfiles += imported
            }
            importedProfiles += imported
            if (preferredIdRaw.isNotBlank()) {
                importedIdMap.putIfAbsent(preferredIdRaw, generatedId)
            }
            if (preferredId.isNotBlank()) {
                importedIdMap.putIfAbsent(preferredId, generatedId)
            }
        }

        if (importedProfiles.isEmpty()) {
            return ProfileImportResult(
                requestedCount = request.profiles.size,
                importedCount = 0,
                skippedCount = request.profiles.size,
                failures = failures.toList(),
                activeProfileBefore = activeBeforeId,
                activeProfileAfter = activeBeforeId,
                importedProfileIdMap = emptyMap()
            )
        }

        val mergedProfiles = workingProfiles.toList()
        val preferredImportedActiveId = request.preferredImportedActiveSourceId
            ?.let { sourceId -> importedIdMap[sourceId] }
        val resolvedActive = resolveImportActiveProfile(
            profiles = mergedProfiles,
            currentActiveId = activeBeforeId,
            importedProfiles = importedProfiles,
            keepCurrentActive = request.keepCurrentActive,
            preferredImportedActiveId = preferredImportedActiveId
        )

        persistState(mergedProfiles, resolvedActive?.id)
        commitState(mergedProfiles, resolvedActive)

        return ProfileImportResult(
            requestedCount = request.profiles.size,
            importedCount = importedProfiles.size,
            skippedCount = request.profiles.size - importedProfiles.size,
            failures = failures.toList(),
            activeProfileBefore = activeBeforeId,
            activeProfileAfter = resolvedActive?.id,
            importedProfileIdMap = importedIdMap.toMap()
        )
    }
}
