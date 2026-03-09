package com.example.xcpro.tasks.racing

import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.core.RacingStartCustomParams

internal data class UpdateRacingStartRulesCommand(
    val rules: RacingStartCustomParams
)

internal data class UpdateRacingFinishRulesCommand(
    val rules: RacingFinishCustomParams
)

internal data class UpdateRacingValidationRulesCommand(
    val profile: RacingTaskStructureRules.Profile
)
