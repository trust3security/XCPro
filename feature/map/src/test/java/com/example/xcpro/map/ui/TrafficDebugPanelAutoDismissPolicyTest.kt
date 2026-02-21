package com.example.xcpro.map.ui

import com.example.xcpro.adsb.AdsbAuthMode
import com.example.xcpro.adsb.AdsbConnectionState
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.ogn.OgnConnectionState
import com.example.xcpro.ogn.OgnTrafficSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
                    connectionState = AdsbConnectionState.Active,
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        )
        assertTrue(
            isAdsbReadyForAutoDismiss(
                adsbSnapshot(
                    connectionState = AdsbConnectionState.Active,
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
                    connectionState = AdsbConnectionState.Active,
                    authMode = AdsbAuthMode.AuthFailed
                )
            )
        )
        assertFalse(
            isAdsbReadyForAutoDismiss(
                adsbSnapshot(
                    connectionState = AdsbConnectionState.BackingOff(retryAfterSec = 5),
                    authMode = AdsbAuthMode.Authenticated
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
        authMode: AdsbAuthMode
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
            lastHttpStatus = null,
            remainingCredits = null,
            lastPollMonoMs = null,
            lastSuccessMonoMs = null,
            lastError = null
        )
}
