package com.example.xcpro.profiles

import android.app.KeyguardManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.xcpro.profiles.ui.ProfileSelectionContent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class ProfileSelectionContentRecoveryInstrumentedTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(DeviceWakeAndUnlockRule())
        .around(composeRule)

    @Test
    fun bootstrapErrorRecoveryButtons_areVisibleAndActionable() {
        setRecoveryContent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Recover Default").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Import Backup").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("recover-count").assertTextEquals("recover_count:1")
        composeRule.onNodeWithTag("import-count").assertTextEquals("import_count:1")
        composeRule.onNodeWithTag("last-action").assertTextEquals("last_action:import")
    }

    @Test
    fun errorMessage_hidesBootstrapRecoveryActions() {
        setRecoveryContent(error = "Failed to recover with default profile: denied")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Recover Default").assertCountEquals(0)
        composeRule.onAllNodesWithText("Import Backup").assertCountEquals(0)
    }

    @Test
    fun loadingState_disablesRecoveryActions() {
        setRecoveryContent(isLoading = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Recover Default").assertIsNotEnabled()
        composeRule.onNodeWithText("Import Backup").assertIsNotEnabled()
    }

    private fun setRecoveryContent(
        error: String? = null,
        isLoading: Boolean = false
    ) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            val keyguardManager = activity.getSystemService(KeyguardManager::class.java)
            keyguardManager?.requestDismissKeyguard(activity, null)
        }
        composeRule.setContent {
            var recoverCount by remember { mutableIntStateOf(0) }
            var importCount by remember { mutableIntStateOf(0) }
            var lastAction by remember { mutableStateOf("none") }

            Column {
                ProfileSelectionContent(
                    state = ProfileUiState(
                        profiles = listOf(
                            UserProfile(
                                id = ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
                                name = "Default",
                                aircraftType = AircraftType.PARAGLIDER
                            )
                        ),
                        activeProfile = null,
                        isHydrated = true,
                        bootstrapError = "Failed to parse stored profiles.",
                        error = error,
                        isLoading = isLoading
                    ),
                    onSelectProfile = {},
                    onDeleteProfile = {},
                    onCreateProfile = {},
                    onShowImportDialog = {
                        importCount += 1
                        lastAction = "import"
                    },
                    onShowCreateDialog = {},
                    onHideCreateDialog = {},
                    onRecoverWithDefaultProfile = {
                        recoverCount += 1
                        lastAction = "recover"
                    },
                    onClearError = {},
                    onContinue = {}
                )
                Text(
                    text = "recover_count:$recoverCount",
                    modifier = Modifier.testTag("recover-count")
                )
                Text(
                    text = "import_count:$importCount",
                    modifier = Modifier.testTag("import-count")
                )
                Text(
                    text = "last_action:$lastAction",
                    modifier = Modifier.testTag("last-action")
                )
            }
        }
    }
}

private class DeviceWakeAndUnlockRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val device = UiDevice.getInstance(instrumentation)
                runCatching {
                    if (!device.isScreenOn) {
                        device.wakeUp()
                    }
                    device.executeShellCommand("wm dismiss-keyguard")
                    device.executeShellCommand("input keyevent KEYCODE_WAKEUP")
                    device.waitForIdle()
                }
                base.evaluate()
            }
        }
    }
}
