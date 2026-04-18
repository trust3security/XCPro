package com.trust3.xcpro.livefollow.account

sealed interface XcAccountAuthResult {
    data class Success(
        val session: XcAccountSession
    ) : XcAccountAuthResult

    data class Unavailable(
        val message: String
    ) : XcAccountAuthResult

    data class Failure(
        val message: String
    ) : XcAccountAuthResult
}

interface XcAccountAuthProvider {
    fun signInCapabilities(): List<XcAccountSignInCapability>

    suspend fun signIn(
        method: XcAccountSignInMethod
    ): XcAccountAuthResult
}
