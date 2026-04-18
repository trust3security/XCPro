package com.trust3.xcpro.livefollow.account

import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.livefollow.di.LiveFollowHttpClient
import com.trust3.xcpro.livefollow.data.transport.LiveFollowTransportFailureSurface
import com.trust3.xcpro.livefollow.data.transport.logAndNormalizeLiveFollowTransportFailure
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

sealed interface XcAccountRemoteResult<out T> {
    data class Success<T>(
        val value: T
    ) : XcAccountRemoteResult<T>

    data class Failure(
        val error: XcAccountApiError
    ) : XcAccountRemoteResult<Nothing>
}

@Singleton
class CurrentApiXcAccountDataSource @Inject constructor(
    @LiveFollowHttpClient private val httpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun exchangeGoogleIdToken(
        googleIdToken: String
    ): XcAccountRemoteResult<XcAccountSession> = withContext(ioDispatcher) {
        val payload = JsonObject().apply {
            addProperty("google_id_token", googleIdToken.trim())
        }
        executeRequest(
            request = Request.Builder()
                .url(baseUrlBuilder().addPathSegments("api/v2/auth/google/exchange").build())
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .build(),
            parser = ::parseCurrentApiXcGoogleExchangeResponse
        )
    }

    suspend fun fetchMe(
        accessToken: String
    ): XcAccountRemoteResult<XcAccountMePayload> = withContext(ioDispatcher) {
        executeRequest(
            request = Request.Builder()
                .url(baseUrlBuilder().addPathSegments("api/v2/me").build())
                .get()
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, bearerHeader(accessToken))
                .build(),
            parser = ::parseCurrentApiXcMeResponse
        )
    }

    suspend fun patchProfile(
        accessToken: String,
        request: XcProfileUpdateRequest
    ): XcAccountRemoteResult<XcPilotProfile> = withContext(ioDispatcher) {
        val payload = JsonObject().apply {
            addProperty("handle", request.handle)
            addProperty("display_name", request.displayName)
            if (request.compNumber != null) {
                addProperty("comp_number", request.compNumber)
            } else {
                add("comp_number", JsonNull.INSTANCE)
            }
        }
        executeRequest(
            request = Request.Builder()
                .url(baseUrlBuilder().addPathSegments("api/v2/me/profile").build())
                .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, bearerHeader(accessToken))
                .build(),
            parser = ::parseCurrentApiXcProfileResponse
        )
    }

    suspend fun patchPrivacy(
        accessToken: String,
        request: XcPrivacyUpdateRequest
    ): XcAccountRemoteResult<XcPrivacySettings> = withContext(ioDispatcher) {
        val payload = JsonObject().apply {
            addProperty("discoverability", request.discoverability.wireValue)
            addProperty("follow_policy", request.followPolicy.wireValue)
            addProperty("default_live_visibility", request.defaultLiveVisibility.wireValue)
            addProperty(
                "connection_list_visibility",
                request.connectionListVisibility.wireValue
            )
        }
        executeRequest(
            request = Request.Builder()
                .url(baseUrlBuilder().addPathSegments("api/v2/me/privacy").build())
                .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, bearerHeader(accessToken))
                .build(),
            parser = ::parseCurrentApiXcPrivacyResponse
        )
    }

    suspend fun searchUsers(
        accessToken: String,
        query: String
    ): XcAccountRemoteResult<List<XcSearchPilot>> = withContext(ioDispatcher) {
        executeRequest(
            request = Request.Builder()
                .url(
                    baseUrlBuilder()
                        .addPathSegments("api/v2/users/search")
                        .addQueryParameter("q", query.trim())
                        .build()
                )
                .get()
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, bearerHeader(accessToken))
                .build(),
            parser = ::parseCurrentApiXcSearchResponse
        )
    }

    suspend fun createFollowRequest(
        accessToken: String,
        targetUserId: String
    ): XcAccountRemoteResult<XcFollowRequestItem> = withContext(ioDispatcher) {
        val payload = JsonObject().apply {
            addProperty("target_user_id", targetUserId)
        }
        executeRequest(
            request = Request.Builder()
                .url(baseUrlBuilder().addPathSegments("api/v2/follow-requests").build())
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, bearerHeader(accessToken))
                .build(),
            parser = ::parseCurrentApiXcFollowRequestResponse
        )
    }

    suspend fun fetchIncomingFollowRequests(
        accessToken: String
    ): XcAccountRemoteResult<List<XcFollowRequestItem>> = withContext(ioDispatcher) {
        executeRequest(
            request = Request.Builder()
                .url(baseUrlBuilder().addPathSegments("api/v2/follow-requests/incoming").build())
                .get()
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, bearerHeader(accessToken))
                .build(),
            parser = ::parseCurrentApiXcFollowRequestsResponse
        )
    }

    suspend fun fetchOutgoingFollowRequests(
        accessToken: String
    ): XcAccountRemoteResult<List<XcFollowRequestItem>> = withContext(ioDispatcher) {
        executeRequest(
            request = Request.Builder()
                .url(baseUrlBuilder().addPathSegments("api/v2/follow-requests/outgoing").build())
                .get()
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, bearerHeader(accessToken))
                .build(),
            parser = ::parseCurrentApiXcFollowRequestsResponse
        )
    }

    suspend fun acceptFollowRequest(
        accessToken: String,
        requestId: String
    ): XcAccountRemoteResult<XcFollowRequestItem> = withContext(ioDispatcher) {
        executeRequest(
            request = Request.Builder()
                .url(
                    baseUrlBuilder()
                        .addPathSegments("api/v2/follow-requests/$requestId/accept")
                        .build()
                )
                .post(EMPTY_JSON_BODY)
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, bearerHeader(accessToken))
                .build(),
            parser = ::parseCurrentApiXcFollowRequestResponse
        )
    }

    suspend fun declineFollowRequest(
        accessToken: String,
        requestId: String
    ): XcAccountRemoteResult<XcFollowRequestItem> = withContext(ioDispatcher) {
        executeRequest(
            request = Request.Builder()
                .url(
                    baseUrlBuilder()
                        .addPathSegments("api/v2/follow-requests/$requestId/decline")
                        .build()
                )
                .post(EMPTY_JSON_BODY)
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, bearerHeader(accessToken))
                .build(),
            parser = ::parseCurrentApiXcFollowRequestResponse
        )
    }

    private fun <T> executeRequest(
        request: Request,
        parser: (String) -> T
    ): XcAccountRemoteResult<T> {
        return try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return XcAccountRemoteResult.Failure(
                        parseCurrentApiXcApiError(
                            responseBody = responseBody,
                            httpCode = response.code
                        )
                    )
                }
                val parsed = runCatching { parser(responseBody) }.getOrElse { cause ->
                    return XcAccountRemoteResult.Failure(
                        XcAccountApiError(
                            message = cause.message?.trim().takeUnless { it.isNullOrEmpty() }
                                ?: "Malformed XCPro account payload",
                            httpCode = response.code
                        )
                    )
                }
                XcAccountRemoteResult.Success(parsed)
            }
        } catch (e: IOException) {
            val message = logAndNormalizeLiveFollowTransportFailure(
                tag = TAG,
                operationLabel = "XC account request ${request.url.encodedPath}",
                surface = LiveFollowTransportFailureSurface.XC_ACCOUNT,
                throwable = e
            ).userMessage
            XcAccountRemoteResult.Failure(
                XcAccountApiError(
                    message = message
                )
            )
        }
    }

    private fun baseUrlBuilder() = BASE_URL.toHttpUrl().newBuilder()

    private fun bearerHeader(accessToken: String): String = "Bearer ${accessToken.trim()}"

    private companion object {
        private const val BASE_URL = "https://api.xcpro.com.au/"
        private const val CONTENT_TYPE_JSON = "application/json"
        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private val JSON_MEDIA_TYPE = CONTENT_TYPE_JSON.toMediaType()
        private val EMPTY_JSON_BODY = "{}".toRequestBody(JSON_MEDIA_TYPE)
        private const val TAG = "XcAccountRemote"
    }
}
