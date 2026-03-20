package com.example.xcpro.livefollow.data.session

import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.livefollow.data.transport.parseCurrentApiErrorMessage
import com.example.xcpro.livefollow.data.transport.parseCurrentApiLiveReadResponse
import com.example.xcpro.livefollow.data.transport.parseCurrentApiSessionEndResponse
import com.example.xcpro.livefollow.data.transport.parseCurrentApiSessionStartResponse
import com.example.xcpro.livefollow.di.LiveFollowHttpClient
import com.example.xcpro.livefollow.model.liveFollowAvailableTransport
import com.example.xcpro.livefollow.model.liveFollowDegradedTransport
import com.example.xcpro.livefollow.model.liveFollowUnavailableTransport
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class CurrentApiLiveFollowSessionGateway @Inject constructor(
    @LiveFollowHttpClient private val httpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LiveFollowSessionGateway {
    private val lock = Any()
    private val mutableSessionState = MutableStateFlow(liveFollowGatewayIdleSnapshot())

    @Volatile
    private var storedPilotTransport = StoredPilotTransport()

    override val sessionState: StateFlow<LiveFollowSessionGatewaySnapshot> =
        mutableSessionState.asStateFlow()

    override suspend fun startPilotSession(
        request: StartPilotLiveFollowSession
    ): LiveFollowSessionGatewayResult = withContext(ioDispatcher) {
        val httpRequest = Request.Builder()
            .url(baseUrlBuilder().addPathSegments("api/v1/session/start").build())
            .post(EMPTY_REQUEST_BODY)
            .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
            .build()

        executeSessionCommand(httpRequest) { body ->
            val response = parseCurrentApiSessionStartResponse(body)
            synchronized(lock) {
                storedPilotTransport = StoredPilotTransport(
                    sessionId = response.sessionId,
                    shareCode = response.shareCode,
                    writeToken = response.writeToken,
                    lastUploadedFixWallMs = null
                )
            }
            val snapshot = LiveFollowSessionGatewaySnapshot(
                sessionId = response.sessionId,
                role = LiveFollowSessionRole.PILOT,
                lifecycle = LiveFollowSessionLifecycle.ACTIVE,
                watchIdentity = null,
                directWatchAuthorized = false,
                transportAvailability = liveFollowAvailableTransport(),
                lastError = null
            )
            mutableSessionState.value = snapshot
            LiveFollowSessionGatewayResult.Success(snapshot)
        }
    }

    override suspend fun stopCurrentSession(sessionId: String): LiveFollowSessionGatewayResult =
        withContext(ioDispatcher) {
            val stored = synchronized(lock) { storedPilotTransport }
            val writeToken = stored.writeToken
                ?: return@withContext LiveFollowSessionGatewayResult.Failure(
                    "LiveFollow session write token is unavailable."
                )

            val requestBody = """{"session_id":"${escapeJson(sessionId)}"}"""
                .toRequestBody(JSON_MEDIA_TYPE)
            val httpRequest = Request.Builder()
                .url(baseUrlBuilder().addPathSegments("api/v1/session/end").build())
                .post(requestBody)
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_SESSION_TOKEN, writeToken)
                .build()

            executeSessionCommand(httpRequest) { body ->
                parseCurrentApiSessionEndResponse(body)
                synchronized(lock) {
                    storedPilotTransport = StoredPilotTransport()
                }
                val snapshot = liveFollowGatewayIdleSnapshot()
                mutableSessionState.value = snapshot
                LiveFollowSessionGatewayResult.Success(snapshot)
            }
        }

    override suspend fun joinWatchSession(sessionId: String): LiveFollowSessionGatewayResult =
        withContext(ioDispatcher) {
            val normalizedSessionId = sessionId.trim().takeIf { it.isNotEmpty() }
                ?: return@withContext LiveFollowSessionGatewayResult.Failure(
                    "Invalid LiveFollow session id."
                )
            val httpRequest = Request.Builder()
                .url(
                    baseUrlBuilder()
                        .addPathSegments("api/v1/live")
                        .addPathSegment(normalizedSessionId)
                        .build()
                )
                .get()
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .build()

            executeSessionCommand(httpRequest) { body ->
                val response = parseCurrentApiLiveReadResponse(body)
                synchronized(lock) {
                    storedPilotTransport = StoredPilotTransport()
                }
                // AI-NOTE: The deployed API does not expose a watch-membership or
                // explicit direct-watch auth concept. Slice 1 treats a successful
                // public GET probe as enough to enable polling while keeping
                // watchIdentity null and task metadata unavailable.
                val snapshot = LiveFollowSessionGatewaySnapshot(
                    sessionId = response.sessionId,
                    role = LiveFollowSessionRole.WATCHER,
                    lifecycle = LiveFollowSessionLifecycle.ACTIVE,
                    watchIdentity = null,
                    directWatchAuthorized = true,
                    transportAvailability = liveFollowAvailableTransport(),
                    lastError = null
                )
                mutableSessionState.value = snapshot
                LiveFollowSessionGatewayResult.Success(snapshot)
            }
        }

    override suspend fun leaveSession(sessionId: String): LiveFollowSessionGatewayResult {
        synchronized(lock) {
            storedPilotTransport = StoredPilotTransport()
        }
        val snapshot = liveFollowGatewayIdleSnapshot()
        mutableSessionState.value = snapshot
        return LiveFollowSessionGatewayResult.Success(snapshot)
    }

    override suspend fun uploadPilotPosition(
        snapshot: com.example.xcpro.livefollow.model.LiveOwnshipSnapshot
    ): LiveFollowPilotPositionUploadResult = withContext(ioDispatcher) {
        val currentSnapshot = mutableSessionState.value
        if (currentSnapshot.role != LiveFollowSessionRole.PILOT ||
            currentSnapshot.lifecycle != LiveFollowSessionLifecycle.ACTIVE
        ) {
            return@withContext LiveFollowPilotPositionUploadResult.Skipped(
                LiveFollowPilotPositionSkipReason.NOT_PILOT_SESSION
            )
        }

        val stored = synchronized(lock) { storedPilotTransport }
        val sessionId = stored.sessionId
        val writeToken = stored.writeToken
        if (sessionId.isNullOrBlank() || writeToken.isNullOrBlank()) {
            return@withContext LiveFollowPilotPositionUploadResult.Skipped(
                LiveFollowPilotPositionSkipReason.MISSING_CREDENTIALS
            )
        }

        val requestPayload = LiveFollowCurrentApiPositionMapper.map(
            sessionId = sessionId,
            snapshot = snapshot
        ) ?: return@withContext LiveFollowPilotPositionUploadResult.Skipped(
            LiveFollowPilotPositionSkipReason.MISSING_REQUIRED_FIELDS
        )

        val lastUploadedFixWallMs = stored.lastUploadedFixWallMs
        if (lastUploadedFixWallMs != null && requestPayload.fixWallMs <= lastUploadedFixWallMs) {
            return@withContext LiveFollowPilotPositionUploadResult.Skipped(
                LiveFollowPilotPositionSkipReason.NON_INCREASING_TIMESTAMP
            )
        }

        val httpRequest = Request.Builder()
            .url(baseUrlBuilder().addPathSegments("api/v1/position").build())
            .post(requestPayload.toJsonString().toRequestBody(JSON_MEDIA_TYPE))
            .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
            .header(HEADER_SESSION_TOKEN, writeToken)
            .build()

        try {
            httpClient.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = parseCurrentApiErrorMessage(
                        responseBody = responseBody,
                        httpCode = response.code
                    )
                    mutableSessionState.value = mutableSessionState.value.copy(
                        transportAvailability = availabilityForHttpFailure(
                            httpCode = response.code,
                            message = message
                        ),
                        lastError = message
                    )
                    return@withContext LiveFollowPilotPositionUploadResult.Failure(message)
                }

                synchronized(lock) {
                    storedPilotTransport = storedPilotTransport.copy(
                        lastUploadedFixWallMs = requestPayload.fixWallMs
                    )
                }
                mutableSessionState.value = mutableSessionState.value.copy(
                    transportAvailability = liveFollowAvailableTransport(),
                    lastError = null
                )
                LiveFollowPilotPositionUploadResult.Uploaded
            }
        } catch (e: IOException) {
            val message = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: e::class.java.simpleName
            mutableSessionState.value = mutableSessionState.value.copy(
                transportAvailability = liveFollowUnavailableTransport(message),
                lastError = message
            )
            LiveFollowPilotPositionUploadResult.Failure(message)
        }
    }

    internal fun storedPilotTransportForTests(): StoredPilotTransport = synchronized(lock) {
        storedPilotTransport
    }

    private inline fun executeSessionCommand(
        request: Request,
        onSuccess: (String) -> LiveFollowSessionGatewayResult
    ): LiveFollowSessionGatewayResult {
        return try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = parseCurrentApiErrorMessage(
                        responseBody = responseBody,
                        httpCode = response.code
                    )
                    mutableSessionState.value = mutableSessionState.value.copy(
                        transportAvailability = availabilityForHttpFailure(
                            httpCode = response.code,
                            message = message
                        ),
                        lastError = message
                    )
                    return LiveFollowSessionGatewayResult.Failure(message)
                }

                runCatching {
                    onSuccess(responseBody)
                }.getOrElse { cause ->
                    val message = cause.message ?: "Malformed LiveFollow response"
                    mutableSessionState.value = mutableSessionState.value.copy(
                        transportAvailability = liveFollowDegradedTransport(message),
                        lastError = message
                    )
                    LiveFollowSessionGatewayResult.Failure(message)
                }
            }
        } catch (e: IOException) {
            val message = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: e::class.java.simpleName
            mutableSessionState.value = mutableSessionState.value.copy(
                transportAvailability = liveFollowUnavailableTransport(message),
                lastError = message
            )
            LiveFollowSessionGatewayResult.Failure(message)
        }
    }

    private fun availabilityForHttpFailure(
        httpCode: Int,
        message: String
    ) = when {
        httpCode >= 500 -> liveFollowDegradedTransport(message)
        else -> liveFollowAvailableTransport()
    }

    private fun baseUrlBuilder() = LIVEFOLLOW_BASE_URL.toHttpUrl().newBuilder()

    internal data class StoredPilotTransport(
        val sessionId: String? = null,
        val shareCode: String? = null,
        val writeToken: String? = null,
        val lastUploadedFixWallMs: Long? = null
    )

    private companion object {
        private const val LIVEFOLLOW_BASE_URL = "https://api.xcpro.com.au/"
        private const val CONTENT_TYPE_JSON = "application/json"
        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_SESSION_TOKEN = "X-Session-Token"
        private val JSON_MEDIA_TYPE = CONTENT_TYPE_JSON.toMediaType()
        private val EMPTY_REQUEST_BODY = ByteArray(0).toRequestBody()
    }
}

private fun escapeJson(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")
