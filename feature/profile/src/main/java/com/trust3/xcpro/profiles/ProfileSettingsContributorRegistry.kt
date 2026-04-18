package com.trust3.xcpro.profiles

import com.trust3.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.trust3.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.trust3.xcpro.core.common.profiles.ProfileSettingsSectionContract
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileSettingsContributorRegistry @Inject constructor(
    captureContributors: Set<@JvmSuppressWildcards ProfileSettingsCaptureContributor>,
    applyContributors: Set<@JvmSuppressWildcards ProfileSettingsApplyContributor>
) {
    private val canonicalSectionOrder = ProfileSettingsSectionContract.CAPTURED_SECTION_ORDER
    private val canonicalSectionIds = ProfileSettingsSectionContract.CAPTURED_SECTION_IDS

    private val captureOwnersBySectionId = buildOwnerIndex(
        ownerKind = "capture",
        contributors = captureContributors,
        sectionIdsOf = { contributor -> contributor.sectionIds }
    )

    private val applyOwnersBySectionId = buildOwnerIndex(
        ownerKind = "apply",
        contributors = applyContributors,
        sectionIdsOf = { contributor -> contributor.sectionIds }
    )

    fun orderedCaptureSectionIds(requestedSectionIds: Set<String>): List<String> {
        return canonicalSectionOrder.filter { sectionId -> requestedSectionIds.contains(sectionId) }
    }

    fun orderedSnapshotSectionIds(settingsSnapshot: ProfileSettingsSnapshot): List<String> {
        return canonicalSectionOrder.filter { sectionId ->
            settingsSnapshot.sections.containsKey(sectionId)
        }
    }

    fun captureContributor(sectionId: String): ProfileSettingsCaptureContributor {
        return captureOwnersBySectionId[sectionId]
            ?: error("Missing capture contributor for section '$sectionId'.")
    }

    fun applyContributor(sectionId: String): ProfileSettingsApplyContributor {
        return applyOwnersBySectionId[sectionId]
            ?: error("Missing apply contributor for section '$sectionId'.")
    }

    private fun <T> buildOwnerIndex(
        ownerKind: String,
        contributors: Set<T>,
        sectionIdsOf: (T) -> Set<String>
    ): Map<String, T> {
        contributors.forEach { contributor ->
            val unsupportedSectionIds = sectionIdsOf(contributor) - canonicalSectionIds
            require(unsupportedSectionIds.isEmpty()) {
                "Unsupported $ownerKind contributor section IDs: " +
                    unsupportedSectionIds.sorted().joinToString(", ")
            }
        }

        val ownersBySectionId = linkedMapOf<String, T>()
        canonicalSectionOrder.forEach { sectionId ->
            val owners = contributors.filter { contributor ->
                sectionIdsOf(contributor).contains(sectionId)
            }
            require(owners.isNotEmpty()) {
                "Missing $ownerKind contributor for section '$sectionId'."
            }
            require(owners.size == 1) {
                "Duplicate $ownerKind contributors for section '$sectionId'."
            }
            ownersBySectionId[sectionId] = owners.single()
        }
        return ownersBySectionId.toMap()
    }
}
