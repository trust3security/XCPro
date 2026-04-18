package com.trust3.xcpro.ogn

import com.trust3.xcpro.core.common.profiles.ProfileSettingsSectionContract
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OgnTrailSelectionProfileSettingsContributorTest {

    @Test
    fun captureSection_serializesSelectedAircraftKeysInSortedOrder() = runTest {
        val repository = mock<OgnTrailSelectionPreferencesRepository>()
        whenever(repository.selectedAircraftKeysFlow).thenReturn(
            flowOf(setOf("z-aircraft", "a-aircraft"))
        )
        val contributor = OgnTrailSelectionProfileSettingsContributor(repository)

        val payload = contributor.captureSection(
            sectionId = ProfileSettingsSectionContract.OGN_TRAIL_SELECTION_PREFERENCES,
            profileIds = emptySet()
        )

        assertNotNull(payload)
        val keys = payload!!
            .asJsonObject
            .getAsJsonArray("selectedAircraftKeys")
            .map { it.asString }
        assertEquals(listOf("a-aircraft", "z-aircraft"), keys)
    }

    @Test
    fun applySection_clearsThenRestoresSelectedAircraftKeys() = runTest {
        val repository = mock<OgnTrailSelectionPreferencesRepository>()
        val contributor = OgnTrailSelectionProfileSettingsContributor(repository)
        val payload = com.google.gson.JsonParser.parseString(
            """
            {
              "selectedAircraftKeys": [
                "aircraft-b",
                "aircraft-a"
              ]
            }
            """.trimIndent()
        )

        contributor.applySection(
            sectionId = ProfileSettingsSectionContract.OGN_TRAIL_SELECTION_PREFERENCES,
            payload = payload,
            importedProfileIdMap = emptyMap()
        )

        verify(repository).clearSelectedAircraft()
        verify(repository).setAircraftSelected("aircraft-b", selected = true)
        verify(repository).setAircraftSelected("aircraft-a", selected = true)
    }
}
