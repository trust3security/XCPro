package com.trust3.xcpro.livefollow.account

import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class XcAccountUseCase @Inject constructor(
    private val repository: XcAccountRepository
) {
    val state: StateFlow<XcAccountSnapshot> = repository.state

    suspend fun refresh(): XcAccountActionResult = repository.refresh()

    suspend fun signIn(
        method: XcAccountSignInMethod
    ): XcAccountActionResult = repository.signIn(method)

    suspend fun signInWithGoogleIdToken(
        googleIdToken: String
    ): XcAccountActionResult = repository.signInWithGoogleIdToken(googleIdToken)

    suspend fun signOut() = repository.signOut()

    suspend fun saveProfile(
        handle: String,
        displayName: String,
        compNumber: String
    ): XcAccountActionResult = repository.saveProfile(
        rawHandle = handle,
        rawDisplayName = displayName,
        rawCompNumber = compNumber
    )

    suspend fun savePrivacy(
        request: XcPrivacyUpdateRequest
    ): XcAccountActionResult = repository.savePrivacy(request)

    suspend fun searchUsers(
        query: String
    ): XcAccountValueResult<List<XcSearchPilot>> = repository.searchUsers(query)

    suspend fun sendFollowRequest(
        targetUserId: String
    ): XcAccountActionResult = repository.sendFollowRequest(targetUserId)

    suspend fun acceptFollowRequest(
        requestId: String
    ): XcAccountActionResult = repository.acceptFollowRequest(requestId)

    suspend fun declineFollowRequest(
        requestId: String
    ): XcAccountActionResult = repository.declineFollowRequest(requestId)
}
