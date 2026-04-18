package com.trust3.xcpro.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import com.trust3.xcpro.profiles.ui.CreateProfileDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProfileAircraftTypeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun createProfileDialog_showsOnlyCanonicalAircraftTypes() {
        composeRule.setContent {
            MaterialTheme {
                CreateProfileDialog(
                    onDismiss = {},
                    onCreate = {}
                )
            }
        }

        composeRule.onNodeWithText("Sailplane").assertIsDisplayed()
        composeRule.onNodeWithText("Paraglider").assertIsDisplayed()
        composeRule.onNodeWithText("Hang Glider").assertIsDisplayed()
        composeRule.onAllNodesWithTag("create_profile_aircraft_type_GLIDER").assertCountEquals(0)
    }

    @Test
    fun profileBasicSettings_showsCanonicalAircraftTypeOptionsForDefaultProfile() {
        val defaultProfile = UserProfile(
            id = ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
            name = "Default",
            aircraftType = AircraftType.PARAGLIDER,
            createdAt = 1L,
            lastUsed = 1L
        )

        composeRule.setContent {
            MaterialTheme {
                ProfileBasicSettings(
                    profile = defaultProfile,
                    onProfileChanged = {}
                )
            }
        }

        composeRule.onAllNodesWithTag("profile_settings_aircraft_type_HANG_GLIDER").assertCountEquals(1)
        composeRule.onAllNodesWithTag("profile_settings_aircraft_type_PARAGLIDER").assertCountEquals(1)
        composeRule.onAllNodesWithTag("profile_settings_aircraft_type_SAILPLANE").assertCountEquals(1)
        composeRule.onAllNodesWithTag("profile_settings_aircraft_type_GLIDER").assertCountEquals(0)
    }
}
