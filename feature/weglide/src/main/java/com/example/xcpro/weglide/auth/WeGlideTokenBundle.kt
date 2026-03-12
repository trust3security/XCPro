package com.example.xcpro.weglide.auth

data class WeGlideTokenBundle(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMs: Long,
    val tokenType: String = "Bearer"
)
