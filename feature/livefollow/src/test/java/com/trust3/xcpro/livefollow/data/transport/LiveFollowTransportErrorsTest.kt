package com.trust3.xcpro.livefollow.data.transport

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveFollowTransportErrorsTest {

    @Test
    fun unknownHost_mapsToDnsKind_withFriendlyLiveFollowMessage() {
        assertFailure(
            throwable = UnknownHostException("api.xcpro.com.au"),
            surface = LiveFollowTransportFailureSurface.LIVEFOLLOW,
            expectedKind = LiveFollowTransportFailureKind.DNS,
            expectedMessage = "LiveFollow network error. Check connection and retry."
        )
    }

    @Test
    fun socketTimeout_mapsToTimeoutKind_withFriendlyLiveFollowMessage() {
        assertFailure(
            throwable = SocketTimeoutException("timeout"),
            surface = LiveFollowTransportFailureSurface.LIVEFOLLOW,
            expectedKind = LiveFollowTransportFailureKind.TIMEOUT,
            expectedMessage = "LiveFollow network error. Check connection and retry."
        )
    }

    @Test
    fun interruptedIo_mapsToTimeoutKind_withFriendlyLiveFollowMessage() {
        assertFailure(
            throwable = InterruptedIOException("interrupted"),
            surface = LiveFollowTransportFailureSurface.LIVEFOLLOW,
            expectedKind = LiveFollowTransportFailureKind.TIMEOUT,
            expectedMessage = "LiveFollow network error. Check connection and retry."
        )
    }

    @Test
    fun connect_mapsToConnectKind_withFriendlyLiveFollowMessage() {
        assertFailure(
            throwable = ConnectException("refused"),
            surface = LiveFollowTransportFailureSurface.LIVEFOLLOW,
            expectedKind = LiveFollowTransportFailureKind.CONNECT,
            expectedMessage = "LiveFollow network error. Check connection and retry."
        )
    }

    @Test
    fun noRoute_mapsToNoRouteKind_withFriendlyLiveFollowMessage() {
        assertFailure(
            throwable = NoRouteToHostException("no route"),
            surface = LiveFollowTransportFailureSurface.LIVEFOLLOW,
            expectedKind = LiveFollowTransportFailureKind.NO_ROUTE,
            expectedMessage = "LiveFollow network error. Check connection and retry."
        )
    }

    @Test
    fun tls_mapsToSecureConnectionMessage_forAccountSurface() {
        assertFailure(
            throwable = SSLException("handshake"),
            surface = LiveFollowTransportFailureSurface.XC_ACCOUNT,
            expectedKind = LiveFollowTransportFailureKind.TLS,
            expectedMessage = "XC account secure connection failed. Retry in a moment."
        )
    }

    @Test
    fun genericIo_mapsToUnknownKind_withFriendlyAccountMessage() {
        assertFailure(
            throwable = IOException("boom"),
            surface = LiveFollowTransportFailureSurface.XC_ACCOUNT,
            expectedKind = LiveFollowTransportFailureKind.UNKNOWN,
            expectedMessage = "XC account network error. Check connection and retry."
        )
    }

    private fun assertFailure(
        throwable: IOException,
        surface: LiveFollowTransportFailureSurface,
        expectedKind: LiveFollowTransportFailureKind,
        expectedMessage: String
    ) {
        val failure = logAndNormalizeLiveFollowTransportFailure(
            tag = "LiveFollowTransportErrorsTest",
            operationLabel = "transport test",
            surface = surface,
            throwable = throwable
        )
        assertEquals(expectedKind, failure.kind)
        assertEquals(expectedMessage, failure.userMessage)
    }
}
