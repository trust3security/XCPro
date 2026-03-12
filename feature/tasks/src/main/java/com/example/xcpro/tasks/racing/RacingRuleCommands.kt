package com.example.xcpro.tasks.racing

import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.core.RacingStartCustomParams

data class UpdateRacingStartRulesCommand(
    val rules: RacingStartCustomParams
)

data class UpdateRacingFinishRulesCommand(
    val rules: RacingFinishCustomParams
)

data class UpdateRacingValidationRulesCommand(
    val profile: RacingTaskStructureRules.Profile
)
