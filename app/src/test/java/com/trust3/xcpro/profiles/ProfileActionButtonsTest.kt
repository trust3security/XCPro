package com.trust3.xcpro.profiles

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProfileActionButtonsTest {

    private val composeRule = createComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(ProfileActionButtonsActivityRegistrationRule())
        .around(composeRule)

    @Test
    fun loadingState_disablesAllActions() {
        composeRule.setContent {
            ProfileActionButtons(
                onExport = {},
                onImport = {},
                isLoading = true,
                canDelete = true,
                onDelete = {}
            )
        }

        composeRule.onNodeWithText("Save Profile").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithText("Load Profile").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithText("Delete Profile").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun enabledState_invokesCallbacks() {
        var exportCalls = 0
        var importCalls = 0
        var deleteCalls = 0

        composeRule.setContent {
            ProfileActionButtons(
                onExport = { exportCalls++ },
                onImport = { importCalls++ },
                isLoading = false,
                canDelete = true,
                onDelete = { deleteCalls++ }
            )
        }

        composeRule.onNodeWithText("Save Profile").performClick()
        composeRule.onNodeWithText("Load Profile").performClick()
        composeRule.onNodeWithText("Delete Profile").performClick()

        assertEquals(1, exportCalls)
        assertEquals(1, importCalls)
        assertEquals(1, deleteCalls)
    }

    @Test
    fun defaultProfile_disablesDeleteAction() {
        var deleteCalls = 0

        composeRule.setContent {
            ProfileActionButtons(
                onExport = {},
                onImport = {},
                isLoading = false,
                canDelete = false,
                onDelete = { deleteCalls++ }
            )
        }

        composeRule.onNodeWithText("Delete Profile").assertIsDisplayed().assertIsNotEnabled()
        assertEquals(0, deleteCalls)
    }
}

private class ProfileActionButtonsActivityRegistrationRule : ExternalResource() {
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
