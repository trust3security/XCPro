package com.example.xcpro.map.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.xcpro.adsb.AdsbEmergencyAudioKpiPolicy
import com.example.xcpro.adsb.AdsbTrafficSnapshot

@Composable
internal fun AdsbDebugPolicyAndKpiRows(snapshot: AdsbTrafficSnapshot) {
    val reasonCounts = snapshot.proximityReasonCounts
    Text(
        text = "Reasons (noRef/circle/geom/closing/dwell/steady): ${reasonCounts.noOwnshipReferenceCount}/${reasonCounts.circlingRuleAppliedCount}/${reasonCounts.geometryEmergencyAppliedCount}/${reasonCounts.approachClosingCount}/${reasonCounts.recoveryDwellCount}/${reasonCounts.divergingOrSteadyCount}",
        color = Color(0xFFE5E7EB),
        style = MaterialTheme.typography.bodySmall
    )

    val emergencyKpis = snapshot.emergencyAudioKpis
    Text(
        text = "KPI per-hour (alerts/blocks): ${formatRatePerHour(emergencyKpis.alertsPerFlightHour)} / ${formatRatePerHour(emergencyKpis.cooldownBlockEpisodesPerFlightHour)}",
        color = Color(0xFFE5E7EB),
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        text = "KPI disable<=5m rate: ${formatPercent(emergencyKpis.disableWithin5MinRate)} (${emergencyKpis.disableWithin5MinCount}/${emergencyKpis.disableEventCount})",
        color = Color(0xFFE5E7EB),
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        text = "KPI violations (retrigger/mismatch): ${emergencyKpis.retriggerWithinCooldownCount}/${emergencyKpis.determinismMismatchCount}",
        color = Color(0xFFFCA5A5),
        style = MaterialTheme.typography.bodySmall
    )
    val thresholdAlertCode = AdsbEmergencyAudioKpiPolicy.firstViolationCode(emergencyKpis)
    Text(
        text = "KPI threshold alert: ${thresholdAlertCode ?: "none"}",
        color = if (thresholdAlertCode == null) Color(0xFFE5E7EB) else Color(0xFFFCA5A5),
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        text = "Rollout (cfg/effective/shadow/cohort): ${snapshot.emergencyAudioMasterRolloutConfigured}/${snapshot.emergencyAudioMasterRolloutEnabled}/${snapshot.emergencyAudioShadowModeEnabled}/${snapshot.emergencyAudioRolloutCohortPercent}%",
        color = Color(0xFFE5E7EB),
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        text = "Rollout bucket/eligible/latch: ${snapshot.emergencyAudioRolloutCohortBucket}/${snapshot.emergencyAudioRolloutCohortEligible}/${snapshot.emergencyAudioRollbackLatched}",
        color = Color(0xFFE5E7EB),
        style = MaterialTheme.typography.bodySmall
    )
    snapshot.emergencyAudioRollbackReason?.let { reason ->
        Text(
            text = "Rollback reason: $reason",
            color = Color(0xFFFCA5A5),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
