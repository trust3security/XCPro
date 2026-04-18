package com.trust3.xcpro.livefollow.watch

internal data class LiveFollowWatchTelemetryField(
    val label: String,
    val value: String
)

internal fun buildLiveFollowWatchTelemetryFields(
    uiState: LiveFollowWatchUiState
): List<LiveFollowWatchTelemetryField> {
    // AI-NOTE: Keep spectator telemetry labels explicit and render AGL only
    // when the watched LiveFollow transport carries a true watched AGL value.
    return listOfNotNull(
        uiState.panelStatusLabel?.let { LiveFollowWatchTelemetryField("Status", it) },
        uiState.panelSpeedLabel?.let { LiveFollowWatchTelemetryField("Speed", it) },
        uiState.panelAltitudeLabel?.let { LiveFollowWatchTelemetryField("Altitude MSL", it) },
        uiState.panelAglLabel?.let { LiveFollowWatchTelemetryField("AGL", it) },
        uiState.panelHeadingLabel?.let { LiveFollowWatchTelemetryField("Heading", it) },
        uiState.panelFreshnessLabel?.let { LiveFollowWatchTelemetryField("Last update", it) }
    )
}
