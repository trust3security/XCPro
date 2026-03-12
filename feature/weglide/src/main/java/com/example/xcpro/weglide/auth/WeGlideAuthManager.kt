package com.example.xcpro.weglide.auth

import android.net.Uri
import com.example.xcpro.core.time.Clock
import com.example.xcpro.weglide.domain.WeGlideAccountLink
import com.example.xcpro.weglide.domain.WeGlideAccountStore
import com.example.xcpro.weglide.domain.WeGlideAuthMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeGlideAuthManager @Inject constructor(
    private val tokenStore: WeGlideTokenStore,
    private val authApi: WeGlideAuthApi,
    private val accountApi: WeGlideAccountApi,
    private val config: WeGlideOAuthConfig,
    private val accountStore: WeGlideAccountStore,
    private val clock: Clock
) {
    suspend fun buildAuthorizationUri(): Result<Uri> = runCatching {
        check(config.isConfigured()) { "WeGlide OAuth config is incomplete" }
        val pkce = WeGlidePkceFactory.create()
        tokenStore.savePendingPkce(pkce)
        config.authorizationUri()
            .buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("code_challenge", pkce.codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", pkce.state)
            .appendQueryParameter("scope", config.scope)
            .build()
    }

    suspend fun handleAuthorizationRedirect(uri: Uri): Result<Unit> = runCatching {
        val error = uri.getQueryParameter("error")
        check(error.isNullOrBlank()) { "WeGlide authorization failed: $error" }

        val code = uri.getQueryParameter("code").orEmpty()
        val state = uri.getQueryParameter("state").orEmpty()
        check(code.isNotBlank()) { "Missing WeGlide authorization code" }
        check(state.isNotBlank()) { "Missing WeGlide authorization state" }

        val pendingPkce = tokenStore.getPendingPkce()
            ?: error("Missing pending WeGlide PKCE state")
        require(state == pendingPkce.state) { "WeGlide OAuth state mismatch" }

        val response = authApi.exchangeCode(
            tokenUrl = config.tokenEndpoint,
            code = code,
            redirectUri = config.redirectUri,
            clientId = config.clientId,
            codeVerifier = pendingPkce.codeVerifier
        )
        check(response.isSuccessful) {
            "WeGlide token exchange failed with HTTP ${response.code()}"
        }
        val body = response.body() ?: error("Empty WeGlide token response")
        val expiresInMs = (body.expiresInSeconds ?: 0L) * 1_000L
        val tokens = WeGlideTokenBundle(
            accessToken = body.accessToken,
            refreshToken = body.refreshToken,
            expiresAtEpochMs = clock.nowWallMs() + expiresInMs,
            tokenType = body.tokenType?.ifBlank { "Bearer" } ?: "Bearer"
        )
        tokenStore.saveTokens(tokens)
        tokenStore.clearPendingPkce()
        accountStore.saveAccountLink(fetchAccountLink(tokens.accessToken))
    }

    suspend fun disconnect() {
        tokenStore.clearPendingPkce()
        tokenStore.clearTokens()
        accountStore.clearAccountLink()
    }

    fun isConfigured(): Boolean = config.isConfigured()

    suspend fun getValidAccessToken(): String? {
        val current = tokenStore.getTokens() ?: return null
        if (current.accessToken.isBlank()) return null
        if (isTokenFresh(current)) return current.accessToken

        val refreshToken = current.refreshToken?.trim().orEmpty()
        if (refreshToken.isBlank()) {
            disconnect()
            return null
        }

        val response = authApi.refreshToken(
            tokenUrl = config.tokenEndpoint,
            refreshToken = refreshToken,
            clientId = config.clientId
        )
        if (!response.isSuccessful) {
            disconnect()
            return null
        }
        val body = response.body() ?: run {
            disconnect()
            return null
        }
        val refreshed = WeGlideTokenBundle(
            accessToken = body.accessToken,
            refreshToken = body.refreshToken ?: current.refreshToken,
            expiresAtEpochMs = clock.nowWallMs() + ((body.expiresInSeconds ?: 0L) * 1_000L),
            tokenType = body.tokenType?.ifBlank { current.tokenType } ?: current.tokenType
        )
        tokenStore.saveTokens(refreshed)
        accountStore.saveAccountLink(
            fetchAccountLink(refreshed.accessToken).copy(
                connectedAtEpochMs = existingAccountSafeTime()
            )
        )
        return refreshed.accessToken
    }

    private fun isTokenFresh(tokens: WeGlideTokenBundle): Boolean {
        if (tokens.expiresAtEpochMs <= 0L) return true
        return tokens.expiresAtEpochMs > clock.nowWallMs() + TOKEN_REFRESH_SKEW_MS
    }

    private suspend fun fetchAccountLink(accessToken: String): WeGlideAccountLink {
        if (!config.hasUserInfoEndpoint()) {
            return defaultConnectedAccountLink()
        }
        val response = accountApi.getCurrentAccount(
            url = config.userInfoEndpoint,
            authorization = "Bearer $accessToken"
        )
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            WeGlideAccountLink(
                userId = body.id,
                displayName = body.name?.ifBlank { "Connected" } ?: "Connected",
                email = body.email?.ifBlank { null },
                connectedAtEpochMs = clock.nowWallMs(),
                authMode = WeGlideAuthMode.OAUTH
            )
        } else {
            defaultConnectedAccountLink()
        }
    }

    private fun defaultConnectedAccountLink(): WeGlideAccountLink {
        return WeGlideAccountLink(
            userId = null,
            displayName = "Connected",
            email = null,
            connectedAtEpochMs = clock.nowWallMs(),
            authMode = WeGlideAuthMode.OAUTH
        )
    }

    private suspend fun existingAccountSafeTime(): Long {
        return accountStore.accountLinkReplaySafe()?.connectedAtEpochMs ?: clock.nowWallMs()
    }

    private companion object {
        private const val TOKEN_REFRESH_SKEW_MS = 60_000L
    }
}
