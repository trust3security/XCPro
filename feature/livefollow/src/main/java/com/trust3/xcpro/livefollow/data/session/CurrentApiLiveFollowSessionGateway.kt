package com.trust3.xcpro.livefollow.data.session

import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.livefollow.account.XcAccountRepository
import com.trust3.xcpro.livefollow.data.transport.LiveFollowTransportFailureSurface
import com.trust3.xcpro.livefollow.data.transport.logAndNormalizeLiveFollowTransportFailure
import com.trust3.xcpro.livefollow.data.transport.parseCurrentApiErrorMessage
import com.trust3.xcpro.livefollow.data.transport.parseCurrentApiLiveReadResponse
import com.trust3.xcpro.livefollow.data.transport.mapCurrentApiTaskClearPayload
import com.trust3.xcpro.livefollow.data.transport.parseCurrentApiSessionEndResponse
import com.trust3.xcpro.livefollow.data.transport.parseCurrentApiSessionVisibilityResponse
import com.trust3.xcpro.livefollow.data.transport.parseCurrentApiSessionStartResponse
import com.trust3.xcpro.livefollow.data.transport.mapCurrentApiTaskUpsertPayload
import com.trust3.xcpro.livefollow.di.LiveFollowHttpClient
import com.trust3.xcpro.livefollow.model.liveFollowAvailableTransport
import com.trust3.xcpro.livefollow.model.liveFollowDegradedTransport
import com.trust3.xcpro.livefollow.model.LiveFollowTaskSnapshot
import com.trust3.xcpro.livefollow.model.liveFollowUnavailableTransport
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class CurrentApiLiveFollowSessionGateway @Inject constructor(
    @LiveFollowHttpClient private val httpClient: OkHttpClient,
    private val xcAccountRepository: XcAccountRepository,
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
        val accessToken = currentAccessToken()
        val visibility = request.visibility ?: LiveFollowSessionVisibility.PUBLIC
        if (accessToken == null && visibility != LiveFollowSessionVisibility.PUBLIC) {
            return@withContext LiveFollowSessionGatewayResult.Failure(
                "Sign in is required for follower-only or off live visibility."
            )
        }
        val httpRequest = if (accessToken != null) {
            buildAuthenticatedSessionStartRequest(
                accessToken = accessToken,
                visibility = visibility
            )
        } else {
            buildPublicSessionStartRequest()
        }

        executeSessionCommand(httpRequest) { body ->
            val response = parseCurrentApiSessionStartResponse(body)
            synchronized(lock) {
                storedPilotTransport = StoredPilotTransport(
                    sessionId = response.sessionId,
                    shareCode = response.shareCode,
                    writeToken = response.writeToken,
                    lastUploadedFixWallMs = null,
                    lastUploadedTaskPayloadJson = null
                )
            }
            val snapshot = LiveFollowSessionGatewaySnapshot(
                sessionId = response.sessionId,
                ownerUserId = response.ownerUserId,
                role = LiveFollowSessionRole.PILOT,
                lifecycle = LiveFollowSessionLifecycle.ACTIVE,
                visibility = response.visibility ?: visibility,
                watchIdentity = null,
                directWatchAuthorized = false,
                transportAvailability = liveFollowAvailableTransport(),
                lastError = null,
                shareCode = response.shareCode
            )
            mutableSessionState.value = snapshot
            LiveFollowSessionGatewayResult.Success(snapshot)
        }
    }

    override suspend fun updatePilotVisibility(
        sessionId: String,
        visibility: LiveFollowSessionVisibility
    ): LiveFollowSessionGatewayResult = withContext(ioDispatcher) {
        val accessToken = currentAccessToken()
            ?: return@withContext LiveFollowSessionGatewayResult.Failure(
                "Sign in is required to update authenticated live visibility."
            )
        val currentSnapshot = mutableSessionState.value
        if (currentSnapshot.ownerUserId.isNullOrBlank()) {
            return@withContext LiveFollowSessionGatewayResult.Failure(
                "Current pilot session is not owned by a signed-in XCPro account."
            )
        }
        val httpRequest = buildVisibilityPatchRequest(
            sessionId = sessionId,
            accessToken = accessToken,
            visibility = visibility
        )

        executeSessionCommand(httpRequest) { body ->
            val response = parseCurrentApiSessionVisibilityResponse(body)
            val snapshot = mutableSessionState.value.copy(
                sessionId = response.sessionId,
                ownerUserId = response.ownerUserId ?: currentSnapshot.ownerUserId,
                role = LiveFollowSessionRole.PILOT,
                lifecycle = LiveFollowSessionLifecycle.ACTIVE,
                visibility = response.visibility ?: visibility,
                directWatchAuthorized = false,
                transportAvailability = liveFollowAvailableTransport(),
                lastError = null,
                shareCode = response.shareCode
            )
            mutableSessionState.value = snapshot
            synchronized(lock) {
                storedPilotTransport = storedPilotTransport.copy(
                    sessionId = response.sessionId,
                    shareCode = response.shareCode
                )
            }
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

            val httpRequest = buildSessionEndRequest(
                sessionId = sessionId,
                writeToken = writeToken
            )

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
            val httpRequest = buildPublicLiveSessionRequest(normalizedSessionId)

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
                    ownerUserId = response.ownerUserId,
                    role = LiveFollowSessionRole.WATCHER,
                    lifecycle = LiveFollowSessionLifecycle.ACTIVE,
                    visibility = response.visibility ?: LiveFollowSessionVisibility.PUBLIC,
                    watchIdentity = null,
                    directWatchAuthorized = true,
                    transportAvailability = liveFollowAvailableTransport(),
                    lastError = null,
                    shareCode = response.shareCode,
                    watchLookup = liveFollowSessionIdLookup(normalizedSessionId)
                )
                mutableSessionState.value = snapshot
                LiveFollowSessionGatewayResult.Success(snapshot)
            }
        }

    override suspend fun joinAuthenticatedWatchSession(
        sessionId: String
    ): LiveFollowSessionGatewayResult = withContext(ioDispatcher) {
        val normalizedSessionId = sessionId.trim().takeIf { it.isNotEmpty() }
            ?: return@withContext LiveFollowSessionGatewayResult.Failure(
                "Invalid LiveFollow session id."
            )
        val accessToken = currentAccessToken()
            ?: return@withContext LiveFollowSessionGatewayResult.Failure(
                "Sign in is required for authorized LiveFollow watch."
            )
        val httpRequest = buildAuthenticatedLiveSessionRequest(
            sessionId = normalizedSessionId,
            accessToken = accessToken
        )

        executeSessionCommand(httpRequest) { body ->
            val response = parseCurrentApiLiveReadResponse(body)
            synchronized(lock) {
                storedPilotTransport = StoredPilotTransport()
            }
            val snapshot = LiveFollowSessionGatewaySnapshot(
                sessionId = response.sessionId,
                ownerUserId = response.ownerUserId,
                role = LiveFollowSessionRole.WATCHER,
                lifecycle = LiveFollowSessionLifecycle.ACTIVE,
                visibility = response.visibility,
                watchIdentity = null,
                directWatchAuthorized = true,
                transportAvailability = liveFollowAvailableTransport(),
                lastError = null,
                shareCode = response.shareCode,
                watchLookup = liveFollowAuthenticatedSessionIdLookup(normalizedSessionId)
            )
            mutableSessionState.value = snapshot
            LiveFollowSessionGatewayResult.Success(snapshot)
        }
    }

    override suspend fun joinWatchSessionByShareCode(
        shareCode: String
    ): LiveFollowSessionGatewayResult = withContext(ioDispatcher) {
        val normalizedShareCode = shareCode.trim()
            .uppercase(Locale.US)
            .takeIf { it.isNotEmpty() }
            ?: return@withContext LiveFollowSessionGatewayResult.Failure(
                "Invalid LiveFollow share code."
            )
        val httpRequest = buildLiveShareCodeRequest(normalizedShareCode)

        executeSessionCommand(httpRequest) { body ->
            val response = parseCurrentApiLiveReadResponse(body)
            synchronized(lock) {
                storedPilotTransport = StoredPilotTransport()
            }
            // AI-NOTE: Public share-code watch keeps the share code as the
            // polling key so later polls stay on the deployed public endpoint.
            val snapshot = LiveFollowSessionGatewaySnapshot(
                sessionId = response.sessionId,
                ownerUserId = response.ownerUserId,
                role = LiveFollowSessionRole.WATCHER,
                lifecycle = LiveFollowSessionLifecycle.ACTIVE,
                visibility = response.visibility ?: LiveFollowSessionVisibility.PUBLIC,
                watchIdentity = null,
                directWatchAuthorized = true,
                transportAvailability = liveFollowAvailableTransport(),
                lastError = null,
                shareCode = response.shareCode ?: normalizedShareCode,
                watchLookup = liveFollowShareCodeLookup(normalizedShareCode)
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
        snapshot: com.trust3.xcpro.livefollow.model.LiveOwnshipSnapshot
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

        val httpRequest = buildPilotPositionUploadRequest(
            writeToken = writeToken,
            requestJson = requestPayload.toJsonString()
        )

        try {
            httpClient.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = parseCurrentApiErrorMessage(
                        responseBody = responseBody,
                        httpCode = response.code
                    )
                    mutableSessionState.value = mutableSessionState.value.copy(
                        transportAvailability = availabilityForLiveFollowHttpFailure(
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
            val message = logAndNormalizeLiveFollowTransportFailure(
                tag = TAG,
                operationLabel = "Pilot position upload",
                surface = LiveFollowTransportFailureSurface.LIVEFOLLOW,
                throwable = e
            ).userMessage
            mutableSessionState.value = mutableSessionState.value.copy(
                transportAvailability = liveFollowUnavailableTransport(message),
                lastError = message
            )
            LiveFollowPilotPositionUploadResult.Failure(message)
        }
    }

    override suspend fun uploadPilotTask(
        snapshot: LiveFollowTaskSnapshot?
    ): LiveFollowPilotTaskUploadResult = withContext(ioDispatcher) {
        val currentSnapshot = mutableSessionState.value
        if (currentSnapshot.role != LiveFollowSessionRole.PILOT ||
            currentSnapshot.lifecycle != LiveFollowSessionLifecycle.ACTIVE
        ) {
            return@withContext LiveFollowPilotTaskUploadResult.Skipped(
                LiveFollowPilotTaskSkipReason.NOT_PILOT_SESSION
            )
        }

        val stored = synchronized(lock) { storedPilotTransport }
        val sessionId = stored.sessionId
        val writeToken = stored.writeToken
        if (sessionId.isNullOrBlank() || writeToken.isNullOrBlank()) {
            return@withContext LiveFollowPilotTaskUploadResult.Skipped(
                LiveFollowPilotTaskSkipReason.MISSING_CREDENTIALS
            )
        }

        val requestJson = snapshot?.let {
            mapCurrentApiTaskUpsertPayload(
                sessionId = sessionId,
                snapshot = it
            )
        } ?: mapCurrentApiTaskClearPayload(sessionId)
        if (snapshot != null && requestJson.isBlank()) {
            return@withContext LiveFollowPilotTaskUploadResult.Skipped(
                LiveFollowPilotTaskSkipReason.MISSING_REQUIRED_FIELDS
            )
        }
        if (requestJson == stored.lastUploadedTaskPayloadJson) {
            return@withContext LiveFollowPilotTaskUploadResult.Skipped(
                LiveFollowPilotTaskSkipReason.UNCHANGED_TASK
            )
        }

        val httpRequest = buildPilotTaskUploadRequest(
            writeToken = writeToken,
            requestJson = requestJson
        )

        try {
            httpClient.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = parseCurrentApiErrorMessage(
                        responseBody = responseBody,
                        httpCode = response.code
                    )
                    mutableSessionState.value = mutableSessionState.value.copy(
                        transportAvailability = availabilityForLiveFollowHttpFailure(
                            httpCode = response.code,
                            message = message
                        ),
                        lastError = message
                    )
                    return@withContext LiveFollowPilotTaskUploadResult.Failure(message)
                }

                synchronized(lock) {
                    storedPilotTransport = storedPilotTransport.copy(
                        lastUploadedTaskPayloadJson = requestJson
                    )
                }
                mutableSessionState.value = mutableSessionState.value.copy(
                    transportAvailability = liveFollowAvailableTransport(),
                    lastError = null
                )
                LiveFollowPilotTaskUploadResult.Uploaded
            }
        } catch (e: IOException) {
            val message = logAndNormalizeLiveFollowTransportFailure(
                tag = TAG,
                operationLabel = "Pilot task upload",
                surface = LiveFollowTransportFailureSurface.LIVEFOLLOW,
                throwable = e
            ).userMessage
            mutableSessionState.value = mutableSessionState.value.copy(
                transportAvailability = liveFollowUnavailableTransport(message),
                lastError = message
            )
            LiveFollowPilotTaskUploadResult.Failure(message)
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
                        transportAvailability = availabilityForLiveFollowHttpFailure(
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
            val message = logAndNormalizeLiveFollowTransportFailure(
                tag = TAG,
                operationLabel = "Session command",
                surface = LiveFollowTransportFailureSurface.LIVEFOLLOW,
                throwable = e
            ).userMessage
            mutableSessionState.value = mutableSessionState.value.copy(
                transportAvailability = liveFollowUnavailableTransport(message),
                lastError = message
            )
            LiveFollowSessionGatewayResult.Failure(message)
        }
    }

    private fun currentAccessToken(): String? {
        return xcAccountRepository.state.value.session?.accessToken
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        private const val TAG = "LiveFollowSessionGateway"
    }
}
