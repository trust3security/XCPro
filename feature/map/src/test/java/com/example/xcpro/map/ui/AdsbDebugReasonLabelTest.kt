package com.example.xcpro.map.ui

import com.example.xcpro.map.AdsbAuthMode
import com.example.xcpro.map.AdsbNetworkFailureKind
import com.example.xcpro.map.AdsbTrafficSnapshot
import com.example.xcpro.map.ADSB_ERROR_CIRCUIT_BREAKER_OPEN
import com.example.xcpro.map.ADSB_ERROR_CIRCUIT_BREAKER_PROBE
import com.example.xcpro.map.adsbConnectionStateActive
import com.example.xcpro.map.adsbConnectionStateBackingOff
import com.example.xcpro.map.adsbConnectionStateError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

import com.example.xcpro.map.AdsbConnectionState

class AdsbDebugReasonLabelTest {

    @Test
    fun breakerOpen_usesCircuitReasonLabel() {
        val snapshot = snapshot(
            connectionState = adsbConnectionStateError(ADSB_ERROR_CIRCUIT_BREAKER_OPEN),
            lastError = ADSB_ERROR_CIRCUIT_BREAKER_OPEN
        )
        assertEquals("Circuit breaker open", snapshot.debugReasonLabel())
    }

    @Test
    fun breakerProbe_usesHalfOpenReasonLabel() {
        val snapshot = snapshot(
            connectionState = adsbConnectionStateError(ADSB_ERROR_CIRCUIT_BREAKER_PROBE),
            lastError = ADSB_ERROR_CIRCUIT_BREAKER_PROBE
        )
        assertEquals("Circuit breaker half-open probe", snapshot.debugReasonLabel())
    }

    @Test
    fun authFailed_usesCredentialFallbackReason() {
        val snapshot = snapshot(
            connectionState = adsbConnectionStateError("Credential error"),
            authMode = AdsbAuthMode.AuthFailed
        )
        assertEquals("Credential auth failed; using anonymous fallback", snapshot.debugReasonLabel())
    }

    @Test
    fun anonymous429_usesQuotaReasonLabel() {
        val snapshot = snapshot(
            connectionState = adsbConnectionStateBackingOff(retryAfterSec = 30),
            authMode = AdsbAuthMode.Anonymous,
            lastHttpStatus = 429
        )
        assertEquals("Anonymous quota exceeded (OpenSky 429)", snapshot.debugReasonLabel())
    }

    @Test
    fun networkFailure_usesNetworkReasonLabel() {
        val snapshot = snapshot(
            connectionState = adsbConnectionStateError("Socket timeout"),
            lastNetworkFailureKind = AdsbNetworkFailureKind.TIMEOUT
        )
        assertEquals("Network: Socket timeout", snapshot.debugReasonLabel())
    }

    @Test
    fun activeState_withoutFailureReason_returnsNull() {
        val snapshot = snapshot(connectionState = adsbConnectionStateActive())
        assertNull(snapshot.debugReasonLabel())
    }

    private fun snapshot(
        connectionState: AdsbConnectionState,
        authMode: AdsbAuthMode = AdsbAuthMode.Anonymous,
        lastHttpStatus: Int? = null,
        lastError: String? = null,
        lastNetworkFailureKind: AdsbNetworkFailureKind? = null
    ): AdsbTrafficSnapshot = AdsbTrafficSnapshot(
        targets = emptyList(),
        connectionState = connectionState,
        authMode = authMode,
        centerLat = null,
        centerLon = null,
        receiveRadiusKm = 10,
        fetchedCount = 0,
        withinRadiusCount = 0,
        displayedCount = 0,
        lastHttpStatus = lastHttpStatus,
        remainingCredits = null,
        lastPollMonoMs = null,
        lastSuccessMonoMs = null,
        lastError = lastError,
        lastNetworkFailureKind = lastNetworkFailureKind
    )
}

