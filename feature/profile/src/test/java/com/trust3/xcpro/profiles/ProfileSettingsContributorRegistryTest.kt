package com.trust3.xcpro.profiles

import com.trust3.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.trust3.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSettingsContributorRegistryTest {

    private class FakeCaptureContributor(
        override val sectionIds: Set<String>
    ) : ProfileSettingsCaptureContributor {
        override suspend fun captureSection(
            sectionId: String,
            profileIds: Set<String>
        ): JsonElement = JsonPrimitive(sectionId)
    }

    private class FakeApplyContributor(
        override val sectionIds: Set<String>
    ) : ProfileSettingsApplyContributor {
        override suspend fun applySection(
            sectionId: String,
            payload: JsonElement,
            importedProfileIdMap: Map<String, String>
        ) = Unit
    }

    @Test
    fun orderedCaptureSectionIds_usesCanonicalOrder_notContributorRegistrationOrder() {
        val firstHalf = ProfileSettingsSectionSets.CAPTURED_SECTION_ORDER
            .take(ProfileSettingsSectionSets.CAPTURED_SECTION_ORDER.size / 2)
            .toSet()
        val secondHalf = ProfileSettingsSectionSets.CAPTURED_SECTION_ORDER
            .drop(ProfileSettingsSectionSets.CAPTURED_SECTION_ORDER.size / 2)
            .toSet()
        val registry = ProfileSettingsContributorRegistry(
            captureContributors = linkedSetOf(
                FakeCaptureContributor(secondHalf),
                FakeCaptureContributor(firstHalf)
            ),
            applyContributors = linkedSetOf(
                FakeApplyContributor(secondHalf),
                FakeApplyContributor(firstHalf)
            )
        )

        val requestedSectionIds = setOf(
            ProfileSettingsSectionIds.WEATHER_OVERLAY_PREFERENCES,
            ProfileSettingsSectionIds.CARD_PREFERENCES,
            ProfileSettingsSectionIds.QNH_PREFERENCES
        )

        assertEquals(
            listOf(
                ProfileSettingsSectionIds.CARD_PREFERENCES,
                ProfileSettingsSectionIds.QNH_PREFERENCES,
                ProfileSettingsSectionIds.WEATHER_OVERLAY_PREFERENCES
            ),
            registry.orderedCaptureSectionIds(requestedSectionIds)
        )
    }

    @Test
    fun init_failsFastWhenDuplicateCaptureOwnerExists() {
        val error = runCatching {
            ProfileSettingsContributorRegistry(
                captureContributors = setOf(
                    FakeCaptureContributor(ProfileSettingsSectionSets.CAPTURED_SECTION_IDS),
                    FakeCaptureContributor(setOf(ProfileSettingsSectionIds.CARD_PREFERENCES))
                ),
                applyContributors = setOf(
                    FakeApplyContributor(ProfileSettingsSectionSets.CAPTURED_SECTION_IDS)
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(
            "Duplicate capture contributors for section '${ProfileSettingsSectionIds.CARD_PREFERENCES}'.",
            error?.message
        )
    }
}
