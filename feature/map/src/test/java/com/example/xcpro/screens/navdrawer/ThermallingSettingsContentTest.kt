package com.example.xcpro.screens.navdrawer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThermallingSettingsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun masterDisabled_disablesDependentControls() {
        setContent(
            ThermallingSettingsUiState(
                enabled = false,
                applyZoomOnEnter = true
            )
        )

        composeRule.onNodeWithTag(THERMALLING_TAG_SWITCH_THERMAL_MODE).assertIsNotEnabled()
        composeRule.onNodeWithTag(THERMALLING_TAG_SWITCH_APPLY_ZOOM).assertIsNotEnabled()
        composeRule.onNodeWithTag(THERMALLING_TAG_SWITCH_REMEMBER_ZOOM).assertIsNotEnabled()
        composeRule.onNodeWithTag(THERMALLING_TAG_ENTER_DELAY_SLIDER).assertIsNotEnabled()
        composeRule.onNodeWithTag(THERMALLING_TAG_EXIT_DELAY_SLIDER).assertIsNotEnabled()
        composeRule.onNodeWithTag(THERMALLING_TAG_ZOOM_SLIDER).assertIsNotEnabled()
    }

    @Test
    fun zoomControls_requireApplyZoomOnEnter() {
        setContent(
            ThermallingSettingsUiState(
                enabled = true,
                applyZoomOnEnter = false
            )
        )

        composeRule.onNodeWithTag(THERMALLING_TAG_SWITCH_THERMAL_MODE).assertIsEnabled()
        composeRule.onNodeWithTag(THERMALLING_TAG_ENTER_DELAY_SLIDER).assertIsEnabled()
        composeRule.onNodeWithTag(THERMALLING_TAG_EXIT_DELAY_SLIDER).assertIsEnabled()
        composeRule.onNodeWithTag(THERMALLING_TAG_ZOOM_SLIDER).assertIsNotEnabled()
        composeRule.onNodeWithTag(THERMALLING_TAG_SWITCH_REMEMBER_ZOOM).assertIsNotEnabled()
    }

    private fun setContent(uiState: ThermallingSettingsUiState) {
        composeRule.setContent {
            MaterialTheme {
                ThermallingSettingsContent(
                    uiState = uiState,
                    onSetEnabled = {},
                    onSetSwitchToThermalMode = {},
                    onSetZoomOnlyFallbackWhenThermalHidden = {},
                    onSetEnterDelaySeconds = {},
                    onSetExitDelaySeconds = {},
                    onSetApplyZoomOnEnter = {},
                    onSetThermalZoomLevel = {},
                    onSetRememberManualThermalZoomInSession = {},
                    onSetRestorePreviousModeOnExit = {},
                    onSetRestorePreviousZoomOnExit = {}
                )
            }
        }
    }
}
