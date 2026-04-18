package com.trust3.xcpro.profiles

import com.trust3.xcpro.core.time.Clock

internal class ProfileRepositoryBundleCoordinator(
    private val clock: Clock,
    private val aircraftProfileSectionIds: Set<String>,
    private val profileSettingsRestoreApplier: ProfileSettingsRestoreApplier,
    private val captureSettingsSnapshot: suspend (Set<String>, Set<String>) -> ProfileSettingsSnapshot,
    private val importProfiles: suspend (ProfileImportRequest) -> Result<ProfileImportResult>
) {
    fun previewBundle(
        json: String,
        existingProfiles: List<UserProfile>
    ): Result<ProfileBundlePreview> {
        return ProfileBundleCodec.parse(json).map { parsedBundle ->
            buildProfileBundlePreview(
                parsedBundle = parsedBundle,
                existingProfiles = existingProfiles
            )
        }
    }

    suspend fun exportBundle(
        availableProfiles: List<UserProfile>,
        activeProfileId: String?,
        selectedProfileIds: Set<String>? = null
    ): Result<ProfileBundleExportArtifact> {
        return runCatching {
            require(availableProfiles.isNotEmpty()) { "No profiles available to export." }
            val selectedProfiles = if (selectedProfileIds.isNullOrEmpty()) {
                availableProfiles
            } else {
                val requested = selectedProfileIds.map(ProfileIdResolver::canonicalOrDefault).toSet()
                availableProfiles.filter { profile -> requested.contains(profile.id) }
            }
            require(selectedProfiles.isNotEmpty()) { "Selected profiles were not found." }
            val selectedIds = selectedProfiles.mapTo(linkedSetOf()) { it.id }
            val scopedActiveId = activeProfileId?.takeIf { selectedIds.contains(it) }
            val exportedAtWallMs = clock.nowWallMs()
            val settingsSnapshot = captureSettingsSnapshot(
                selectedIds,
                aircraftProfileSectionIds
            )
            val bundleJson = ProfileBundleCodec.serialize(
                ProfileBundleDocument(
                    exportedAtWallMs = exportedAtWallMs,
                    activeProfileId = scopedActiveId,
                    profiles = selectedProfiles,
                    settings = settingsSnapshot
                )
            )
            ProfileBundleExportArtifact(
                bundleJson = bundleJson,
                exportedAtWallMs = exportedAtWallMs,
                suggestedFileName = AircraftProfileFileNames.buildExportFileName(
                    profile = selectedProfiles.singleOrNull(),
                    nowWallMs = exportedAtWallMs
                )
            )
        }
    }

    suspend fun importBundle(
        request: ProfileBundleImportRequest,
        onParseFailure: (Throwable) -> Unit = {}
    ): Result<ProfileBundleImportResult> {
        val parsed = ProfileBundleCodec.parse(request.json).getOrElse { error ->
            onParseFailure(error)
            return Result.failure(error)
        }
        val scopedSettingsSnapshot = when (request.settingsImportScope) {
            ProfileSettingsImportScope.PROFILES_ONLY -> ProfileSettingsSnapshot.empty()
            ProfileSettingsImportScope.PROFILE_SCOPED_SETTINGS ->
                parsed.settingsSnapshot.copy(
                    sections = parsed.settingsSnapshot.sections.filterKeys { sectionId ->
                        aircraftProfileSectionIds.contains(sectionId)
                    }
                )
            ProfileSettingsImportScope.FULL_BUNDLE -> parsed.settingsSnapshot
        }
        return importProfiles(
            ProfileImportRequest(
                profiles = parsed.profiles,
                keepCurrentActive = request.keepCurrentActive,
                nameCollisionPolicy = request.nameCollisionPolicy,
                preserveImportedPreferences = request.preserveImportedPreferences,
                preferredImportedActiveSourceId = parsed.activeProfileId
            )
        ).mapCatching { profileImportResult ->
            val settingsRestoreResult = if (
                profileImportResult.importedCount > 0 &&
                    scopedSettingsSnapshot.sections.isNotEmpty()
            ) {
                profileSettingsRestoreApplier.apply(
                    settingsSnapshot = scopedSettingsSnapshot,
                    importedProfileIdMap = profileImportResult.importedProfileIdMap
                )
            } else {
                ProfileSettingsRestoreResult()
            }
            if (request.strictSettingsRestore && settingsRestoreResult.failedSections.isNotEmpty()) {
                val failedSections = settingsRestoreResult.failedSections.keys
                    .sorted()
                    .joinToString(", ")
                error("Strict restore failed for sections: $failedSections")
            }
            ProfileBundleImportResult(
                profileImportResult = profileImportResult,
                settingsRestoreResult = settingsRestoreResult,
                sourceFormat = parsed.sourceFormat
            )
        }
    }
}
