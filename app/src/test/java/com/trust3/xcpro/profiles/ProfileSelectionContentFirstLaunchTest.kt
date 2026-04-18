package com.trust3.xcpro.profiles

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.profiles.ui.ProfileSelectionContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProfileSelectionContentFirstLaunchTest {

    private val composeRule = createComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(FirstLaunchActivityRegistrationRule())
        .around(composeRule)

    @Test
    fun cleanFirstLaunch_showsAircraftPickerAndCompletesWithSelectedType() {
        var createdAircraftType: AircraftType? = null

        composeRule.setContent {
            ProfileSelectionContent(
                state = ProfileUiState(
                    isHydrated = true,
                    isFirstLaunchSetupRequired = true
                ),
                onCompleteFirstLaunch = { createdAircraftType = it },
                onSelectProfile = {},
                onDeleteProfile = {},
                onCreateProfile = {},
                onShowImportDialog = {},
                onShowCreateDialog = {},
                onHideCreateDialog = {},
                onRecoverWithDefaultProfile = {},
                onClearError = {},
                onContinue = {}
            )
        }

        composeRule.onNodeWithText("Create Default Profile").assertIsNotEnabled()
        composeRule.onNodeWithTag("first_launch_option_HANG_GLIDER")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Create Default Profile")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals(AircraftType.HANG_GLIDER, createdAircraftType)
    }

    @Test
    fun cleanFirstLaunch_keepsLoadProfileFileActionAvailable() {
        var importCalls = 0

        composeRule.setContent {
            ProfileSelectionContent(
                state = ProfileUiState(
                    isHydrated = true,
                    isFirstLaunchSetupRequired = true
                ),
                onSelectProfile = {},
                onDeleteProfile = {},
                onCreateProfile = {},
                onShowImportDialog = { importCalls++ },
                onShowCreateDialog = {},
                onHideCreateDialog = {},
                onRecoverWithDefaultProfile = {},
                onClearError = {},
                onContinue = {}
            )
        }

        composeRule.onNodeWithText("Load Profile File")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, importCalls)
    }

    @Test
    fun bootstrapError_emptyState_hidesFirstLaunchPickerAndShowsRecovery() {
        composeRule.setContent {
            ProfileSelectionContent(
                state = ProfileUiState(
                    isHydrated = true,
                    bootstrapError = "Failed to parse stored profiles."
                ),
                onSelectProfile = {},
                onDeleteProfile = {},
                onCreateProfile = {},
                onShowImportDialog = {},
                onShowCreateDialog = {},
                onHideCreateDialog = {},
                onRecoverWithDefaultProfile = {},
                onClearError = {},
                onContinue = {}
            )
        }

        composeRule.onAllNodesWithText("Create Default Profile").assertCountEquals(0)
        composeRule.onNodeWithText("Recover Default").assertIsDisplayed()
    }

    @Test
    fun existingActiveProfile_hidesFirstLaunchPickerAndShowsNormalSelectionUi() {
        val restoredProfile = UserProfile(
            id = "p-restored",
            name = "Restored Pilot",
            aircraftType = AircraftType.SAILPLANE,
            createdAt = 1L,
            lastUsed = 1L
        )

        composeRule.setContent {
            ProfileSelectionContent(
                state = ProfileUiState(
                    profiles = listOf(restoredProfile),
                    activeProfile = restoredProfile,
                    isHydrated = true
                ),
                onSelectProfile = {},
                onDeleteProfile = {},
                onCreateProfile = {},
                onShowImportDialog = {},
                onShowCreateDialog = {},
                onHideCreateDialog = {},
                onRecoverWithDefaultProfile = {},
                onClearError = {},
                onContinue = {}
            )
        }

        composeRule.onAllNodesWithText("Create Default Profile").assertCountEquals(0)
        composeRule.onNodeWithText("Profile Selected!").assertIsDisplayed()
        composeRule.onNodeWithText("Continue to Flight Map").assertIsDisplayed()
    }
}

private class FirstLaunchActivityRegistrationRule : ExternalResource() {
    override fun before() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowPackageManager = Shadows.shadowOf(context.packageManager)
        val component = ComponentName(context, ComponentActivity::class.java)
        val activityInfo = shadowPackageManager.addActivityIfNotPresent(component)
        activityInfo.exported = true
        activityInfo.name = ComponentActivity::class.java.name
        activityInfo.packageName = context.packageName
        shadowPackageManager.addOrUpdateActivity(activityInfo)
    }
}
