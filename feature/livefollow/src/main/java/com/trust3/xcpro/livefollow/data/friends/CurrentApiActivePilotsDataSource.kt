package com.trust3.xcpro.livefollow.data.friends

import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.livefollow.data.transport.CurrentApiLivePoint
import com.trust3.xcpro.livefollow.data.transport.LiveFollowTransportFailureSurface
import com.trust3.xcpro.livefollow.data.transport.logAndNormalizeLiveFollowTransportFailure
import com.trust3.xcpro.livefollow.data.transport.parseCurrentApiActivePilotsResponse
import com.trust3.xcpro.livefollow.data.transport.parseCurrentApiErrorMessage
import com.trust3.xcpro.livefollow.di.LiveFollowHttpClient
import com.trust3.xcpro.livefollow.model.LiveFollowActivePilot
import com.trust3.xcpro.livefollow.model.LiveFollowActivePilotPoint
import com.trust3.xcpro.livefollow.model.liveFollowAvailableTransport
import com.trust3.xcpro.livefollow.model.liveFollowDegradedTransport
import com.trust3.xcpro.livefollow.model.liveFollowUnavailableTransport
import com.trust3.xcpro.livefollow.normalizeLiveFollowShareCode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class CurrentApiActivePilotsDataSource @Inject constructor(
    @LiveFollowHttpClient private val httpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ActivePilotsDataSource {

    override suspend fun fetchActivePilots(): ActivePilotsFetchResult = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(baseUrlBuilder().addPathSegments("api/v1/live/active").build())
            .get()
            .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = parseCurrentApiErrorMessage(
                        responseBody = responseBody,
                        httpCode = response.code
                    )
                    return@withContext ActivePilotsFetchResult.Failure(
                        availability = liveFollowDegradedTransport(message),
                        message = message
                    )
                }

                val parsed = runCatching { parseCurrentApiActivePilotsResponse(responseBody) }
                    .getOrElse { cause ->
                        val message = activePilotsPayloadFailureMessage(cause)
                        return@withContext ActivePilotsFetchResult.Failure(
                            availability = liveFollowDegradedTransport(message),
                            message = message
                        )
                    }
                val items = parsed.items.mapNotNull(::mapActivePilot)
                ActivePilotsFetchResult.Success(items)
            }
        } catch (e: IOException) {
            val message = logAndNormalizeLiveFollowTransportFailure(
                tag = TAG,
                operationLabel = "Active pilots fetch",
                surface = LiveFollowTransportFailureSurface.LIVEFOLLOW,
                throwable = e
            ).userMessage
            ActivePilotsFetchResult.Failure(
                availability = liveFollowUnavailableTransport(message),
                message = message
            )
        }
    }

    private fun mapActivePilot(
        item: com.trust3.xcpro.livefollow.data.transport.CurrentApiActivePilotItem
    ): LiveFollowActivePilot? {
        val shareCode = normalizeLiveFollowShareCode(item.shareCode) ?: return null
        return LiveFollowActivePilot(
            sessionId = item.sessionId,
            shareCode = shareCode,
            status = item.status.trim(),
            displayLabel = item.displayLabel?.trim()?.takeIf { it.isNotEmpty() } ?: shareCode,
            lastPositionWallMs = item.lastPositionWallMs,
            latest = item.latest?.toActivePilotPoint()
        )
    }

    private fun CurrentApiLivePoint.toActivePilotPoint(): LiveFollowActivePilotPoint =
        LiveFollowActivePilotPoint(
            latitudeDeg = latitudeDeg,
            longitudeDeg = longitudeDeg,
            altitudeMslMeters = altitudeMslMeters,
            groundSpeedMs = groundSpeedMs,
            headingDeg = headingDeg,
            fixWallMs = fixWallMs
        )

    private fun baseUrlBuilder() = LIVEFOLLOW_BASE_URL.toHttpUrl().newBuilder()

    private fun activePilotsPayloadFailureMessage(cause: Throwable): String {
        val message = cause.message?.trim()
        return when {
            message.isNullOrEmpty() -> "Malformed LiveFollow active-pilots payload"
            message.equals("Invalid LiveFollow payload", ignoreCase = true) ->
                "Malformed LiveFollow active-pilots payload"

            else -> message
        }
    }

    private companion object {
        private const val LIVEFOLLOW_BASE_URL = "https://api.xcpro.com.au/"
        private const val CONTENT_TYPE_JSON = "application/json"
        private const val HEADER_ACCEPT = "Accept"
        private const val TAG = "LiveFollowActivePilots"
    }
}
