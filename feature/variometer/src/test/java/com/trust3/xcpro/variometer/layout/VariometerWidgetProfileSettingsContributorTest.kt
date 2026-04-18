package com.trust3.xcpro.variometer.layout

import com.trust3.xcpro.core.common.geometry.OffsetPx
import com.trust3.xcpro.core.common.profiles.ProfileSettingsProfileIds
import com.trust3.xcpro.core.common.profiles.ProfileSettingsSectionContract
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class VariometerWidgetProfileSettingsContributorTest {

    private val gson = Gson()

    @Test
    fun captureSection_serializesProfileLayouts() = runTest {
        val repository = mock<VariometerWidgetRepository>()
        whenever(repository.load("default-profile", OffsetPx.Zero, 0f)).thenReturn(
            VariometerLayout(
                offset = OffsetPx(10f, 20f),
                sizePx = 150f,
                hasPersistedOffset = true,
                hasPersistedSize = false
            )
        )
        val contributor = VariometerWidgetProfileSettingsContributor(repository)

        val payload = contributor.captureSection(
            sectionId = ProfileSettingsSectionContract.VARIOMETER_WIDGET_LAYOUT,
            profileIds = setOf("default-profile")
        )

        assertNotNull(payload)
        val defaultLayout = payload!!
            .asJsonObject
            .getAsJsonObject("layoutsByProfile")
            .getAsJsonObject("default-profile")
        assertEquals(10f, defaultLayout.getAsJsonObject("offset").get("x").asFloat)
        assertEquals(20f, defaultLayout.getAsJsonObject("offset").get("y").asFloat)
        assertEquals(150f, defaultLayout.get("sizePx").asFloat)
        assertEquals(true, defaultLayout.get("hasPersistedOffset").asBoolean)
        assertEquals(false, defaultLayout.get("hasPersistedSize").asBoolean)
    }

    @Test
    fun applySection_mapsImportedProfileIds_forProfileSnapshots() = runTest {
        val repository = mock<VariometerWidgetRepository>()
        val contributor = VariometerWidgetProfileSettingsContributor(repository)
        val payload = gson.toJsonTree(
            mapOf(
                "layoutsByProfile" to mapOf(
                    "source-a" to mapOf(
                        "offset" to mapOf("x" to 40f, "y" to 50f),
                        "sizePx" to 160f,
                        "hasPersistedOffset" to true,
                        "hasPersistedSize" to true
                    )
                )
            )
        )

        contributor.applySection(
            sectionId = ProfileSettingsSectionContract.VARIOMETER_WIDGET_LAYOUT,
            payload = payload,
            importedProfileIdMap = mapOf("source-a" to "target-a")
        )

        verify(repository).saveOffset(
            profileId = "target-a",
            offset = OffsetPx(40f, 50f)
        )
        verify(repository).saveSize(profileId = "target-a", sizePx = 160f)
    }

    @Test
    fun applySection_supportsLegacyPayload_forDefaultProfileFallback() = runTest {
        val repository = mock<VariometerWidgetRepository>()
        val contributor = VariometerWidgetProfileSettingsContributor(repository)
        val payload = gson.toJsonTree(
            mapOf(
                "offset" to mapOf("x" to 12f, "y" to 18f),
                "sizePx" to 144f,
                "hasPersistedOffset" to true,
                "hasPersistedSize" to true
            )
        )

        contributor.applySection(
            sectionId = ProfileSettingsSectionContract.VARIOMETER_WIDGET_LAYOUT,
            payload = payload,
            importedProfileIdMap = emptyMap()
        )

        verify(repository).saveOffset(
            profileId = ProfileSettingsProfileIds.CANONICAL_DEFAULT_PROFILE_ID,
            offset = OffsetPx(12f, 18f)
        )
        verify(repository).saveSize(
            profileId = ProfileSettingsProfileIds.CANONICAL_DEFAULT_PROFILE_ID,
            sizePx = 144f
        )
    }
}
