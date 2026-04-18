package com.trust3.xcpro.livefollow.account

import com.trust3.xcpro.livefollow.di.XcAccountScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class XcAccountRepository @Inject constructor(
    private val sessionStore: XcAccountSessionStore,
    private val authProvider: XcAccountAuthProvider,
    private val googleAuthGateway: XcGoogleAuthGateway,
    private val remoteDataSource: CurrentApiXcAccountDataSource,
    @XcAccountScope private val scope: CoroutineScope
) {
    private val signInCapabilities = buildSignInCapabilities()
    private val mutableState = MutableStateFlow(
        XcAccountSnapshot(
            isLoading = true,
            signInCapabilities = signInCapabilities
        )
    )
    private val operationMutex = Mutex()

    // Repository owns the private-follow account SSOT across screens and restores persisted auth state.
    val state: StateFlow<XcAccountSnapshot> = mutableState.asStateFlow()

    init {
        scope.launch {
            restorePersistedSession()
        }
    }

    suspend fun refresh(): XcAccountActionResult = operationMutex.withLock {
        val session = mutableState.value.session ?: return signedOutFailure("Sign in required.")
        refreshAccountFromSession(session)
    }

    suspend fun signIn(
        method: XcAccountSignInMethod
    ): XcAccountActionResult = operationMutex.withLock {
        when (val result = authProvider.signIn(method)) {
            is XcAccountAuthResult.Success -> {
                sessionStore.saveSession(result.session)
                refreshAccountFromSession(result.session)
            }

            is XcAccountAuthResult.Unavailable -> XcAccountActionResult.Failure(result.message)
            is XcAccountAuthResult.Failure -> XcAccountActionResult.Failure(result.message)
        }
    }

    suspend fun signInWithGoogleIdToken(
        googleIdToken: String
    ): XcAccountActionResult = operationMutex.withLock {
        when (val result = googleAuthGateway.signInWithGoogleIdToken(googleIdToken)) {
            is XcAccountAuthResult.Success -> {
                sessionStore.saveSession(result.session)
                refreshAccountFromSession(result.session)
            }

            is XcAccountAuthResult.Unavailable -> XcAccountActionResult.Failure(result.message)
            is XcAccountAuthResult.Failure -> XcAccountActionResult.Failure(result.message)
        }
    }

    suspend fun signOut() = operationMutex.withLock {
        if (mutableState.value.session?.authMethod == XcAccountAuthMethod.GOOGLE) {
            googleAuthGateway.signOut()
        }
        sessionStore.clearSession()
        mutableState.value = signedOutSnapshot()
    }

    suspend fun saveProfile(
        rawHandle: String,
        rawDisplayName: String,
        rawCompNumber: String
    ): XcAccountActionResult = operationMutex.withLock {
        val session = mutableState.value.session ?: return signedOutFailure("Sign in required.")
        val resolvedSession = resolveBackendSession(session)
            ?: return authResolutionFailure()
        val normalizedHandle = normalizeXcHandleCandidate(rawHandle)
            ?: return XcAccountActionResult.Failure(
                message = XCPRO_HANDLE_RULE_MESSAGE,
                code = "invalid_handle"
            )
        val normalizedDisplayName = normalizeXcDisplayNameCandidate(rawDisplayName)
            ?: return XcAccountActionResult.Failure(
                message = "Display name is required.",
                code = "profile_incomplete"
            )
        setLoadingFor(resolvedSession)
        when (
            val result = remoteDataSource.patchProfile(
                accessToken = resolvedSession.accessToken,
                request = XcProfileUpdateRequest(
                    handle = normalizedHandle,
                    displayName = normalizedDisplayName,
                    compNumber = normalizeXcCompNumberCandidate(rawCompNumber)
                )
            )
        ) {
            is XcAccountRemoteResult.Success -> {
                val current = mutableState.value
                mutableState.value = current.copy(
                    isLoading = false,
                    profile = result.value,
                    errorMessage = null
                )
                XcAccountActionResult.Success
            }

            is XcAccountRemoteResult.Failure -> handleRemoteFailure(resolvedSession, result.error)
        }
    }

    suspend fun savePrivacy(
        request: XcPrivacyUpdateRequest
    ): XcAccountActionResult = operationMutex.withLock {
        val session = mutableState.value.session ?: return signedOutFailure("Sign in required.")
        val resolvedSession = resolveBackendSession(session)
            ?: return authResolutionFailure()
        setLoadingFor(resolvedSession)
        when (
            val result = remoteDataSource.patchPrivacy(
                accessToken = resolvedSession.accessToken,
                request = request
            )
        ) {
            is XcAccountRemoteResult.Success -> {
                val current = mutableState.value
                mutableState.value = current.copy(
                    isLoading = false,
                    privacy = result.value,
                    errorMessage = null
                )
                XcAccountActionResult.Success
            }

            is XcAccountRemoteResult.Failure -> handleRemoteFailure(resolvedSession, result.error)
        }
    }

    suspend fun searchUsers(
        query: String
    ): XcAccountValueResult<List<XcSearchPilot>> = operationMutex.withLock {
        val session = mutableState.value.session
            ?: return@withLock signedOutValueFailure("Sign in required.")
        val resolvedSession = resolveBackendSession(session)
            ?: return@withLock authResolutionValueFailure()
        return@withLock when (val result = remoteDataSource.searchUsers(resolvedSession.accessToken, query)) {
            is XcAccountRemoteResult.Success -> XcAccountValueResult.Success(result.value)
            is XcAccountRemoteResult.Failure -> handleRemoteFailureForValue(resolvedSession, result.error)
        }
    }

    suspend fun sendFollowRequest(
        targetUserId: String
    ): XcAccountActionResult = operationMutex.withLock {
        val session = mutableState.value.session ?: return signedOutFailure("Sign in required.")
        val resolvedSession = resolveBackendSession(session)
            ?: return authResolutionFailure()
        setLoadingFor(resolvedSession)
        return@withLock when (
            val result = remoteDataSource.createFollowRequest(
                accessToken = resolvedSession.accessToken,
                targetUserId = targetUserId
            )
        ) {
            is XcAccountRemoteResult.Success -> refreshFollowRequestLists(resolvedSession)
            is XcAccountRemoteResult.Failure -> handleRemoteFailure(resolvedSession, result.error)
        }
    }

    suspend fun acceptFollowRequest(
        requestId: String
    ): XcAccountActionResult = operationMutex.withLock {
        val session = mutableState.value.session ?: return signedOutFailure("Sign in required.")
        val resolvedSession = resolveBackendSession(session)
            ?: return authResolutionFailure()
        setLoadingFor(resolvedSession)
        return@withLock when (
            val result = remoteDataSource.acceptFollowRequest(
                accessToken = resolvedSession.accessToken,
                requestId = requestId
            )
        ) {
            is XcAccountRemoteResult.Success -> refreshFollowRequestLists(resolvedSession)
            is XcAccountRemoteResult.Failure -> handleRemoteFailure(resolvedSession, result.error)
        }
    }

    suspend fun declineFollowRequest(
        requestId: String
    ): XcAccountActionResult = operationMutex.withLock {
        val session = mutableState.value.session ?: return signedOutFailure("Sign in required.")
        val resolvedSession = resolveBackendSession(session)
            ?: return authResolutionFailure()
        setLoadingFor(resolvedSession)
        return@withLock when (
            val result = remoteDataSource.declineFollowRequest(
                accessToken = resolvedSession.accessToken,
                requestId = requestId
            )
        ) {
            is XcAccountRemoteResult.Success -> refreshFollowRequestLists(resolvedSession)
            is XcAccountRemoteResult.Failure -> handleRemoteFailure(resolvedSession, result.error)
        }
    }

    private suspend fun restorePersistedSession() {
        val session = sessionStore.loadSession()
        if (session == null) {
            mutableState.value = signedOutSnapshot()
            return
        }
        refreshAccountFromSession(session)
    }

    private suspend fun refreshAccountFromSession(
        session: XcAccountSession
    ): XcAccountActionResult {
        val resolvedSession = resolveBackendSession(session)
            ?: return authResolutionFailure()
        setLoadingFor(resolvedSession)
        return when (val result = remoteDataSource.fetchMe(resolvedSession.accessToken)) {
            is XcAccountRemoteResult.Success -> {
                when (val requests = loadFollowRequestLists(resolvedSession.accessToken)) {
                    is XcAccountRemoteResult.Success -> {
                        updateSignedInSnapshot(
                            session = resolvedSession,
                            profile = result.value.profile,
                            privacy = result.value.privacy,
                            incomingFollowRequests = requests.value.first,
                            outgoingFollowRequests = requests.value.second
                        )
                        XcAccountActionResult.Success
                    }

                    is XcAccountRemoteResult.Failure -> handleRelationshipLoadFailure(
                        session = resolvedSession,
                        profile = result.value.profile,
                        privacy = result.value.privacy,
                        error = requests.error
                    )
                }
            }

            is XcAccountRemoteResult.Failure -> handleRemoteFailure(resolvedSession, result.error)
        }
    }

    private suspend fun handleRemoteFailure(
        session: XcAccountSession,
        error: XcAccountApiError
    ): XcAccountActionResult.Failure {
        if (error.isUnauthenticated) {
            sessionStore.clearSession()
            mutableState.value = signedOutSnapshot(
                message = "Your XCPro session is no longer valid. Sign in again."
            )
            return XcAccountActionResult.Failure(
                message = error.message,
                code = error.code
            )
        }
        val current = mutableState.value
        val keepCurrentData = current.session?.accessToken == session.accessToken
        mutableState.value = XcAccountSnapshot(
            isLoading = false,
            session = session,
            profile = if (keepCurrentData) current.profile else null,
            privacy = if (keepCurrentData) current.privacy else null,
            incomingFollowRequests = if (keepCurrentData) current.incomingFollowRequests else emptyList(),
            outgoingFollowRequests = if (keepCurrentData) current.outgoingFollowRequests else emptyList(),
            signInCapabilities = signInCapabilities,
            errorMessage = error.message
        )
        return XcAccountActionResult.Failure(
            message = error.message,
            code = error.code
        )
    }

    private fun setLoadingFor(session: XcAccountSession) {
        val current = mutableState.value
        mutableState.value = XcAccountSnapshot(
            isLoading = true,
            session = session,
            profile = if (current.session?.accessToken == session.accessToken) current.profile else null,
            privacy = if (current.session?.accessToken == session.accessToken) current.privacy else null,
            incomingFollowRequests = if (
                current.session?.accessToken == session.accessToken
            ) {
                current.incomingFollowRequests
            } else {
                emptyList()
            },
            outgoingFollowRequests = if (
                current.session?.accessToken == session.accessToken
            ) {
                current.outgoingFollowRequests
            } else {
                emptyList()
            },
            signInCapabilities = signInCapabilities
        )
    }

    private fun signedOutSnapshot(message: String? = null): XcAccountSnapshot {
        return XcAccountSnapshot(
            isLoading = false,
            signInCapabilities = signInCapabilities,
            errorMessage = message
        )
    }

    private fun signedOutFailure(message: String): XcAccountActionResult.Failure {
        return XcAccountActionResult.Failure(message = message)
    }

    private fun signedOutValueFailure(message: String): XcAccountValueResult.Failure {
        return XcAccountValueResult.Failure(message = message)
    }

    private fun buildSignInCapabilities(): List<XcAccountSignInCapability> {
        return buildList {
            add(googleAuthGateway.signInCapability())
            add(
                XcAccountSignInCapability(
                    method = XcAccountSignInMethod.EMAIL_LINK,
                    title = "Continue with email link",
                    description = "Passwordless sign-in path for the future private-follow lane.",
                    isAvailable = false,
                    availabilityNote = "Email-link sign-in is not implemented in this build yet."
                )
            )
            addAll(authProvider.signInCapabilities())
        }
    }

    private suspend fun resolveBackendSession(
        session: XcAccountSession
    ): XcAccountSession? {
        val authResult = when (session.authMethod) {
            XcAccountAuthMethod.GOOGLE -> googleAuthGateway.restoreSession(session)
            else -> XcAccountAuthResult.Success(session)
        }
        return when (authResult) {
            is XcAccountAuthResult.Success -> {
                sessionStore.saveSession(authResult.session)
                authResult.session
            }

            is XcAccountAuthResult.Unavailable -> {
                sessionStore.clearSession()
                mutableState.value = signedOutSnapshot(authResult.message)
                null
            }

            is XcAccountAuthResult.Failure -> {
                sessionStore.clearSession()
                mutableState.value = signedOutSnapshot(authResult.message)
                null
            }
        }
    }

    private fun authResolutionFailure(): XcAccountActionResult.Failure {
        return XcAccountActionResult.Failure("Your XCPro session is no longer valid. Sign in again.")
    }

    private fun authResolutionValueFailure(): XcAccountValueResult.Failure {
        return XcAccountValueResult.Failure("Your XCPro session is no longer valid. Sign in again.")
    }

    private suspend fun loadFollowRequestLists(
        accessToken: String
    ): XcAccountRemoteResult<Pair<List<XcFollowRequestItem>, List<XcFollowRequestItem>>> {
        return when (val incoming = remoteDataSource.fetchIncomingFollowRequests(accessToken)) {
            is XcAccountRemoteResult.Success -> {
                when (val outgoing = remoteDataSource.fetchOutgoingFollowRequests(accessToken)) {
                    is XcAccountRemoteResult.Success -> {
                        XcAccountRemoteResult.Success(incoming.value to outgoing.value)
                    }

                    is XcAccountRemoteResult.Failure -> outgoing
                }
            }

            is XcAccountRemoteResult.Failure -> incoming
        }
    }

    private suspend fun refreshFollowRequestLists(
        session: XcAccountSession
    ): XcAccountActionResult {
        val current = mutableState.value
        val profile = current.profile ?: return refreshAccountFromSession(session)
        val privacy = current.privacy ?: return refreshAccountFromSession(session)
        return when (val requests = loadFollowRequestLists(session.accessToken)) {
            is XcAccountRemoteResult.Success -> {
                updateSignedInSnapshot(
                    session = session,
                    profile = profile,
                    privacy = privacy,
                    incomingFollowRequests = requests.value.first,
                    outgoingFollowRequests = requests.value.second
                )
                XcAccountActionResult.Success
            }

            is XcAccountRemoteResult.Failure -> handleRelationshipLoadFailure(
                session = session,
                profile = profile,
                privacy = privacy,
                error = requests.error
            )
        }
    }

    private fun updateSignedInSnapshot(
        session: XcAccountSession,
        profile: XcPilotProfile,
        privacy: XcPrivacySettings,
        incomingFollowRequests: List<XcFollowRequestItem>,
        outgoingFollowRequests: List<XcFollowRequestItem>,
        errorMessage: String? = null
    ) {
        mutableState.value = XcAccountSnapshot(
            isLoading = false,
            session = session,
            profile = profile,
            privacy = privacy,
            incomingFollowRequests = incomingFollowRequests,
            outgoingFollowRequests = outgoingFollowRequests,
            signInCapabilities = signInCapabilities,
            errorMessage = errorMessage
        )
    }

    private suspend fun handleRelationshipLoadFailure(
        session: XcAccountSession,
        profile: XcPilotProfile,
        privacy: XcPrivacySettings,
        error: XcAccountApiError
    ): XcAccountActionResult.Failure {
        if (error.isUnauthenticated) {
            return handleRemoteFailure(session, error)
        }
        val current = mutableState.value
        val keepCurrentLists = current.session?.accessToken == session.accessToken
        updateSignedInSnapshot(
            session = session,
            profile = profile,
            privacy = privacy,
            incomingFollowRequests = if (keepCurrentLists) current.incomingFollowRequests else emptyList(),
            outgoingFollowRequests = if (keepCurrentLists) current.outgoingFollowRequests else emptyList(),
            errorMessage = error.message
        )
        return XcAccountActionResult.Failure(
            message = error.message,
            code = error.code
        )
    }

    private suspend fun handleRemoteFailureForValue(
        session: XcAccountSession,
        error: XcAccountApiError
    ): XcAccountValueResult.Failure {
        val failure = handleRemoteFailure(session, error)
        return XcAccountValueResult.Failure(
            message = failure.message,
            code = failure.code
        )
    }
}
