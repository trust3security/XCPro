package com.example.xcpro.map.ui

import com.example.xcpro.map.AdsbAuthMode
import com.example.xcpro.map.AdsbTrafficSnapshot
import com.example.xcpro.map.OgnConnectionState
import com.example.xcpro.map.OgnTrafficSnapshot
import com.example.xcpro.map.isActive
import com.example.xcpro.map.isBackingOff
import com.example.xcpro.map.isDisabled
import com.example.xcpro.map.isError

enum class TrafficConnectionIndicatorTone {
    GREEN,
    RED
}

data class TrafficConnectionIndicatorUiModel(
    val sourceLabel: String,
    val tone: TrafficConnectionIndicatorTone
)

data class TrafficConnectionIndicatorsUiState(
    val ogn: TrafficConnectionIndicatorUiModel?,
    val adsb: TrafficConnectionIndicatorUiModel?
)

internal object MapTrafficConnectionIndicatorModelBuilder {
    private const val OGN_SOURCE_LABEL = "OGN"
    private const val ADSB_SOURCE_LABEL = "ADS-B"

    fun build(
        ognOverlayEnabled: Boolean,
        ognSnapshot: OgnTrafficSnapshot,
        adsbOverlayEnabled: Boolean,
        adsbSnapshot: AdsbTrafficSnapshot
    ): TrafficConnectionIndicatorsUiState = TrafficConnectionIndicatorsUiState(
        ogn = buildOgnIndicator(
            overlayEnabled = ognOverlayEnabled,
            snapshot = ognSnapshot
        ),
        adsb = buildAdsbIndicator(
            overlayEnabled = adsbOverlayEnabled,
            snapshot = adsbSnapshot
        )
    )

    private fun buildOgnIndicator(
        overlayEnabled: Boolean,
        snapshot: OgnTrafficSnapshot
    ): TrafficConnectionIndicatorUiModel? {
        if (!overlayEnabled) return null
        return when (snapshot.connectionState) {
            OgnConnectionState.CONNECTED -> TrafficConnectionIndicatorUiModel(
                sourceLabel = OGN_SOURCE_LABEL,
                tone = TrafficConnectionIndicatorTone.GREEN
            )

            OgnConnectionState.ERROR -> TrafficConnectionIndicatorUiModel(
                sourceLabel = OGN_SOURCE_LABEL,
                tone = TrafficConnectionIndicatorTone.RED
            )

            OgnConnectionState.DISCONNECTED,
            OgnConnectionState.CONNECTING -> null
        }
    }

    private fun buildAdsbIndicator(
        overlayEnabled: Boolean,
        snapshot: AdsbTrafficSnapshot
    ): TrafficConnectionIndicatorUiModel? {
        if (!overlayEnabled) return null
        if (snapshot.connectionState.isDisabled()) return null
        if (snapshot.authMode == AdsbAuthMode.AuthFailed) {
            return TrafficConnectionIndicatorUiModel(
                sourceLabel = ADSB_SOURCE_LABEL,
                tone = TrafficConnectionIndicatorTone.RED
            )
        }
        return when {
            snapshot.connectionState.isActive() -> TrafficConnectionIndicatorUiModel(
                sourceLabel = ADSB_SOURCE_LABEL,
                tone = TrafficConnectionIndicatorTone.GREEN
            )

            snapshot.connectionState.isError() || snapshot.connectionState.isBackingOff() ->
                TrafficConnectionIndicatorUiModel(
                    sourceLabel = ADSB_SOURCE_LABEL,
                    tone = TrafficConnectionIndicatorTone.RED
                )

            else -> null
        }
    }
}
