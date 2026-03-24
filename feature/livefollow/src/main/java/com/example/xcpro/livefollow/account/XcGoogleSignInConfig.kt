package com.example.xcpro.livefollow.account

import com.example.xcpro.livefollow.BuildConfig

internal data class XcGoogleSignInConfig(
    val serverClientId: String?,
    val availabilityNote: String?
) {
    val isAvailable: Boolean
        get() = !serverClientId.isNullOrBlank()
}

internal fun resolveXcGoogleSignInConfig(): XcGoogleSignInConfig {
    val serverClientId = BuildConfig.XCPRO_GOOGLE_SERVER_CLIENT_ID.trim().takeIf { it.isNotEmpty() }
    val availabilityNote = if (serverClientId == null) {
        "Google sign-in requires XCPRO_GOOGLE_SERVER_CLIENT_ID in this build."
    } else {
        null
    }
    return XcGoogleSignInConfig(
        serverClientId = serverClientId,
        availabilityNote = availabilityNote
    )
}
