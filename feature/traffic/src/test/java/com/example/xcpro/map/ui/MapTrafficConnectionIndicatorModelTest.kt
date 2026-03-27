package com.example.xcpro.map.ui

import com.example.xcpro.map.AdsbAuthMode
import com.example.xcpro.map.AdsbTrafficSnapshot
import com.example.xcpro.map.OgnConnectionState
import com.example.xcpro.map.OgnTrafficSnapshot
import com.example.xcpro.map.adsbConnectionStateActive
import com.example.xcpro.map.adsbConnectionStateBackingOff
import com.example.xcpro.map.adsbConnectionStateDisabled
import com.example.xcpro.map.adsbConnectionStateError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapTrafficConnectionIndicatorModelTest {

    @Test
    fun build_returnsGreenOgnIndicator_whenOgnIsConnected() {
        val state = buildState(
            ognOverlayEnabled = true,
            ognSnapshot = ognSnapshot(connectionState = OgnConnectionState.CONNECTED)
        )

        assertEquals("OGN", state.ogn?.sourceLabel)
        assertEquals(TrafficConnectionIndicatorTone.GREEN, state.ogn?.tone)
    }

    @Test
    fun build_returnsRedOgnIndicator_whenOgnIsInError() {
        val state = buildState(
            ognOverlayEnabled = true,
            ognSnapshot = ognSnapshot(connectionState = OgnConnectionState.ERROR)
        )

        assertEquals("OGN", state.ogn?.sourceLabel)
        assertEquals(TrafficConnectionIndicatorTone.RED, state.ogn?.tone)
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
    fun build_returnsGreenAdsbIndicator_whenAdsbIsActiveAndAuthHealthy() {
        val state = buildState(
            adsbOverlayEnabled = true,
            adsbSnapshot = adsbSnapshot(
                connectionState = adsbConnectionStateActive(),
                authMode = AdsbAuthMode.Authenticated
            )
        )

        assertEquals("ADS-B", state.adsb?.sourceLabel)
        assertEquals(TrafficConnectionIndicatorTone.GREEN, state.adsb?.tone)
    }

    @Test
    fun build_returnsRedAdsbIndicator_whenAdsbIsBackingOffOrInError() {
        val backingOff = buildState(
            adsbOverlayEnabled = true,
            adsbSnapshot = adsbSnapshot(connectionState = adsbConnectionStateBackingOff(15))
        )
        val inError = buildState(
            adsbOverlayEnabled = true,
            adsbSnapshot = adsbSnapshot(connectionState = adsbConnectionStateError("offline"))
        )

        assertEquals(TrafficConnectionIndicatorTone.RED, backingOff.adsb?.tone)
        assertEquals(TrafficConnectionIndicatorTone.RED, inError.adsb?.tone)
    }

    @Test
    fun build_returnsRedAdsbIndicator_whenAuthFailedEvenIfConnectionIsActive() {
        val state = buildState(
            adsbOverlayEnabled = true,
            adsbSnapshot = adsbSnapshot(
                connectionState = adsbConnectionStateActive(),
                authMode = AdsbAuthMode.AuthFailed
            )
        )

        assertEquals("ADS-B", state.adsb?.sourceLabel)
        assertEquals(TrafficConnectionIndicatorTone.RED, state.adsb?.tone)
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
        connectionState: OgnConnectionState
    ): OgnTrafficSnapshot = OgnTrafficSnapshot(
        targets = emptyList(),
        connectionState = connectionState,
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
        authMode: AdsbAuthMode = AdsbAuthMode.Anonymous
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
        lastHttpStatus = null,
        remainingCredits = null,
        lastPollMonoMs = null,
        lastSuccessMonoMs = null,
        lastError = null
    )
}
