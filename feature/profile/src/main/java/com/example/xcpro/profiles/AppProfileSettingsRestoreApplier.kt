package com.example.xcpro.profiles

import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppProfileSettingsRestoreApplier @Inject constructor(
    private val contributorRegistry: Lazy<ProfileSettingsContributorRegistry>
) : ProfileSettingsRestoreApplier {

    override suspend fun apply(
        settingsSnapshot: ProfileSettingsSnapshot,
        importedProfileIdMap: Map<String, String>
    ): ProfileSettingsRestoreResult {
        if (settingsSnapshot.sections.isEmpty()) {
            return ProfileSettingsRestoreResult()
        }
        val applied = linkedSetOf<String>()
        val failed = linkedMapOf<String, String>()
        val registry = contributorRegistry.get()

        suspend fun applyCapturedSection(sectionId: String) {
            val payload = settingsSnapshot.sections[sectionId] ?: return
            runCatching {
                registry.applyContributor(sectionId).applySection(
                    sectionId = sectionId,
                    payload = payload,
                    importedProfileIdMap = importedProfileIdMap
                )
            }
                .onSuccess { applied.add(sectionId) }
                .onFailure { error ->
                    failed[sectionId] = error.message ?: error.javaClass.simpleName
                }
        }

        for (sectionId in registry.orderedSnapshotSectionIds(settingsSnapshot)) {
            applyCapturedSection(sectionId)
        }

        return ProfileSettingsRestoreResult(
            appliedSections = applied.toSet(),
            failedSections = failed.toMap()
        )
    }
}
