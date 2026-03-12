package com.example.xcpro.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.xcpro.tasks.core.RacingAltitudeReference
import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.core.RacingPevCustomParams
import com.example.xcpro.tasks.core.RacingStartCustomParams
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.racing.RacingTaskStructureRules
import com.example.xcpro.tasks.racing.UpdateRacingFinishRulesCommand
import com.example.xcpro.tasks.racing.UpdateRacingStartRulesCommand
import com.example.xcpro.tasks.racing.UpdateRacingValidationRulesCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RulesRacingTaskParametersTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun strictProfileRendersValidationErrorsForTaskShapeAndGateRules() {
        setContent(
            task = taskWithWaypoints(
                turnpointCount = 1,
                finishRadiusMeters = 2_000.0
            ),
            profile = RacingTaskStructureRules.Profile.FAI_STRICT
        )

        composeRule.onNodeWithTag("rt_rules_validation_card").assertIsDisplayed()
        composeRule.onNodeWithText("Error: FAI Strict requires at least 2 turnpoints").assertIsDisplayed()
        composeRule.onNodeWithText("Error: Start gate open time is required in FAI Strict profile").assertIsDisplayed()
        composeRule.onNodeWithText("Error: FAI Strict finish ring radius must be at least 3000m").assertIsDisplayed()
    }

    @Test
    fun applyStartRulesButton_disabledWhenStrictValidationFails() {
        setContent(
            task = taskWithWaypoints(
                turnpointCount = 1,
                finishRadiusMeters = 2_000.0,
                startRules = RacingStartCustomParams(
                    gateOpenTimeMillis = 1_000L,
                    gateCloseTimeMillis = 2_000L
                )
            ),
            profile = RacingTaskStructureRules.Profile.FAI_STRICT
        )

        composeRule.onNodeWithTag("rt_start_apply").assertIsNotEnabled()
    }

    @Test
    fun profileChipClick_emitsValidationProfileCommand() {
        var captured: UpdateRacingValidationRulesCommand? = null
        setContent(
            task = taskWithWaypoints(turnpointCount = 2, finishRadiusMeters = 3_000.0),
            profile = RacingTaskStructureRules.Profile.FAI_STRICT,
            onValidation = { captured = it }
        )

        composeRule.onNodeWithTag("rt_rules_profile_extended").performClick()

        assertNotNull(captured)
        assertEquals(RacingTaskStructureRules.Profile.XC_PRO_EXTENDED, captured?.profile)
    }

    @Test
    fun startApplyButton_enabledWhenStartInputsAreValid() {
        setContent(
            task = taskWithWaypoints(
                turnpointCount = 2,
                finishRadiusMeters = 3_000.0,
                startRules = RacingStartCustomParams(
                    gateOpenTimeMillis = 1_000L,
                    gateCloseTimeMillis = 2_000L,
                    toleranceMeters = 450.0,
                    preStartAltitudeMeters = 1_250.0,
                    altitudeReference = RacingAltitudeReference.QNH,
                    directionOverrideDegrees = 178.5,
                    maxStartAltitudeMeters = 1_900.0,
                    maxStartGroundspeedMs = 45.0,
                    pev = RacingPevCustomParams(
                        enabled = true,
                        waitTimeMinutes = 6,
                        startWindowMinutes = 8
                    )
                )
            ),
            profile = RacingTaskStructureRules.Profile.FAI_STRICT
        )

        composeRule.onNodeWithTag("rt_start_apply").assertIsEnabled()
    }

    @Test
    fun finishApplyButton_enabledWhenFinishInputsAreValid() {
        setContent(
            task = taskWithWaypoints(
                turnpointCount = 2,
                finishRadiusMeters = 3_000.0,
                startRules = RacingStartCustomParams(gateOpenTimeMillis = 1_000L),
                finishRules = RacingFinishCustomParams(
                    closeTimeMillis = 9_000L,
                    minAltitudeMeters = 850.0,
                    altitudeReference = RacingAltitudeReference.QNH,
                    directionOverrideDegrees = 92.0,
                    allowStraightInBelowMinAltitude = true,
                    requireLandWithoutDelay = true,
                    landWithoutDelayWindowSeconds = 300L,
                    landingSpeedThresholdMs = 6.5,
                    landingHoldSeconds = 18L,
                    contestBoundaryRadiusMeters = 9_500.0,
                    stopPlusFiveEnabled = true,
                    stopPlusFiveMinutes = 7L
                )
            ),
            profile = RacingTaskStructureRules.Profile.FAI_STRICT
        )

        composeRule.onNodeWithTag("rt_finish_apply").assertIsEnabled()
    }

    @Test
    fun finishApplyButton_disabledWhenStrictValidationFails() {
        setContent(
            task = taskWithWaypoints(
                turnpointCount = 1,
                finishRadiusMeters = 2_000.0,
                startRules = RacingStartCustomParams(
                    gateOpenTimeMillis = 1_000L,
                    gateCloseTimeMillis = 2_000L
                ),
                finishRules = RacingFinishCustomParams(
                    closeTimeMillis = 9_000L,
                    minAltitudeMeters = 850.0
                )
            ),
            profile = RacingTaskStructureRules.Profile.FAI_STRICT
        )

        composeRule.onNodeWithTag("rt_finish_apply").assertIsNotEnabled()
    }

    @Test
    fun gateOpenAfterClose_showsValidationErrorAndDisablesApply() {
        setContent(
            task = taskWithWaypoints(
                turnpointCount = 2,
                finishRadiusMeters = 3_000.0,
                startRules = RacingStartCustomParams(
                    gateOpenTimeMillis = 2_000L,
                    gateCloseTimeMillis = 1_000L
                )
            ),
            profile = RacingTaskStructureRules.Profile.FAI_STRICT
        )

        composeRule.onNodeWithText(
            "Error: Start gate open must be less than or equal to gate close"
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("rt_start_apply").assertIsNotEnabled()
    }

    @Test
    fun pevRangeViolation_showsValidationErrorsAndDisablesApply() {
        setContent(
            task = taskWithWaypoints(
                turnpointCount = 2,
                finishRadiusMeters = 3_000.0,
                startRules = RacingStartCustomParams(
                    gateOpenTimeMillis = 1_000L,
                    gateCloseTimeMillis = 2_000L,
                    pev = RacingPevCustomParams(
                        enabled = true,
                        waitTimeMinutes = 2,
                        startWindowMinutes = 12
                    )
                )
            ),
            profile = RacingTaskStructureRules.Profile.FAI_STRICT
        )

        composeRule.onNodeWithText("Error: PEV wait must be between 5 and 10 minutes").assertIsDisplayed()
        composeRule.onNodeWithText("Error: PEV window must be between 5 and 10 minutes").assertIsDisplayed()
        composeRule.onNodeWithTag("rt_start_apply").assertIsNotEnabled()
    }

    @Test
    fun invalidFinishNumberInput_showsErrorAndDisablesFinishApply() {
        setContent(
            task = taskWithWaypoints(
                turnpointCount = 2,
                finishRadiusMeters = 3_000.0,
                startRules = RacingStartCustomParams(gateOpenTimeMillis = 1_000L)
            ),
            profile = RacingTaskStructureRules.Profile.FAI_STRICT
        )

        composeRule.onNodeWithTag("rt_finish_min_alt").performTextReplacement("not-a-number")

        composeRule.onNodeWithText("Error: Finish minimum altitude must be a valid number").assertIsDisplayed()
        composeRule.onNodeWithTag("rt_finish_apply").assertIsNotEnabled()
    }

    @Test
    fun extendedProfile_showsWarningAndSuppressesStrictErrors() {
        setContent(
            task = taskWithWaypoints(turnpointCount = 0, finishRadiusMeters = 500.0),
            profile = RacingTaskStructureRules.Profile.XC_PRO_EXTENDED
        )

        composeRule.onNodeWithText(
            "Warning: XC Pro Extended profile enabled. Strict FAI constraints are not enforced."
        ).assertIsDisplayed()
    }

    private fun setContent(
        task: Task,
        profile: RacingTaskStructureRules.Profile,
        onStart: (UpdateRacingStartRulesCommand) -> Unit = {},
        onFinish: (UpdateRacingFinishRulesCommand) -> Unit = {},
        onValidation: (UpdateRacingValidationRulesCommand) -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                RulesRacingTaskParameters(
                    uiState = TaskUiState(
                        task = task,
                        taskType = TaskType.RACING,
                        racingValidationProfile = profile
                    ),
                    onUpdateRacingStartRules = onStart,
                    onUpdateRacingFinishRules = onFinish,
                    onUpdateRacingValidationRules = onValidation
                )
            }
        }
    }

    private fun taskWithWaypoints(
        turnpointCount: Int,
        finishRadiusMeters: Double,
        startRules: RacingStartCustomParams? = null,
        finishRules: RacingFinishCustomParams? = null
    ): Task {
        val startParams = mutableMapOf<String, Any>().apply {
            startRules?.applyTo(this)
        }
        val finishParams = mutableMapOf<String, Any>().apply {
            finishRules?.applyTo(this)
        }

        val waypoints = mutableListOf(
            TaskWaypoint(
                id = "start",
                title = "Start",
                subtitle = "",
                lat = 0.0,
                lon = 0.0,
                role = WaypointRole.START,
                customParameters = startParams
            )
        )
        repeat(turnpointCount) { idx ->
            waypoints += TaskWaypoint(
                id = "tp-$idx",
                title = "TP $idx",
                subtitle = "",
                lat = 0.01 * (idx + 1),
                lon = 0.01 * (idx + 1),
                role = WaypointRole.TURNPOINT
            )
        }
        waypoints += TaskWaypoint(
            id = "finish",
            title = "Finish",
            subtitle = "",
            lat = 1.0,
            lon = 1.0,
            role = WaypointRole.FINISH,
            customPointType = "FINISH_CYLINDER",
            customRadiusMeters = finishRadiusMeters,
            customParameters = finishParams
        )
        return Task(id = "task-ui", waypoints = waypoints)
    }
}
