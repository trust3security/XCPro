package com.example.xcpro.map.ui

import com.example.xcpro.adsb.ADSB_ERROR_OFFLINE
import com.example.xcpro.map.AdsbAuthMode
import com.example.xcpro.map.AdsbNetworkFailureKind
import com.example.xcpro.map.OgnConnectionIssue
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

sealed interface TrafficConnectionIndicatorPresentation {
    data object Dot : TrafficConnectionIndicatorPresentation

    data class LostCard(
        val message: String
    ) : TrafficConnectionIndicatorPresentation
}

data class TrafficConnectionIndicatorUiModel(
    val sourceLabel: String,
    val tone: TrafficConnectionIndicatorTone,
    val presentation: TrafficConnectionIndicatorPresentation = TrafficConnectionIndicatorPresentation.Dot
)

data class TrafficConnectionIndicatorsUiState(
    val ogn: TrafficConnectionIndicatorUiModel?,
    val adsb: TrafficConnectionIndicatorUiModel?
)

internal object MapTrafficConnectionIndicatorModelBuilder {
    private const val OGN_SOURCE_LABEL = "OGN"
    private const val ADSB_SOURCE_LABEL = "ADS-B"
    private const val OGN_CONNECTION_LOST_MESSAGE = "OGN connection lost"
    private const val ADSB_SIGNAL_LOST_MESSAGE = "ADS-B signal lost"

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
            OgnConnectionState.CONNECTED -> connectedDot(sourceLabel = OGN_SOURCE_LABEL)

            OgnConnectionState.ERROR -> if (shouldShowOgnSignalLost(snapshot)) {
                lostCard(
                    sourceLabel = OGN_SOURCE_LABEL,
                    message = OGN_CONNECTION_LOST_MESSAGE
                )
            } else {
                failedDot(sourceLabel = OGN_SOURCE_LABEL)
            }

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
            return failedDot(sourceLabel = ADSB_SOURCE_LABEL)
        }
        return when {
            snapshot.connectionState.isActive() -> connectedDot(
                sourceLabel = ADSB_SOURCE_LABEL
            )

            shouldShowAdsbSignalLost(snapshot) -> lostCard(
                sourceLabel = ADSB_SOURCE_LABEL,
                message = ADSB_SIGNAL_LOST_MESSAGE
            )

            snapshot.connectionState.isError() || snapshot.connectionState.isBackingOff() ->
                failedDot(
                    sourceLabel = ADSB_SOURCE_LABEL
                )

            else -> null
        }
    }

    private fun connectedDot(
        sourceLabel: String
    ): TrafficConnectionIndicatorUiModel = TrafficConnectionIndicatorUiModel(
        sourceLabel = sourceLabel,
        tone = TrafficConnectionIndicatorTone.GREEN
    )

    private fun failedDot(
        sourceLabel: String
    ): TrafficConnectionIndicatorUiModel = TrafficConnectionIndicatorUiModel(
        sourceLabel = sourceLabel,
        tone = TrafficConnectionIndicatorTone.RED
    )

    private fun lostCard(
        sourceLabel: String,
        message: String
    ): TrafficConnectionIndicatorUiModel = TrafficConnectionIndicatorUiModel(
        sourceLabel = sourceLabel,
        tone = TrafficConnectionIndicatorTone.RED,
        presentation = TrafficConnectionIndicatorPresentation.LostCard(message = message)
    )

    private fun shouldShowAdsbSignalLost(
        snapshot: AdsbTrafficSnapshot
    ): Boolean {
        if (!snapshot.connectionState.isError() && !snapshot.connectionState.isBackingOff()) {
            return false
        }
        return isAdsbTransportLoss(snapshot)
    }

    private fun isAdsbTransportLoss(
        snapshot: AdsbTrafficSnapshot
    ): Boolean {
        if (!snapshot.networkOnline) return true
        if (snapshot.lastError == ADSB_ERROR_OFFLINE) return true
        return snapshot.lastNetworkFailureKind in TRANSPORT_LOSS_FAILURE_KINDS
    }

    private fun shouldShowOgnSignalLost(
        snapshot: OgnTrafficSnapshot
    ): Boolean {
        val issue = snapshot.connectionIssue ?: return true
        return issue in OGN_TRANSPORT_LOSS_ISSUES
    }

    private val TRANSPORT_LOSS_FAILURE_KINDS = setOf(
        AdsbNetworkFailureKind.DNS,
        AdsbNetworkFailureKind.TIMEOUT,
        AdsbNetworkFailureKind.CONNECT,
        AdsbNetworkFailureKind.NO_ROUTE
    )

    private val OGN_TRANSPORT_LOSS_ISSUES = setOf(
        OgnConnectionIssue.UNEXPECTED_STREAM_END,
        OgnConnectionIssue.OFFLINE_WAIT,
        OgnConnectionIssue.STALL_TIMEOUT,
        OgnConnectionIssue.TRANSPORT_ERROR
    )
}
