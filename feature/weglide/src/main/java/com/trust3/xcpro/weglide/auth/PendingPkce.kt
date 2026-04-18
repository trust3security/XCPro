package com.trust3.xcpro.weglide.auth

data class PendingPkce(
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String
)
