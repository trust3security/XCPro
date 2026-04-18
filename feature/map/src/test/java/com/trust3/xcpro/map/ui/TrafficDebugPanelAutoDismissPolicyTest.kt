package com.trust3.xcpro.map.ui

import com.trust3.xcpro.map.AdsbAuthMode
import com.trust3.xcpro.map.AdsbNetworkFailureKind
import com.trust3.xcpro.map.AdsbTrafficSnapshot
import com.trust3.xcpro.map.OgnConnectionState
import com.trust3.xcpro.map.OgnTrafficSnapshot
import com.trust3.xcpro.map.adsbConnectionStateActive
import com.trust3.xcpro.map.adsbConnectionStateBackingOff
import com.trust3.xcpro.map.adsbConnectionStateDisabled
import com.trust3.xcpro.map.adsbConnectionStateError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import com.trust3.xcpro.map.AdsbConnectionState

class TrafficDebugPanelAutoDismissPolicyTest {

    @Test
    fun ogn_readyOnlyWhenConnected() {
        assertFalse(isOgnReadyForAutoDismiss(ognSnapshot(OgnConnectionState.DISCONNECTED)))
        assertFalse(isOgnReadyForAutoDismiss(ognSnapshot(OgnConnectionState.CONNECTING)))
        assertFalse(isOgnReadyForAutoDismiss(ognSnapshot(OgnConnectionState.ERROR)))
        assertTrue(isOgnReadyForAutoDismiss(ognSnapshot(OgnConnectionState.CONNECTED)))
    }

