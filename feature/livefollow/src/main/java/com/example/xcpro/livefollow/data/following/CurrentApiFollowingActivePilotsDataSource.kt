package com.example.xcpro.livefollow.data.following

import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.livefollow.account.XcAccountRepository
import com.example.xcpro.livefollow.data.transport.CurrentApiLivePoint
import com.example.xcpro.livefollow.data.transport.LiveFollowTransportFailureSurface
import com.example.xcpro.livefollow.data.transport.logAndNormalizeLiveFollowTransportFailure
import com.example.xcpro.livefollow.data.transport.parseCurrentApiErrorMessage
import com.example.xcpro.livefollow.data.transport.parseCurrentApiFollowingActivePilotsResponse
import com.example.xcpro.livefollow.di.LiveFollowHttpClient
import com.example.xcpro.livefollow.model.LiveFollowActivePilotPoint
import com.example.xcpro.livefollow.model.LiveFollowFollowingPilot
import com.example.xcpro.livefollow.model.liveFollowDegradedTransport
import com.example.xcpro.livefollow.model.liveFollowUnavailableTransport
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class CurrentApiFollowingActivePilotsDataSource @Inject constructor(
    private val xcAccountRepository: XcAccountRepository,
    @LiveFollowHttpClient private val httpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : FollowingActivePilotsDataSource {

    override suspend fun fetchFollowingActivePilots(): FollowingActivePilotsFetchResult =
        withContext(ioDispatcher) {
            val accessToken = xcAccountRepository.state.value.session?.accessToken
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@withContext FollowingActivePilotsFetchResult.SignedOut

            val request = Request.Builder()
                .url(baseUrlBuilder().addPathSegments("api/v2/live/following/active").build())
                .get()
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, "Bearer $accessToken")
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = parseCurrentApiErrorMessage(
                            responseBody = responseBody,
                            httpCode = response.code
                        )
                        return@withContext FollowingActivePilotsFetchResult.Failure(
                            availability = liveFollowDegradedTransport(message),
                            message = message
                        )
                    }

                    val parsed = runCatching {
                        parseCurrentApiFollowingActivePilotsResponse(responseBody)
                    }.getOrElse { cause ->
                        val message = cause.message ?: "Malformed LiveFollow following-live payload"
                        return@withContext FollowingActivePilotsFetchResult.Failure(
                            availability = liveFollowDegradedTransport(message),
                            message = message
                        )
                    }
                    FollowingActivePilotsFetchResult.Success(
                        parsed.items.map { item ->
                            LiveFollowFollowingPilot(
                                sessionId = item.sessionId,
                                userId = item.userId,
                                visibility = item.visibility,
                                shareCode = item.shareCode,
                                status = item.status,
                                displayLabel = item.displayLabel?.trim()?.takeIf { it.isNotEmpty() }
                                    ?: item.userId,
                                lastPositionWallMs = item.lastPositionWallMs,
                                latest = item.latest?.toFollowingPoint()
                            )
                        }
                    )
                }
            } catch (e: IOException) {
                val message = logAndNormalizeLiveFollowTransportFailure(
                    tag = TAG,
                    operationLabel = "Following active pilots fetch",
                    surface = LiveFollowTransportFailureSurface.LIVEFOLLOW,
                    throwable = e
                ).userMessage
                FollowingActivePilotsFetchResult.Failure(
                    availability = liveFollowUnavailableTransport(message),
                    message = message
                )
            }
        }

    private fun CurrentApiLivePoint.toFollowingPoint(): LiveFollowActivePilotPoint =
        LiveFollowActivePilotPoint(
            latitudeDeg = latitudeDeg,
            longitudeDeg = longitudeDeg,
            altitudeMslMeters = altitudeMslMeters,
            groundSpeedMs = groundSpeedMs,
            headingDeg = headingDeg,
            fixWallMs = fixWallMs
        )

    private fun baseUrlBuilder() = LIVEFOLLOW_BASE_URL.toHttpUrl().newBuilder()

    private companion object {
        private const val LIVEFOLLOW_BASE_URL = "https://api.xcpro.com.au/"
        private const val CONTENT_TYPE_JSON = "application/json"
        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val TAG = "LiveFollowFollowingPilots"
    }
}
