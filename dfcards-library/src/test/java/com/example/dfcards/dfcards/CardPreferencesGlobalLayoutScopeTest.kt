package com.example.dfcards

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CardPreferencesGlobalLayoutScopeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = CardPreferences(context)

    @Test
    fun clearProfile_keepsGlobalLayoutKeysWhileDroppingProfileScopedCardState() = runTest {
        val profileId = "layout-scope-profile"
        preferences.clearProfile(profileId)
        preferences.setCardsAcrossPortrait(6)
        preferences.setCardsAnchorPortrait(CardPreferences.CardAnchor.BOTTOM)
        preferences.saveProfileTemplateCards(profileId, "template-1", listOf("agl", "vario"))
        preferences.saveProfileFlightModeVisibility(profileId, "THERMAL", false)

        preferences.clearProfile(profileId)

        assertEquals(6, preferences.getCardsAcrossPortrait().first())
        assertEquals(CardPreferences.CardAnchor.BOTTOM, preferences.getCardsAnchorPortrait().first())
        assertFalse(preferences.getAllProfileTemplateCards().first().containsKey(profileId))
        assertEquals(true, preferences.getProfileFlightModeVisibility(profileId, "THERMAL").first())
    }
}
