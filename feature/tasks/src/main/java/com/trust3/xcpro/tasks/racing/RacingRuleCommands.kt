package com.trust3.xcpro.tasks.racing

import com.trust3.xcpro.tasks.core.RacingFinishCustomParams
import com.trust3.xcpro.tasks.core.RacingStartCustomParams

data class UpdateRacingStartRulesCommand(
    val rules: RacingStartCustomParams
)

data class UpdateRacingFinishRulesCommand(
    val rules: RacingFinishCustomParams
)

data class UpdateRacingValidationRulesCommand(
    val profile: RacingTaskStructureRules.Profile
)
