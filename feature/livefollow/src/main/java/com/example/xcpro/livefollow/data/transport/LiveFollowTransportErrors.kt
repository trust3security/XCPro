package com.example.xcpro.livefollow.data.transport

import com.example.xcpro.core.common.logging.AppLogger
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal enum class LiveFollowTransportFailureSurface {
    LIVEFOLLOW,
    XC_ACCOUNT
}

internal enum class LiveFollowTransportFailureKind {
    DNS,
    TIMEOUT,
    CONNECT,
    NO_ROUTE,
    TLS,
    UNKNOWN
}

internal data class LiveFollowTransportFailure(
    val kind: LiveFollowTransportFailureKind,
    val userMessage: String
)

internal fun logAndNormalizeLiveFollowTransportFailure(
    tag: String,
    operationLabel: String,
    surface: LiveFollowTransportFailureSurface,
    throwable: IOException
): LiveFollowTransportFailure {
    val kind = throwable.toLiveFollowTransportFailureKind()
    val failure = LiveFollowTransportFailure(
        kind = kind,
        userMessage = surface.userMessageFor(kind)
    )
    AppLogger.w(
        tag,
        "$operationLabel transport failure (${failure.kind.name})",
        throwable
    )
    return failure
}

private fun IOException.toLiveFollowTransportFailureKind(): LiveFollowTransportFailureKind = when (this) {
    is UnknownHostException -> LiveFollowTransportFailureKind.DNS
    is SocketTimeoutException,
    is InterruptedIOException -> LiveFollowTransportFailureKind.TIMEOUT
    is NoRouteToHostException -> LiveFollowTransportFailureKind.NO_ROUTE
    is ConnectException -> LiveFollowTransportFailureKind.CONNECT
    is SSLException -> LiveFollowTransportFailureKind.TLS
    else -> LiveFollowTransportFailureKind.UNKNOWN
}

private fun LiveFollowTransportFailureSurface.userMessageFor(
    kind: LiveFollowTransportFailureKind
): String = when (this) {
    LiveFollowTransportFailureSurface.LIVEFOLLOW -> when (kind) {
        LiveFollowTransportFailureKind.TLS ->
            "LiveFollow secure connection failed. Retry in a moment."

        else -> "LiveFollow network error. Check connection and retry."
    }

    LiveFollowTransportFailureSurface.XC_ACCOUNT -> when (kind) {
        LiveFollowTransportFailureKind.TLS ->
            "XC account secure connection failed. Retry in a moment."

        else -> "XC account network error. Check connection and retry."
    }
}
