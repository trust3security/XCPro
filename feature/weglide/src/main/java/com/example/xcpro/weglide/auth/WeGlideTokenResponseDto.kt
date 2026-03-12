package com.example.xcpro.weglide.auth

import com.google.gson.annotations.SerializedName

data class WeGlideTokenResponseDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresInSeconds: Long? = null,
    @SerializedName("token_type") val tokenType: String? = null,
    val scope: String? = null
)
