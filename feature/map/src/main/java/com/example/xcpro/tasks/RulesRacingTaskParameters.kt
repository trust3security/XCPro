package com.example.xcpro.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.tasks.core.RacingAltitudeReference
import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.core.RacingPevCustomParams
import com.example.xcpro.tasks.core.RacingStartCustomParams
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.racing.RacingTaskStructureRules
import com.example.xcpro.tasks.racing.UpdateRacingFinishRulesCommand
import com.example.xcpro.tasks.racing.UpdateRacingStartRulesCommand
import com.example.xcpro.tasks.racing.UpdateRacingValidationRulesCommand
import com.example.xcpro.tasks.racing.models.RacingFinishPointType

private const val FAI_MIN_TURNPOINTS = 2
private const val FAI_MIN_FINISH_RING_METERS = 3_000.0

private data class ParsedInput<T>(
    val value: T?,
    val hasError: Boolean
)

@Composable
internal fun RulesRacingTaskParameters(
    uiState: TaskUiState,
    onUpdateRacingStartRules: (UpdateRacingStartRulesCommand) -> Unit,
    onUpdateRacingFinishRules: (UpdateRacingFinishRulesCommand) -> Unit,
    onUpdateRacingValidationRules: (UpdateRacingValidationRulesCommand) -> Unit
) {
    val waypoints = uiState.task.waypoints
    val startWaypoint = remember(waypoints) { waypoints.firstOrNull { it.role == WaypointRole.START } }
    val finishWaypoint = remember(waypoints) { waypoints.firstOrNull { it.role == WaypointRole.FINISH } }
    val turnpointCount = remember(waypoints) { waypoints.count { it.role == WaypointRole.TURNPOINT } }

    val startRules = remember(startWaypoint?.customParameters) {
        startWaypoint?.let { RacingStartCustomParams.from(it.customParameters) } ?: RacingStartCustomParams()
    }
    val finishRules = remember(finishWaypoint?.customParameters) {
        finishWaypoint?.let { RacingFinishCustomParams.from(it.customParameters) } ?: RacingFinishCustomParams()
    }

    var gateOpenInput by remember(startRules.gateOpenTimeMillis) { mutableStateOf(startRules.gateOpenTimeMillis?.toString().orEmpty()) }
    var gateCloseInput by remember(startRules.gateCloseTimeMillis) { mutableStateOf(startRules.gateCloseTimeMillis?.toString().orEmpty()) }
    var preStartAltitudeInput by remember(startRules.preStartAltitudeMeters) { mutableStateOf(startRules.preStartAltitudeMeters?.toString().orEmpty()) }
    var startDirectionInput by remember(startRules.directionOverrideDegrees) { mutableStateOf(startRules.directionOverrideDegrees?.toString().orEmpty()) }
    var startAltitudeReference by remember(startRules.altitudeReference) { mutableStateOf(startRules.altitudeReference) }

    var pevEnabled by remember(startRules.pev.enabled) { mutableStateOf(startRules.pev.enabled) }
    var pevWaitInput by remember(startRules.pev.waitTimeMinutes) { mutableStateOf(startRules.pev.waitTimeMinutes?.toString().orEmpty()) }
    var pevWindowInput by remember(startRules.pev.startWindowMinutes) { mutableStateOf(startRules.pev.startWindowMinutes?.toString().orEmpty()) }

    var finishCloseInput by remember(finishRules.closeTimeMillis) { mutableStateOf(finishRules.closeTimeMillis?.toString().orEmpty()) }
    var finishMinAltitudeInput by remember(finishRules.minAltitudeMeters) { mutableStateOf(finishRules.minAltitudeMeters?.toString().orEmpty()) }
    var finishDirectionInput by remember(finishRules.directionOverrideDegrees) { mutableStateOf(finishRules.directionOverrideDegrees?.toString().orEmpty()) }
    var finishAltitudeReference by remember(finishRules.altitudeReference) { mutableStateOf(finishRules.altitudeReference) }

    val gateOpen = parseLongInput(gateOpenInput)
    val gateClose = parseLongInput(gateCloseInput)
    val preStartAltitude = parseDoubleInput(preStartAltitudeInput)
    val startDirection = parseDoubleInput(startDirectionInput)
    val pevWait = parseIntInput(pevWaitInput)
    val pevWindow = parseIntInput(pevWindowInput)

    val finishClose = parseLongInput(finishCloseInput)
    val finishMinAltitude = parseDoubleInput(finishMinAltitudeInput)
    val finishDirection = parseDoubleInput(finishDirectionInput)

    val startErrors = buildStartInputErrors(
        profile = uiState.racingValidationProfile,
        gateOpen = gateOpen,
        gateClose = gateClose,
        pevEnabled = pevEnabled,
        pevWait = pevWait,
        pevWindow = pevWindow
    )
    val finishErrors = buildFinishInputErrors(
        finishClose = finishClose,
        finishMinAltitude = finishMinAltitude,
        finishDirection = finishDirection
    )
    val strictErrors = buildStrictProfileErrors(
        profile = uiState.racingValidationProfile,
        turnpointCount = turnpointCount,
        finishWaypoint = finishWaypoint
    )
    val hasStrictBlockingErrors = strictErrors.isNotEmpty()
    val warnings = buildStrictProfileWarnings(uiState.racingValidationProfile)

    RulesParameterSection(
        title = "Racing Task Rules",
        icon = Icons.Default.Speed,
        color = RacingTaskColor
    ) {
        Text(
            text = "Validation Profile",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.racingValidationProfile == RacingTaskStructureRules.Profile.FAI_STRICT,
                onClick = {
                    onUpdateRacingValidationRules(
                        UpdateRacingValidationRulesCommand(RacingTaskStructureRules.Profile.FAI_STRICT)
                    )
                },
                label = { Text("FAI Strict") },
                modifier = Modifier.testTag("rt_rules_profile_fai")
            )
            FilterChip(
                selected = uiState.racingValidationProfile == RacingTaskStructureRules.Profile.XC_PRO_EXTENDED,
                onClick = {
                    onUpdateRacingValidationRules(
                        UpdateRacingValidationRulesCommand(RacingTaskStructureRules.Profile.XC_PRO_EXTENDED)
                    )
                },
                label = { Text("XC Pro Extended") },
                modifier = Modifier.testTag("rt_rules_profile_extended")
            )
        }

        if (startErrors.isNotEmpty() || finishErrors.isNotEmpty() || strictErrors.isNotEmpty() || warnings.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rt_rules_validation_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (strictErrors + startErrors + finishErrors).forEach { error ->
                        Text(
                            text = "Error: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    warnings.forEach { warning ->
                        Text(
                            text = "Warning: $warning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(text = "Start Rules", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = gateOpenInput,
            onValueChange = { gateOpenInput = it },
            label = { Text("Gate Open (ms)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("rt_start_gate_open")
        )
        OutlinedTextField(
            value = gateCloseInput,
            onValueChange = { gateCloseInput = it },
            label = { Text("Gate Close (ms)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("rt_start_gate_close")
        )
        OutlinedTextField(
            value = preStartAltitudeInput,
            onValueChange = { preStartAltitudeInput = it },
            label = { Text("Pre-start Altitude (m)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("rt_start_pre_alt")
        )
        OutlinedTextField(
            value = startDirectionInput,
            onValueChange = { startDirectionInput = it },
            label = { Text("Direction Override (deg)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("rt_start_direction")
        )
        AltitudeReferenceSelector(
            selected = startAltitudeReference,
            onSelect = { startAltitudeReference = it },
            testTagPrefix = "rt_start_alt_ref"
        )
        FilterChip(
            selected = pevEnabled,
            onClick = { pevEnabled = !pevEnabled },
            label = { Text("PEV Start Enabled") },
            modifier = Modifier.testTag("rt_start_pev_enabled")
        )
        OutlinedTextField(
            value = pevWaitInput,
            onValueChange = { pevWaitInput = it },
            label = { Text("PEV Wait (minutes)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("rt_start_pev_wait")
        )
        OutlinedTextField(
            value = pevWindowInput,
            onValueChange = { pevWindowInput = it },
            label = { Text("PEV Window (minutes)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("rt_start_pev_window")
        )
        Button(
            enabled = startErrors.isEmpty() && !hasStrictBlockingErrors,
            onClick = {
                onUpdateRacingStartRules(
                    UpdateRacingStartRulesCommand(
                        rules = RacingStartCustomParams(
                            gateOpenTimeMillis = gateOpen.value,
                            gateCloseTimeMillis = gateClose.value,
                            toleranceMeters = startRules.toleranceMeters,
                            preStartAltitudeMeters = preStartAltitude.value,
                            altitudeReference = startAltitudeReference,
                            directionOverrideDegrees = startDirection.value,
                            maxStartAltitudeMeters = startRules.maxStartAltitudeMeters,
                            maxStartGroundspeedMs = startRules.maxStartGroundspeedMs,
                            pev = RacingPevCustomParams(
                                enabled = pevEnabled,
                                waitTimeMinutes = pevWait.value,
                                startWindowMinutes = pevWindow.value,
                                maxPressesPerLaunch = startRules.pev.maxPressesPerLaunch,
                                dedupeSeconds = startRules.pev.dedupeSeconds,
                                minIntervalMinutes = startRules.pev.minIntervalMinutes,
                                pressTimestampsMillis = startRules.pev.pressTimestampsMillis
                            )
                        )
                    )
                )
            },
            modifier = Modifier.testTag("rt_start_apply")
        ) {
            Text("Apply Start Rules")
        }

        Spacer(Modifier.height(12.dp))
        Text(text = "Finish Rules", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = finishCloseInput,
            onValueChange = { finishCloseInput = it },
            label = { Text("Finish Close (ms)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("rt_finish_close")
        )
        OutlinedTextField(
            value = finishMinAltitudeInput,
            onValueChange = { finishMinAltitudeInput = it },
            label = { Text("Min Finish Altitude (m)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("rt_finish_min_alt")
        )
        OutlinedTextField(
            value = finishDirectionInput,
            onValueChange = { finishDirectionInput = it },
            label = { Text("Direction Override (deg)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("rt_finish_direction")
        )
        AltitudeReferenceSelector(
            selected = finishAltitudeReference,
            onSelect = { finishAltitudeReference = it },
            testTagPrefix = "rt_finish_alt_ref"
        )
        Button(
            enabled = finishErrors.isEmpty() && !hasStrictBlockingErrors,
            onClick = {
                onUpdateRacingFinishRules(
                    UpdateRacingFinishRulesCommand(
                        rules = RacingFinishCustomParams(
                            closeTimeMillis = finishClose.value,
                            minAltitudeMeters = finishMinAltitude.value,
                            altitudeReference = finishAltitudeReference,
                            directionOverrideDegrees = finishDirection.value,
                            allowStraightInBelowMinAltitude = finishRules.allowStraightInBelowMinAltitude,
                            requireLandWithoutDelay = finishRules.requireLandWithoutDelay,
                            landWithoutDelayWindowSeconds = finishRules.landWithoutDelayWindowSeconds,
                            landingSpeedThresholdMs = finishRules.landingSpeedThresholdMs,
                            landingHoldSeconds = finishRules.landingHoldSeconds,
                            contestBoundaryRadiusMeters = finishRules.contestBoundaryRadiusMeters,
                            stopPlusFiveEnabled = finishRules.stopPlusFiveEnabled,
                            stopPlusFiveMinutes = finishRules.stopPlusFiveMinutes
                        )
                    )
                )
            },
            modifier = Modifier.testTag("rt_finish_apply")
        ) {
            Text("Apply Finish Rules")
        }
    }
}

@Composable
private fun AltitudeReferenceSelector(
    selected: RacingAltitudeReference,
    onSelect: (RacingAltitudeReference) -> Unit,
    testTagPrefix: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Altitude Reference", style = MaterialTheme.typography.labelMedium)
        FilterChip(
            selected = selected == RacingAltitudeReference.MSL,
            onClick = { onSelect(RacingAltitudeReference.MSL) },
            label = { Text("MSL") },
            modifier = Modifier.testTag("${testTagPrefix}_msl")
        )
        FilterChip(
            selected = selected == RacingAltitudeReference.QNH,
            onClick = { onSelect(RacingAltitudeReference.QNH) },
            label = { Text("QNH") },
            modifier = Modifier.testTag("${testTagPrefix}_qnh")
        )
    }
}

private fun buildStartInputErrors(
    profile: RacingTaskStructureRules.Profile,
    gateOpen: ParsedInput<Long>,
    gateClose: ParsedInput<Long>,
    pevEnabled: Boolean,
    pevWait: ParsedInput<Int>,
    pevWindow: ParsedInput<Int>
): List<String> {
    val errors = mutableListOf<String>()
    if (gateOpen.hasError) errors += "Start gate open must be a valid number"
    if (gateClose.hasError) errors += "Start gate close must be a valid number"
    if (profile == RacingTaskStructureRules.Profile.FAI_STRICT && gateOpen.value == null) {
        errors += "Start gate open time is required in FAI Strict profile"
    }
    if (gateOpen.value != null && gateClose.value != null && gateOpen.value > gateClose.value) {
        errors += "Start gate open must be less than or equal to gate close"
    }
    if (pevEnabled) {
        if (pevWait.hasError || pevWait.value == null || pevWait.value !in 5..10) {
            errors += "PEV wait must be between 5 and 10 minutes"
        }
        if (pevWindow.hasError || pevWindow.value == null || pevWindow.value !in 5..10) {
            errors += "PEV window must be between 5 and 10 minutes"
        }
    }
    return errors
}

private fun buildFinishInputErrors(
    finishClose: ParsedInput<Long>,
    finishMinAltitude: ParsedInput<Double>,
    finishDirection: ParsedInput<Double>
): List<String> {
    val errors = mutableListOf<String>()
    if (finishClose.hasError) errors += "Finish close must be a valid number"
    if (finishMinAltitude.hasError) errors += "Finish minimum altitude must be a valid number"
    if (finishDirection.hasError) errors += "Finish direction override must be a valid number"
    return errors
}

private fun buildStrictProfileErrors(
    profile: RacingTaskStructureRules.Profile,
    turnpointCount: Int,
    finishWaypoint: com.example.xcpro.tasks.core.TaskWaypoint?
): List<String> {
    if (profile != RacingTaskStructureRules.Profile.FAI_STRICT) return emptyList()
    val errors = mutableListOf<String>()
    if (turnpointCount < FAI_MIN_TURNPOINTS) {
        errors += "FAI Strict requires at least $FAI_MIN_TURNPOINTS turnpoints"
    }
    val finishType = finishWaypoint?.customPointType
    val isFinishRing = finishType != RacingFinishPointType.FINISH_LINE.name
    val finishRadius = finishWaypoint?.resolvedCustomRadiusMeters() ?: FAI_MIN_FINISH_RING_METERS
    if (isFinishRing && finishRadius < FAI_MIN_FINISH_RING_METERS) {
        errors += "FAI Strict finish ring radius must be at least ${FAI_MIN_FINISH_RING_METERS.toInt()}m"
    }
    return errors
}

private fun buildStrictProfileWarnings(profile: RacingTaskStructureRules.Profile): List<String> {
    if (profile != RacingTaskStructureRules.Profile.XC_PRO_EXTENDED) return emptyList()
    return listOf("XC Pro Extended profile enabled. Strict FAI constraints are not enforced.")
}

private fun parseLongInput(text: String): ParsedInput<Long> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ParsedInput(value = null, hasError = false)
    val value = trimmed.toLongOrNull()
    return ParsedInput(value = value, hasError = value == null)
}

private fun parseIntInput(text: String): ParsedInput<Int> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ParsedInput(value = null, hasError = false)
    val value = trimmed.toIntOrNull()
    return ParsedInput(value = value, hasError = value == null)
}

private fun parseDoubleInput(text: String): ParsedInput<Double> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ParsedInput(value = null, hasError = false)
    val value = trimmed.toDoubleOrNull()
    return ParsedInput(value = value, hasError = value == null)
}
