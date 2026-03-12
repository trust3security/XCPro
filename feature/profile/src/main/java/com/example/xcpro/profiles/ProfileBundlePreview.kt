package com.example.xcpro.profiles

import java.util.Locale

data class ProfileBundlePreview(
    val profiles: List<ProfileBundlePreviewProfile>,
    val preferredActiveProfileName: String?,
    val sourceFormat: ProfileBundleSourceFormat,
    val schemaVersion: String,
    val exportedAtWallMs: Long?,
    val exportedAtLabel: String?,
    val aircraftProfileSectionIds: Set<String>,
    val ignoredGlobalSectionIds: Set<String>,
    val unknownSectionIds: Set<String>
) {
    val hasRestorableAircraftSettings: Boolean
        get() = aircraftProfileSectionIds.isNotEmpty()
}

data class ProfileBundlePreviewProfile(
    val sourceId: String,
    val name: String,
    val aircraftType: AircraftType,
    val aircraftModel: String?,
    val matchesExistingProfileName: Boolean
)

internal fun buildProfileBundlePreview(
    parsedBundle: ParsedProfileBundle,
    existingProfiles: List<UserProfile>
): ProfileBundlePreview {
    val existingNames = existingProfiles
        .map { it.name.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .toSet()
    val sectionIds = parsedBundle.settingsSnapshot.sections.keys
    val aircraftProfileSectionIds = sectionIds.intersect(
        ProfileSettingsSectionSets.AIRCRAFT_PROFILE_SECTION_IDS
    )
    val ignoredGlobalSectionIds = sectionIds.intersect(
        ProfileSettingsSectionSets.GLOBAL_APP_SECTION_IDS
    )
    val unknownSectionIds = sectionIds - ProfileSettingsSectionSets.CAPTURED_SECTION_IDS

    return ProfileBundlePreview(
        profiles = parsedBundle.profiles.map { profile ->
            ProfileBundlePreviewProfile(
                sourceId = profile.id,
                name = profile.name,
                aircraftType = profile.aircraftType,
                aircraftModel = profile.aircraftModel,
                matchesExistingProfileName = profile.name.trim()
                    .lowercase(Locale.ROOT)
                    .let(existingNames::contains)
            )
        },
        preferredActiveProfileName = parsedBundle.activeProfileId?.let { activeId ->
            parsedBundle.profiles.firstOrNull { it.id == activeId }?.getDisplayName()
        },
        sourceFormat = parsedBundle.sourceFormat,
        schemaVersion = parsedBundle.schemaVersion,
        exportedAtWallMs = parsedBundle.exportedAtWallMs,
        exportedAtLabel = parsedBundle.exportedAtLabel,
        aircraftProfileSectionIds = aircraftProfileSectionIds,
        ignoredGlobalSectionIds = ignoredGlobalSectionIds,
        unknownSectionIds = unknownSectionIds
    )
}
