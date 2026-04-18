package com.trust3.xcpro.livefollow.data.session

import com.trust3.xcpro.livefollow.model.LiveFollowTransportAvailability
import com.trust3.xcpro.livefollow.model.liveFollowAvailableTransport
import com.trust3.xcpro.livefollow.model.liveFollowDegradedTransport
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal data class StoredPilotTransport(
    val sessionId: String? = null,
    val shareCode: String? = null,
    val writeToken: String? = null,
    val lastUploadedFixWallMs: Long? = null,
    val lastUploadedTaskPayloadJson: String? = null
)

internal fun buildAuthenticatedSessionStartRequest(
    accessToken: String,
    visibility: LiveFollowSessionVisibility
): Request {
    return Request.Builder()
        .url(liveFollowBaseUrlBuilder().addPathSegments("api/v2/live/session/start").build())
        .post(
            """{"visibility":"${escapeJson(visibility.wireValue)}"}"""
                .toRequestBody(jsonMediaType())
        )
        .header(headerAccept(), contentTypeJson())
        .header(headerContentType(), contentTypeJson())
        .header(headerAuthorization(), bearerHeader(accessToken))
        .build()
}

internal fun buildPublicSessionStartRequest(): Request {
    return Request.Builder()
        .url(liveFollowBaseUrlBuilder().addPathSegments("api/v1/session/start").build())
        .post(emptyRequestBody())
        .header(headerAccept(), contentTypeJson())
        .build()
}

internal fun buildVisibilityPatchRequest(
    sessionId: String,
    accessToken: String,
    visibility: LiveFollowSessionVisibility
): Request {
    return Request.Builder()
        .url(
            liveFollowBaseUrlBuilder()
                .addPathSegments("api/v2/live/session")
                .addPathSegment(sessionId)
                .addPathSegment("visibility")
                .build()
        )
        .patch(
            """{"visibility":"${escapeJson(visibility.wireValue)}"}"""
                .toRequestBody(jsonMediaType())
        )
        .header(headerAccept(), contentTypeJson())
        .header(headerContentType(), contentTypeJson())
        .header(headerAuthorization(), bearerHeader(accessToken))
        .build()
}

internal fun buildSessionEndRequest(
    sessionId: String,
    writeToken: String
): Request {
    val requestBody = """{"session_id":"${escapeJson(sessionId)}"}"""
        .toRequestBody(jsonMediaType())
    return Request.Builder()
        .url(liveFollowBaseUrlBuilder().addPathSegments("api/v1/session/end").build())
        .post(requestBody)
        .header(headerAccept(), contentTypeJson())
        .header(headerSessionToken(), writeToken)
        .build()
}

internal fun buildPublicLiveSessionRequest(
    sessionId: String
): Request {
    return Request.Builder()
        .url(
            liveFollowBaseUrlBuilder()
                .addPathSegments("api/v1/live")
                .addPathSegment(sessionId)
                .build()
        )
        .get()
        .header(headerAccept(), contentTypeJson())
        .build()
}

internal fun buildAuthenticatedLiveSessionRequest(
    sessionId: String,
    accessToken: String
): Request {
    return Request.Builder()
        .url(
            liveFollowBaseUrlBuilder()
                .addPathSegments("api/v2/live/session")
                .addPathSegment(sessionId)
                .build()
        )
        .get()
        .header(headerAccept(), contentTypeJson())
        .header(headerAuthorization(), bearerHeader(accessToken))
        .build()
}

internal fun buildLiveShareCodeRequest(
    shareCode: String
): Request {
    return Request.Builder()
        .url(
            liveFollowBaseUrlBuilder()
                .addPathSegments("api/v1/live/share")
                .addPathSegment(shareCode)
                .build()
        )
        .get()
        .header(headerAccept(), contentTypeJson())
        .build()
}

internal fun buildPilotPositionUploadRequest(
    writeToken: String,
    requestJson: String
): Request {
    return Request.Builder()
        .url(liveFollowBaseUrlBuilder().addPathSegments("api/v1/position").build())
        .post(requestJson.toRequestBody(jsonMediaType()))
        .header(headerAccept(), contentTypeJson())
        .header(headerSessionToken(), writeToken)
        .build()
}

internal fun buildPilotTaskUploadRequest(
    writeToken: String,
    requestJson: String
): Request {
    return Request.Builder()
        .url(liveFollowBaseUrlBuilder().addPathSegments("api/v1/task/upsert").build())
        .post(requestJson.toRequestBody(jsonMediaType()))
        .header(headerAccept(), contentTypeJson())
        .header(headerSessionToken(), writeToken)
        .build()
}

internal fun availabilityForLiveFollowHttpFailure(
    httpCode: Int,
    message: String
): LiveFollowTransportAvailability {
    return when {
        httpCode >= 500 -> liveFollowDegradedTransport(message)
        else -> liveFollowAvailableTransport()
    }
}

internal fun liveFollowBaseUrlBuilder(): HttpUrl.Builder =
    "https://api.xcpro.com.au/".toHttpUrl().newBuilder()

internal fun bearerHeader(accessToken: String): String = "Bearer $accessToken"

internal fun escapeJson(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

private fun contentTypeJson(): String = "application/json"

private fun headerAccept(): String = "Accept"

private fun headerAuthorization(): String = "Authorization"

private fun headerContentType(): String = "Content-Type"

private fun headerSessionToken(): String = "X-Session-Token"

private fun jsonMediaType() = contentTypeJson().toMediaType()

private fun emptyRequestBody() = ByteArray(0).toRequestBody()
