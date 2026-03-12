package com.example.xcpro.weglide.auth

import android.net.Uri

data class WeGlideOAuthConfig(
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val clientId: String,
    val redirectUri: String,
    val scope: String,
    val userInfoEndpoint: String
) {
    fun isConfigured(): Boolean {
        return authorizationEndpoint.isNotBlank() &&
            tokenEndpoint.isNotBlank() &&
            clientId.isNotBlank() &&
            redirectUri.isNotBlank()
    }

    fun authorizationUri(): Uri = Uri.parse(authorizationEndpoint)

    fun hasUserInfoEndpoint(): Boolean = userInfoEndpoint.isNotBlank()
}
