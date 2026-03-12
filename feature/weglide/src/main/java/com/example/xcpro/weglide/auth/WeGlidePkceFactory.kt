package com.example.xcpro.weglide.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object WeGlidePkceFactory {
    private val secureRandom = SecureRandom()

    fun create(): PendingPkce {
        val codeVerifier = randomUrlSafeString(64)
        val state = randomUrlSafeString(32)
        return PendingPkce(
            codeVerifier = codeVerifier,
            codeChallenge = sha256Base64Url(codeVerifier),
            state = state
        )
    }

    private fun randomUrlSafeString(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    private fun sha256Base64Url(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(digest)
    }
}
