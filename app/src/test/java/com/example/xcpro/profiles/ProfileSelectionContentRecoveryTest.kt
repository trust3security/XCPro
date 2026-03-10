package com.example.xcpro.profiles

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.profiles.ui.ProfileSelectionContent
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
class ProfileSelectionContentRecoveryTest {

    private val composeRule = createComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(ActivityRegistrationRule())
        .around(composeRule)

    @Test
    fun bootstrapError_showsRecoveryActionsAndInvokesCallbacks() {
        var recoverCalls = 0
        var importCalls = 0

        composeRule.setContent {
            ProfileSelectionContent(
                state = baseState(
                    bootstrapError = "Failed to parse stored profiles."
                ),
                onSelectProfile = {},
                onDeleteProfile = {},
                onCreateProfile = {},
                onShowImportDialog = { importCalls++ },
                onShowCreateDialog = {},
                onHideCreateDialog = {},
                onRecoverWithDefaultProfile = { recoverCalls++ },
                onClearError = {},
                onContinue = {}
            )
        }

        composeRule.onNodeWithText("Recover Default").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Import Aircraft Profile").assertIsDisplayed().performClick()

        assertEquals(1, recoverCalls)
        assertEquals(1, importCalls)
    }

    @Test
    fun errorMessage_hidesBootstrapRecoveryActions() {
        composeRule.setContent {
            ProfileSelectionContent(
                state = baseState(
                    bootstrapError = "Failed to parse stored profiles.",
                    error = "Failed to recover with default profile: denied"
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

        composeRule.onAllNodesWithText("Recover Default").assertCountEquals(0)
        composeRule.onAllNodesWithText("Import Aircraft Profile").assertCountEquals(0)
    }

    @Test
    fun loadingState_disablesRecoveryActions() {
        composeRule.setContent {
            ProfileSelectionContent(
                state = baseState(
                    bootstrapError = "Failed to parse stored profiles.",
                    isLoading = true
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

        composeRule.onNodeWithText("Recover Default").assertIsNotEnabled()
        composeRule.onNodeWithText("Import Aircraft Profile").assertIsNotEnabled()
    }

    @Test
    fun editProfileButton_invokesCallback() {
        val profile = UserProfile(
            id = "test-profile",
            name = "Test Profile",
            aircraftType = AircraftType.PARAGLIDER
        )
        var editedProfileId: String? = null

        composeRule.setContent {
            ProfileSelectionContent(
                state = ProfileUiState(
                    profiles = listOf(profile),
                    activeProfile = null,
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
                onContinue = {},
                onEditProfile = { editedProfileId = it.id }
            )
        }

        composeRule.onNodeWithContentDescription("Edit Profile").performClick()

        assertEquals("test-profile", editedProfileId)
    }

    private fun baseState(
        bootstrapError: String,
        error: String? = null,
        isLoading: Boolean = false
    ): ProfileUiState {
        val defaultProfile = UserProfile(
            id = ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
            name = "Default",
            aircraftType = AircraftType.PARAGLIDER
        )
        return ProfileUiState(
            profiles = listOf(defaultProfile),
            activeProfile = null,
            isHydrated = true,
            bootstrapError = bootstrapError,
            isLoading = isLoading,
            error = error
        )
    }
}

private class ActivityRegistrationRule : ExternalResource() {
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
