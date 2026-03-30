package com.example.xcpro.map.ui

import com.example.xcpro.adsb.ADSB_ERROR_OFFLINE
import com.example.xcpro.map.AdsbAuthMode
import com.example.xcpro.map.AdsbNetworkFailureKind
import com.example.xcpro.map.OgnConnectionIssue
import com.example.xcpro.map.AdsbTrafficSnapshot
import com.example.xcpro.map.OgnConnectionState
import com.example.xcpro.map.OgnTrafficSnapshot
import com.example.xcpro.map.adsbConnectionStateActive
import com.example.xcpro.map.adsbConnectionStateBackingOff
import com.example.xcpro.map.adsbConnectionStateDisabled
import com.example.xcpro.map.adsbConnectionStateError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MapTrafficConnectionIndicatorModelTest {

    @Test
    fun build_returnsGreenOgnDot_whenOgnIsConnected() {
        val state = buildState(
            ognOverlayEnabled = true,
            ognSnapshot = ognSnapshot(connectionState = OgnConnectionState.CONNECTED)
        )

        assertDot(
            indicator = state.ogn,
            sourceLabel = "OGN",
            tone = TrafficConnectionIndicatorTone.GREEN
        )
    }

    @Test
    fun build_returnsOgnLostCard_whenOgnIsInError() {
        val state = buildState(
            ognOverlayEnabled = true,
            ognSnapshot = ognSnapshot(connectionState = OgnConnectionState.ERROR)
        )

        assertLostCard(
            indicator = state.ogn,
            sourceLabel = "OGN",
            message = "OGN connection lost"
        )
    }

    @Test
    fun build_returnsOgnLostCard_whenOgnIsWaitingOffline() {
        val state = buildState(
            ognOverlayEnabled = true,
            ognSnapshot = ognSnapshot(
                connectionState = OgnConnectionState.ERROR,
                connectionIssue = OgnConnectionIssue.OFFLINE_WAIT
            )
        )

        assertLostCard(
            indicator = state.ogn,
            sourceLabel = "OGN",
            message = "OGN connection lost"
        )
    }

    @Test
    fun build_returnsCompactRedOgnDot_whenOgnLoginIsUnverified() {
        val state = buildState(
            ognOverlayEnabled = true,
            ognSnapshot = ognSnapshot(
                connectionState = OgnConnectionState.ERROR,
                connectionIssue = OgnConnectionIssue.LOGIN_UNVERIFIED
            )
        )

        assertDot(
            indicator = state.ogn,
            sourceLabel = "OGN",
            tone = TrafficConnectionIndicatorTone.RED
        )
    }

    @Test
    fun build_hidesOgnIndicator_whenOgnIsDisconnectedOrOverlayDisabled() {
        val disconnected = buildState(
            ognOverlayEnabled = true,
            ognSnapshot = ognSnapshot(connectionState = OgnConnectionState.DISCONNECTED)
        )
        val overlayDisabled = buildState(
            ognOverlayEnabled = false,
            ognSnapshot = ognSnapshot(connectionState = OgnConnectionState.CONNECTED)
        )

        assertNull(disconnected.ogn)
        assertNull(overlayDisabled.ogn)
    }

    @Test
    fun build_returnsGreenAdsbDot_whenAdsbIsActiveAndAuthHealthy() {
        val state = buildState(
            adsbOverlayEnabled = true,
            adsbSnapshot = adsbSnapshot(
                connectionState = adsbConnectionStateActive(),
                authMode = AdsbAuthMode.Authenticated
            )
        )

        assertDot(
            indicator = state.adsb,
            sourceLabel = "ADS-B",
            tone = TrafficConnectionIndicatorTone.GREEN
        )
    }

    @Test
    fun build_returnsAdsbLostCard_whenAdsbIsOfflineInError() {
        val state = buildState(
            adsbOverlayEnabled = true,
            adsbSnapshot = adsbSnapshot(
                connectionState = adsbConnectionStateError(ADSB_ERROR_OFFLINE),
                lastError = ADSB_ERROR_OFFLINE,
                networkOnline = false
            )
        )

        assertLostCard(
            indicator = state.adsb,
            sourceLabel = "ADS-B",
            message = "ADS-B signal lost"
        )
    }

    @Test
    fun build_returnsAdsbLostCard_whenAdsbIsBackingOffAfterTransportFailure() {
        val state = buildState(
            adsbOverlayEnabled = true,
            adsbSnapshot = adsbSnapshot(
                connectionState = adsbConnectionStateBackingOff(15),
                lastNetworkFailureKind = AdsbNetworkFailureKind.TIMEOUT
            )
        )

        assertLostCard(
            indicator = state.adsb,
            sourceLabel = "ADS-B",
            message = "ADS-B signal lost"
        )
    }

    @Test
    fun build_returnsCompactRedAdsbDot_whenAuthFailedEvenIfConnectionIsActive() {
        val state = buildState(
            adsbOverlayEnabled = true,
            adsbSnapshot = adsbSnapshot(
                connectionState = adsbConnectionStateActive(),
                authMode = AdsbAuthMode.AuthFailed
            )
        )

        assertDot(
            indicator = state.adsb,
            sourceLabel = "ADS-B",
            tone = TrafficConnectionIndicatorTone.RED
        )
    }

    @Test
    fun build_returnsCompactRedAdsbDot_whenAdsbBackoffIsRateLimitedNotSignalLost() {
        val state = buildState(
            adsbOverlayEnabled = true,
            adsbSnapshot = adsbSnapshot(
                connectionState = adsbConnectionStateBackingOff(30),
                lastHttpStatus = 429
            )
        )

        assertDot(
            indicator = state.adsb,
            sourceLabel = "ADS-B",
            tone = TrafficConnectionIndicatorTone.RED
        )
    }

    @Test
    fun build_hidesAdsbIndicator_whenAdsbIsDisabledOrOverlayDisabled() {
        val disabled = buildState(
            adsbOverlayEnabled = true,
            adsbSnapshot = adsbSnapshot(connectionState = adsbConnectionStateDisabled())
        )
        val overlayDisabled = buildState(
            adsbOverlayEnabled = false,
            adsbSnapshot = adsbSnapshot(connectionState = adsbConnectionStateActive())
        )

        assertNull(disabled.adsb)
        assertNull(overlayDisabled.adsb)
    }

    @Test
    fun followingIndicatorTopOffset_matchesVisibleIndicatorModes() {
        val none = TrafficConnectionIndicatorsUiState(
            ogn = null,
            adsb = null
        )
        val one = TrafficConnectionIndicatorsUiState(
            ogn = TrafficConnectionIndicatorUiModel(
                sourceLabel = "OGN",
                tone = TrafficConnectionIndicatorTone.GREEN
            ),
            adsb = null
        )
        val twoDots = TrafficConnectionIndicatorsUiState(
            ogn = TrafficConnectionIndicatorUiModel(
                sourceLabel = "OGN",
                tone = TrafficConnectionIndicatorTone.GREEN
            ),
            adsb = TrafficConnectionIndicatorUiModel(
                sourceLabel = "ADS-B",
                tone = TrafficConnectionIndicatorTone.RED
            )
        )
        val dotAndLostCard = TrafficConnectionIndicatorsUiState(
            ogn = TrafficConnectionIndicatorUiModel(
                sourceLabel = "OGN",
                tone = TrafficConnectionIndicatorTone.GREEN
            ),
            adsb = TrafficConnectionIndicatorUiModel(
                sourceLabel = "ADS-B",
                tone = TrafficConnectionIndicatorTone.RED,
                presentation = TrafficConnectionIndicatorPresentation.LostCard(
                    message = "ADS-B signal lost"
                )
            )
        )

        assertEquals(0f, none.followingIndicatorTopOffset().value, 0.001f)
        assertEquals(24.6f, one.followingIndicatorTopOffset().value, 0.001f)
        assertEquals(49.2f, twoDots.followingIndicatorTopOffset().value, 0.001f)
        assertEquals(54.6f, dotAndLostCard.followingIndicatorTopOffset().value, 0.001f)
    }

    private fun buildState(
        ognOverlayEnabled: Boolean = false,
        ognSnapshot: OgnTrafficSnapshot = ognSnapshot(connectionState = OgnConnectionState.DISCONNECTED),
        adsbOverlayEnabled: Boolean = false,
        adsbSnapshot: AdsbTrafficSnapshot = adsbSnapshot(connectionState = adsbConnectionStateDisabled())
    ): TrafficConnectionIndicatorsUiState = MapTrafficConnectionIndicatorModelBuilder.build(
        ognOverlayEnabled = ognOverlayEnabled,
        ognSnapshot = ognSnapshot,
        adsbOverlayEnabled = adsbOverlayEnabled,
        adsbSnapshot = adsbSnapshot
    )

    private fun ognSnapshot(
        connectionState: OgnConnectionState,
        connectionIssue: OgnConnectionIssue? = null
    ): OgnTrafficSnapshot = OgnTrafficSnapshot(
        targets = emptyList(),
        connectionState = connectionState,
        connectionIssue = connectionIssue,
        lastError = null,
        subscriptionCenterLat = null,
        subscriptionCenterLon = null,
        receiveRadiusKm = 50,
        ddbCacheAgeMs = null,
        reconnectBackoffMs = null,
        lastReconnectWallMs = null
    )

    private fun adsbSnapshot(
        connectionState: com.example.xcpro.map.AdsbConnectionState,
        authMode: AdsbAuthMode = AdsbAuthMode.Anonymous,
        lastHttpStatus: Int? = null,
        lastError: String? = null,
        lastNetworkFailureKind: AdsbNetworkFailureKind? = null,
        networkOnline: Boolean = true
    ): AdsbTrafficSnapshot = AdsbTrafficSnapshot(
        targets = emptyList(),
        connectionState = connectionState,
        authMode = authMode,
        centerLat = null,
        centerLon = null,
        receiveRadiusKm = 50,
        fetchedCount = 0,
        withinRadiusCount = 0,
        displayedCount = 0,
        lastHttpStatus = lastHttpStatus,
        remainingCredits = null,
        lastPollMonoMs = null,
        lastSuccessMonoMs = null,
        lastError = lastError,
        lastNetworkFailureKind = lastNetworkFailureKind,
        networkOnline = networkOnline
    )

    private fun assertDot(
        indicator: TrafficConnectionIndicatorUiModel?,
        sourceLabel: String,
        tone: TrafficConnectionIndicatorTone
    ) {
        assertNotNull(indicator)
        assertEquals(sourceLabel, indicator?.sourceLabel)
        assertEquals(tone, indicator?.tone)
        assertEquals(TrafficConnectionIndicatorPresentation.Dot, indicator?.presentation)
    }

    private fun assertLostCard(
        indicator: TrafficConnectionIndicatorUiModel?,
        sourceLabel: String,
        message: String
    ) {
        assertNotNull(indicator)
        assertEquals(sourceLabel, indicator?.sourceLabel)
        assertEquals(TrafficConnectionIndicatorTone.RED, indicator?.tone)
        assertEquals(
            TrafficConnectionIndicatorPresentation.LostCard(message = message),
            indicator?.presentation
        )
    }
}
