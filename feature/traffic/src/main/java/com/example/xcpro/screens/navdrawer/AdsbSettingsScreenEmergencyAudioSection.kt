package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.xcpro.adsb.ADSB_EMERGENCY_AUDIO_MAX_COOLDOWN_MS
import com.example.xcpro.adsb.ADSB_EMERGENCY_AUDIO_MIN_COOLDOWN_MS
import com.example.xcpro.adsb.ADSB_EMERGENCY_AUDIO_COHORT_PERCENT_MAX
import com.example.xcpro.adsb.ADSB_EMERGENCY_AUDIO_COHORT_PERCENT_MIN
import kotlin.math.roundToInt

private const val EMERGENCY_COOLDOWN_STEP_SECONDS = 5f
private const val EMERGENCY_COHORT_PERCENT_STEP = 5f

@Composable
internal fun AdsbEmergencyAudioSection(
    emergencyAudioMasterEnabled: Boolean,
    onEmergencyAudioMasterEnabledChanged: (Boolean) -> Unit,
    emergencyAudioShadowMode: Boolean,
    onEmergencyAudioShadowModeChanged: (Boolean) -> Unit,
    emergencyFlashEnabled: Boolean,
    onEmergencyFlashEnabledChanged: (Boolean) -> Unit,
    emergencyAudioEnabled: Boolean,
    onEmergencyAudioEnabledChanged: (Boolean) -> Unit,
    emergencyCooldownSliderSeconds: Float,
    onEmergencyCooldownSliderSecondsChanged: (Float) -> Unit,
    onEmergencyCooldownValueChangeFinished: () -> Unit,
    emergencyAudioCohortPercent: Int,
    onEmergencyAudioCohortPercentChanged: (Int) -> Unit,
    onEmergencyAudioCohortPercentValueChangeFinished: () -> Unit,
    emergencyAudioRollbackLatched: Boolean,
    emergencyAudioRollbackReason: String?,
    onClearEmergencyAudioRollback: () -> Unit
) {
    Text(
        text = "Optional one-shot alert for EMERGENCY risk only.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))

    RolloutToggleRow(
        title = "Master rollout gate",
        subtitle = "Global emergency audio enable.",
        checked = emergencyAudioMasterEnabled,
        onCheckedChanged = onEmergencyAudioMasterEnabledChanged
    )

    Spacer(modifier = Modifier.height(8.dp))

    RolloutToggleRow(
        title = "Shadow mode",
        subtitle = "Track FSM telemetry without sound.",
        checked = emergencyAudioShadowMode,
        onCheckedChanged = onEmergencyAudioShadowModeChanged
    )

    Spacer(modifier = Modifier.height(8.dp))

    RolloutToggleRow(
        title = "Emergency audio alerts",
        subtitle = "Plays only for EMERGENCY risk (never RED).",
        checked = emergencyAudioEnabled,
        onCheckedChanged = onEmergencyAudioEnabledChanged
    )

    Spacer(modifier = Modifier.height(12.dp))

    RolloutToggleRow(
        title = "Emergency icon flash",
        subtitle = "Pulse EMERGENCY markers on the map.",
        checked = emergencyFlashEnabled,
        onCheckedChanged = onEmergencyFlashEnabledChanged
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Emergency cooldown: ${emergencyCooldownSliderSeconds.roundToInt()} s",
        style = MaterialTheme.typography.bodyMedium
    )
    Slider(
        value = emergencyCooldownSliderSeconds,
        onValueChange = onEmergencyCooldownSliderSecondsChanged,
        onValueChangeFinished = onEmergencyCooldownValueChangeFinished,
        valueRange = emergencyCooldownSliderRange(),
        steps = emergencyCooldownSliderSteps()
    )
    Text(
        text = "Set how long re-alerts are suppressed after an emergency clears.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Rollout cohort: $emergencyAudioCohortPercent%",
        style = MaterialTheme.typography.bodyMedium
    )
    Slider(
        value = emergencyAudioCohortPercent.toFloat(),
        onValueChange = { value ->
            val snapped = snapEmergencyCohortPercent(value)
            onEmergencyAudioCohortPercentChanged(snapped)
        },
        onValueChangeFinished = onEmergencyAudioCohortPercentValueChangeFinished,
        valueRange = emergencyCohortPercentSliderRange(),
        steps = emergencyCohortPercentSliderSteps()
    )
    Text(
        text = "Controls percentage of devices eligible for master rollout output.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (emergencyAudioRollbackLatched) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.75f)) {
                Text(
                    text = "Rollback latched",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = emergencyAudioRollbackReason ?: "Auto rollback triggered",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClearEmergencyAudioRollback) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun RolloutToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.85f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChanged
        )
    }
}

internal fun emergencyCooldownSliderRange(): ClosedFloatingPointRange<Float> =
    (ADSB_EMERGENCY_AUDIO_MIN_COOLDOWN_MS / 1_000f)..
        (ADSB_EMERGENCY_AUDIO_MAX_COOLDOWN_MS / 1_000f)

internal fun emergencyCooldownSliderSteps(): Int {
    val range = emergencyCooldownSliderRange()
    return (((range.endInclusive - range.start) / EMERGENCY_COOLDOWN_STEP_SECONDS).roundToInt() - 1)
        .coerceAtLeast(0)
}

internal fun snapEmergencyCooldownSeconds(value: Float): Float {
    val range = emergencyCooldownSliderRange()
    val snapped = (value / EMERGENCY_COOLDOWN_STEP_SECONDS).roundToInt() * EMERGENCY_COOLDOWN_STEP_SECONDS
    return snapped.coerceIn(range.start, range.endInclusive)
}

internal fun emergencyCooldownMillisFromSeconds(seconds: Float): Long =
    (snapEmergencyCooldownSeconds(seconds).roundToInt() * 1_000L).coerceIn(
        ADSB_EMERGENCY_AUDIO_MIN_COOLDOWN_MS,
        ADSB_EMERGENCY_AUDIO_MAX_COOLDOWN_MS
    )

internal fun emergencyCooldownSeconds(cooldownMs: Long): Float =
    (cooldownMs.coerceIn(
        ADSB_EMERGENCY_AUDIO_MIN_COOLDOWN_MS,
        ADSB_EMERGENCY_AUDIO_MAX_COOLDOWN_MS
    ) / 1_000L).toFloat()

internal fun emergencyCohortPercentSliderRange(): ClosedFloatingPointRange<Float> =
    ADSB_EMERGENCY_AUDIO_COHORT_PERCENT_MIN.toFloat()..
        ADSB_EMERGENCY_AUDIO_COHORT_PERCENT_MAX.toFloat()

internal fun emergencyCohortPercentSliderSteps(): Int {
    val range = emergencyCohortPercentSliderRange()
    return (((range.endInclusive - range.start) / EMERGENCY_COHORT_PERCENT_STEP).roundToInt() - 1)
        .coerceAtLeast(0)
}

internal fun snapEmergencyCohortPercent(value: Float): Int {
    val range = emergencyCohortPercentSliderRange()
    val snapped = (value / EMERGENCY_COHORT_PERCENT_STEP).roundToInt() * EMERGENCY_COHORT_PERCENT_STEP
    return snapped.coerceIn(range.start, range.endInclusive).roundToInt()
}
