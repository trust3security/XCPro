package com.trust3.xcpro.profiles

import com.google.gson.JsonElement
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppProfileSettingsSnapshotProvider @Inject constructor(
    private val contributorRegistry: Lazy<ProfileSettingsContributorRegistry>
) : ProfileSettingsSnapshotProvider {

    override suspend fun buildSnapshot(
        profileIds: Set<String>,
        sectionIds: Set<String>
    ): ProfileSettingsSnapshot {
        val normalizedProfileIds = profileIds
            .map(ProfileIdResolver::canonicalOrDefault)
            .toSortedSet()
        val orderedRequestedSectionIds = contributorRegistry.get().orderedCaptureSectionIds(sectionIds)

        if (orderedRequestedSectionIds.isEmpty()) {
            return ProfileSettingsSnapshot.empty()
        }

        val sections = linkedMapOf<String, JsonElement>()
        val registry = contributorRegistry.get()
        for (sectionId in orderedRequestedSectionIds) {
            registry.captureContributor(sectionId)
                .captureSection(sectionId, normalizedProfileIds)
                ?.let { payload -> sections[sectionId] = payload }
        }
        return ProfileSettingsSnapshot(sections = sections.toMap())
    }
}
