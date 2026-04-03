package com.example.xcpro.livefollow.data.watch

import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.time.Clock
import com.example.xcpro.livefollow.account.XcAccountRepository
import com.example.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.example.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.example.xcpro.livefollow.data.session.LiveFollowWatchLookup
import com.example.xcpro.livefollow.data.session.LiveFollowWatchLookupType
import com.example.xcpro.livefollow.data.session.deriveLiveFollowWatchLookup
import com.example.xcpro.livefollow.data.transport.LiveFollowTransportFailureSurface
import com.example.xcpro.livefollow.data.transport.logAndNormalizeLiveFollowTransportFailure
import com.example.xcpro.livefollow.data.transport.parseCurrentApiErrorMessage
import com.example.xcpro.livefollow.data.transport.parseCurrentApiLiveReadResponse
import com.example.xcpro.livefollow.data.transport.preferredCurrentApiLivePoint
import com.example.xcpro.livefollow.di.LiveFollowHttpClient
import com.example.xcpro.livefollow.model.LiveFollowConfidence
import com.example.xcpro.livefollow.model.LiveFollowSourceState
import com.example.xcpro.livefollow.model.LiveFollowTaskSnapshot
import com.example.xcpro.livefollow.model.LiveFollowTransportAvailability
import com.example.xcpro.livefollow.model.liveFollowAvailableTransport
import com.example.xcpro.livefollow.model.liveFollowDegradedTransport
import com.example.xcpro.livefollow.model.liveFollowUnavailableTransport
import com.example.xcpro.livefollow.state.LiveFollowRuntimeMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class CurrentApiDirectWatchTrafficSource @Inject constructor(
    scope: CoroutineScope,
    private val clock: Clock,
    private val sessionState: StateFlow<LiveFollowSessionSnapshot>,
    private val xcAccountRepository: XcAccountRepository,
    @LiveFollowHttpClient private val httpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) : DirectWatchTrafficSource {
    private val mutableAircraft = MutableStateFlow<DirectWatchAircraftSample?>(null)
    private val mutableTask = MutableStateFlow<LiveFollowTaskSnapshot?>(null)
    private val mutableTransportAvailability = MutableStateFlow<LiveFollowTransportAvailability>(
        liveFollowAvailableTransport()
    )

    override val aircraft: StateFlow<DirectWatchAircraftSample?> = mutableAircraft.asStateFlow()
    override val task: StateFlow<LiveFollowTaskSnapshot?> = mutableTask.asStateFlow()
    override val transportAvailability: StateFlow<LiveFollowTransportAvailability> =
        mutableTransportAvailability.asStateFlow()

    init {
        require(pollIntervalMs > 0L) { "pollIntervalMs must be > 0" }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionState.collectLatest { snapshot ->
                val activeLookup = activeWatchLookup(snapshot)
                if (activeLookup == null) {
                    clearWatchState()
                    return@collectLatest
                }
                pollWatchLookup(activeLookup)
            }
        }
    }

    private suspend fun pollWatchLookup(lookup: LiveFollowWatchLookup) {
        while (currentCoroutineContext().isActive) {
            if (!shouldPollLookup(lookup)) return
            pollOnce(lookup)
            if (!currentCoroutineContext().isActive) return
            delay(pollIntervalMs)
        }
    }

    private suspend fun pollOnce(lookup: LiveFollowWatchLookup) = withContext(ioDispatcher) {
        if (!shouldPollLookup(lookup)) return@withContext
        val request = liveReadRequest(lookup)
            ?: return@withContext

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = parseCurrentApiErrorMessage(
                        responseBody = responseBody,
                        httpCode = response.code
                    )
                    mutableTransportAvailability.value = availabilityForHttpFailure(
                        httpCode = response.code,
                        message = message
                    )
                    return@withContext
                }

                val parsed = runCatching { parseCurrentApiLiveReadResponse(responseBody) }
                    .getOrElse { cause ->
                        val message = cause.message ?: "Malformed LiveFollow live payload"
                        mutableTransportAvailability.value = liveFollowDegradedTransport(message)
                        return@withContext
                    }
                mutableTask.value = parsed.task
                val point = preferredCurrentApiLivePoint(parsed)
                if (point == null) {
                    mutableAircraft.value = null
                    mutableTransportAvailability.value = liveFollowAvailableTransport()
                    return@withContext
                }

                val fixMonoMs = point.fixWallMs?.let { fixWallMs ->
                    deriveLocalFixMonoMs(
                        nowMonoMs = clock.nowMonoMs(),
                        nowWallMs = clock.nowWallMs(),
                        fixWallMs = fixWallMs
                    )
                } ?: return@withContext

                mutableAircraft.value = DirectWatchAircraftSample(
                    state = LiveFollowSourceState.VALID,
                    confidence = LiveFollowConfidence.HIGH,
                    latitudeDeg = point.latitudeDeg,
                    longitudeDeg = point.longitudeDeg,
                    altitudeMslMeters = point.altitudeMslMeters,
                    aglMeters = point.aglMeters,
                    groundSpeedMs = point.groundSpeedMs,
                    trackDeg = point.headingDeg,
                    verticalSpeedMs = null,
                    fixMonoMs = fixMonoMs,
                    fixWallMs = point.fixWallMs,
                    canonicalIdentity = null,
                    displayLabel = parsed.shareCode ?: parsed.sessionId
                )
                mutableTransportAvailability.value = liveFollowAvailableTransport()
            }
        } catch (e: IOException) {
            val message = logAndNormalizeLiveFollowTransportFailure(
                tag = TAG,
                operationLabel = "Direct watch poll",
                surface = LiveFollowTransportFailureSurface.LIVEFOLLOW,
                throwable = e
            ).userMessage
            mutableTransportAvailability.value = liveFollowUnavailableTransport(message)
        }
    }

    private fun clearWatchState() {
        mutableAircraft.value = null
        mutableTask.value = null
        mutableTransportAvailability.value = liveFollowAvailableTransport()
    }

    private fun shouldPollLookup(lookup: LiveFollowWatchLookup): Boolean =
        activeWatchLookup(sessionState.value) == lookup

    private fun liveReadUrl(lookup: LiveFollowWatchLookup) =
        LIVEFOLLOW_BASE_URL.toHttpUrl()
            .newBuilder()
            .apply {
                when (lookup.type) {
                    LiveFollowWatchLookupType.SESSION_ID -> {
                        addPathSegments("api/v1/live")
                        addPathSegment(lookup.value)
                    }

                    LiveFollowWatchLookupType.AUTHENTICATED_SESSION_ID -> {
                        addPathSegments("api/v2/live/session")
                        addPathSegment(lookup.value)
                    }

                    LiveFollowWatchLookupType.SHARE_CODE -> {
                        addPathSegments("api/v1/live/share")
                        addPathSegment(lookup.value)
                    }
                }
            }
            .build()

    private fun liveReadRequest(lookup: LiveFollowWatchLookup): Request? {
        val builder = Request.Builder()
            .url(liveReadUrl(lookup))
            .get()
            .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
        if (lookup.type != LiveFollowWatchLookupType.AUTHENTICATED_SESSION_ID) {
            return builder.build()
        }
        val accessToken = xcAccountRepository.state.value.session?.accessToken
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (accessToken == null) {
            mutableTransportAvailability.value = liveFollowUnavailableTransport(
                "Sign in is required for authenticated LiveFollow watch."
            )
            return null
        }
        return builder
            .header(HEADER_AUTHORIZATION, "Bearer $accessToken")
            .build()
    }

    private fun availabilityForHttpFailure(
        httpCode: Int,
        message: String
    ): LiveFollowTransportAvailability {
        return when {
            httpCode >= 500 -> liveFollowDegradedTransport(message)
            else -> liveFollowDegradedTransport(message)
        }
    }

    private companion object {
        private const val LIVEFOLLOW_BASE_URL = "https://api.xcpro.com.au/"
        private const val CONTENT_TYPE_JSON = "application/json"
        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val DEFAULT_POLL_INTERVAL_MS = 5_000L
        private const val TAG = "LiveFollowDirectWatch"
    }
}

private fun activeWatchLookup(
    snapshot: LiveFollowSessionSnapshot
): LiveFollowWatchLookup? {
    if (!snapshot.sideEffectsAllowed) return null
    if (snapshot.runtimeMode != LiveFollowRuntimeMode.LIVE) return null
    if (snapshot.role != LiveFollowSessionRole.WATCHER) return null
    if (snapshot.lifecycle != LiveFollowSessionLifecycle.ACTIVE) return null
    return deriveLiveFollowWatchLookup(
        explicitLookup = snapshot.watchLookup,
        shareCode = snapshot.shareCode,
        sessionId = snapshot.sessionId
    )
}

private fun deriveLocalFixMonoMs(
    nowMonoMs: Long,
    nowWallMs: Long,
    fixWallMs: Long
): Long {
    val ageMs = (nowWallMs - fixWallMs).coerceAtLeast(0L)
    return (nowMonoMs - ageMs).coerceAtLeast(0L)
}
