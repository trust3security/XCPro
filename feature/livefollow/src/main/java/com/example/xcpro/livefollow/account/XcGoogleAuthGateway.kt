package com.example.xcpro.livefollow.account

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.xcpro.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface XcGoogleAuthGateway {
    fun signInCapability(): XcAccountSignInCapability

    suspend fun signInWithGoogleIdToken(
        googleIdToken: String
    ): XcAccountAuthResult

    suspend fun restoreSession(
        session: XcAccountSession
    ): XcAccountAuthResult

    suspend fun signOut()
}

@Singleton
class ServerExchangeXcGoogleAuthGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteDataSource: CurrentApiXcAccountDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : XcGoogleAuthGateway {

    override fun signInCapability(): XcAccountSignInCapability {
        val config = resolveXcGoogleSignInConfig()
        return XcAccountSignInCapability(
            method = XcAccountSignInMethod.GOOGLE,
            title = "Continue with Google",
            description = "Preferred production sign-in path for the private-follow lane.",
            isAvailable = config.isAvailable,
            availabilityNote = config.availabilityNote
        )
    }

    override suspend fun signInWithGoogleIdToken(
        googleIdToken: String
    ): XcAccountAuthResult = withContext(ioDispatcher) {
        val config = resolveXcGoogleSignInConfig()
        if (!config.isAvailable) {
            return@withContext unavailable(config)
        }
        val trimmedToken = googleIdToken.trim()
        if (trimmedToken.isEmpty()) {
            return@withContext XcAccountAuthResult.Failure(
                "Google sign-in did not return an ID token."
            )
        }
        return@withContext when (
            val result = remoteDataSource.exchangeGoogleIdToken(trimmedToken)
        ) {
            is XcAccountRemoteResult.Success -> XcAccountAuthResult.Success(result.value)
            is XcAccountRemoteResult.Failure -> XcAccountAuthResult.Failure(result.error.message)
        }
    }

    override suspend fun restoreSession(
        session: XcAccountSession
    ): XcAccountAuthResult = withContext(ioDispatcher) {
        if (session.accessToken.isBlank()) {
            XcAccountAuthResult.Failure(
                "Your Google XCPro session is no longer available. Sign in again."
            )
        } else {
            XcAccountAuthResult.Success(session)
        }
    }

    override suspend fun signOut() = withContext(ioDispatcher) {
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
        Unit
    }

    private fun unavailable(
        config: XcGoogleSignInConfig = resolveXcGoogleSignInConfig()
    ): XcAccountAuthResult.Unavailable {
        return XcAccountAuthResult.Unavailable(
            config.availabilityNote ?: "Google sign-in is not configured in this build."
        )
    }
}