    @Test
    fun adsb_readyWhenActiveAndAuthNotFailed() {
        assertTrue(
            isAdsbReadyForAutoDismiss(
                adsbSnapshot(
                    connectionState = adsbConnectionStateActive(),
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        )
        assertTrue(
            isAdsbReadyForAutoDismiss(
                adsbSnapshot(
                    connectionState = adsbConnectionStateActive(),
                    authMode = AdsbAuthMode.Anonymous
                )
            )
        )
    }

    @Test
    fun adsb_notReadyWhenAuthFailedOrNotActive() {
        assertFalse(
            isAdsbReadyForAutoDismiss(
                adsbSnapshot(
                    connectionState = adsbConnectionStateActive(),
                    authMode = AdsbAuthMode.AuthFailed
                )
            )
        )
        assertFalse(
            isAdsbReadyForAutoDismiss(
                adsbSnapshot(
                    connectionState = adsbConnectionStateBackingOff(retryAfterSec = 5),
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        )
    }

    @Test
    fun ogn_surfaceOnlyOnError() {
        assertFalse(shouldSurfaceOgnDebugPanel(ognSnapshot(OgnConnectionState.DISCONNECTED)))
        assertFalse(shouldSurfaceOgnDebugPanel(ognSnapshot(OgnConnectionState.CONNECTING)))
        assertFalse(shouldSurfaceOgnDebugPanel(ognSnapshot(OgnConnectionState.CONNECTED)))
        assertTrue(shouldSurfaceOgnDebugPanel(ognSnapshot(OgnConnectionState.ERROR)))
    }

    @Test
    fun adsb_surfaceOnErrorOrBackingOff() {
        assertFalse(
            shouldSurfaceAdsbDebugPanel(
                adsbSnapshot(
                    connectionState = adsbConnectionStateDisabled(),
                    authMode = AdsbAuthMode.Anonymous
                )
            )
        )
        assertFalse(
            shouldSurfaceAdsbDebugPanel(
                adsbSnapshot(
                    connectionState = adsbConnectionStateActive(),
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        )
        assertTrue(
            shouldSurfaceAdsbDebugPanel(
                adsbSnapshot(
                    connectionState = adsbConnectionStateBackingOff(retryAfterSec = 5),
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        )
        assertTrue(
            shouldSurfaceAdsbDebugPanel(
                adsbSnapshot(
                    connectionState = adsbConnectionStateError("Network unavailable"),
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        )
    }

    @Test
    fun adsb_issueFlashOnErrorOrBackingOff() {
        assertFalse(
            shouldFlashAdsbIssue(
                adsbSnapshot(
                    connectionState = adsbConnectionStateDisabled(),
                    authMode = AdsbAuthMode.Anonymous
                )
            )
        )
        assertFalse(
            shouldFlashAdsbIssue(
                adsbSnapshot(
                    connectionState = adsbConnectionStateActive(),
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        )
        assertTrue(
            shouldFlashAdsbIssue(
                adsbSnapshot(
                    connectionState = adsbConnectionStateBackingOff(retryAfterSec = 5),
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        )
        assertTrue(
            shouldFlashAdsbIssue(
                adsbSnapshot(
                    connectionState = adsbConnectionStateError("Network unavailable"),
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        )
    }

    @Test
    fun adsb_persistentStatusSurfacedForDegradedStates() {
        assertTrue(
            shouldSurfacePersistentAdsbStatus(
                adsbSnapshot(
                    connectionState = adsbConnectionStateError("Network unavailable"),
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        )
        assertTrue(
            shouldSurfacePersistentAdsbStatus(
                adsbSnapshot(
                    connectionState = adsbConnectionStateBackingOff(retryAfterSec = 5),
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        )
        assertTrue(
            shouldSurfacePersistentAdsbStatus(
                adsbSnapshot(
                    connectionState = adsbConnectionStateActive(),
                    authMode = AdsbAuthMode.AuthFailed
                )
            )
        )
        assertFalse(
            shouldSurfacePersistentAdsbStatus(
                adsbSnapshot(
                    connectionState = adsbConnectionStateActive(),
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        )
    }

    @Test
    fun adsb_persistentStatusPresentation_mapsTitleAndReason() {
        val offline = persistentAdsbStatusPresentation(
            adsbSnapshot(
                connectionState = adsbConnectionStateError("Socket timeout"),
                authMode = AdsbAuthMode.Authenticated,
                lastNetworkFailureKind = AdsbNetworkFailureKind.TIMEOUT
            )
        )
        assertEquals("ADS-B Offline", offline.first)
        assertEquals("Network: Socket timeout", offline.second)

        val backoff = persistentAdsbStatusPresentation(
            adsbSnapshot(
                connectionState = adsbConnectionStateBackingOff(retryAfterSec = 5),
                authMode = AdsbAuthMode.Authenticated
            )
        )
        assertEquals("ADS-B Backoff", backoff.first)
        assertEquals("Waiting before next retry", backoff.second)

        val authFailed = persistentAdsbStatusPresentation(
            adsbSnapshot(
                connectionState = adsbConnectionStateActive(),
                authMode = AdsbAuthMode.AuthFailed
            )
        )
        assertEquals("ADS-B Credential Issue", authFailed.first)
        assertEquals("Credential auth failed; using anonymous fallback", authFailed.second)
    }

    @Test
    fun ogn_debugPanelHiddenWhileStartupStates() {
        assertTrue(
            shouldHideOgnDebugPanelWhileConnecting(
                ognSnapshot(OgnConnectionState.CONNECTING)
            )
        )
        assertTrue(
            shouldHideOgnDebugPanelWhileConnecting(
                ognSnapshot(OgnConnectionState.DISCONNECTED)
            )
        )
        assertFalse(
            shouldHideOgnDebugPanelWhileConnecting(
                ognSnapshot(OgnConnectionState.CONNECTED)
            )
        )
    }

    @Test
    fun adsb_debugPanelHiddenWhileStartupStates() {
        assertTrue(
            shouldHideAdsbDebugPanelWhileConnecting(
                adsbSnapshot(
                    connectionState = adsbConnectionStateDisabled(),
                    authMode = AdsbAuthMode.Anonymous
                )
            )
        )
        assertFalse(
            shouldHideAdsbDebugPanelWhileConnecting(
                adsbSnapshot(
                    connectionState = adsbConnectionStateBackingOff(retryAfterSec = 5),
                    authMode = AdsbAuthMode.Anonymous
                )
            )
        )
        assertFalse(
            shouldHideAdsbDebugPanelWhileConnecting(
                adsbSnapshot(
                    connectionState = adsbConnectionStateActive(),
                    authMode = AdsbAuthMode.Anonymous
                )
            )
        )
    }

    private fun ognSnapshot(connectionState: OgnConnectionState): OgnTrafficSnapshot =
        OgnTrafficSnapshot(
            targets = emptyList(),
            connectionState = connectionState,
            lastError = null,
            subscriptionCenterLat = null,
            subscriptionCenterLon = null,
            receiveRadiusKm = 150,
            ddbCacheAgeMs = null,
            reconnectBackoffMs = null,
            lastReconnectWallMs = null,
            activeSubscriptionCenterLat = null,
            activeSubscriptionCenterLon = null
        )

    private fun adsbSnapshot(
        connectionState: AdsbConnectionState,
        authMode: AdsbAuthMode,
        lastError: String? = null,
        lastHttpStatus: Int? = null,
        lastNetworkFailureKind: AdsbNetworkFailureKind? = null
    ): AdsbTrafficSnapshot =
        AdsbTrafficSnapshot(
            targets = emptyList(),
            connectionState = connectionState,
            authMode = authMode,
            centerLat = null,
            centerLon = null,
            receiveRadiusKm = 10,
            fetchedCount = 0,
            withinRadiusCount = 0,
            withinVerticalCount = 0,
            filteredByVerticalCount = 0,
            cappedCount = 0,
            displayedCount = 0,
            lastHttpStatus = lastHttpStatus,
            remainingCredits = null,
            lastPollMonoMs = null,
            lastSuccessMonoMs = null,
            lastError = lastError,
            lastNetworkFailureKind = lastNetworkFailureKind
        )
}

