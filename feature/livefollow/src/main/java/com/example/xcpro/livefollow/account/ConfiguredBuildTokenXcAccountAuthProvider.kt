package com.example.xcpro.livefollow.account

import com.example.xcpro.livefollow.di.ConfiguredDevBearerToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfiguredBuildTokenXcAccountAuthProvider @Inject constructor(
    @ConfiguredDevBearerToken
    private val configuredDevBearerToken: String
) : XcAccountAuthProvider {

    override fun signInCapabilities(): List<XcAccountSignInCapability> {
        val configuredDevTokenAvailable = configuredDevBearerToken.isNotBlank()
        return buildList {
            if (!configuredDevTokenAvailable) return@buildList
            add(
                XcAccountSignInCapability(
                    method = XcAccountSignInMethod.CONFIGURED_DEV_TOKEN,
                    title = "Use configured dev account",
                    description = "Bootstraps the private-follow account seam with a preconfigured bearer token.",
                    isAvailable = true
                )
            )
        }
    }

    override suspend fun signIn(
        method: XcAccountSignInMethod
    ): XcAccountAuthResult {
        return when (method) {
            XcAccountSignInMethod.GOOGLE -> XcAccountAuthResult.Unavailable(
                "Google sign-in is not configured in this build."
            )

            XcAccountSignInMethod.EMAIL_LINK -> XcAccountAuthResult.Unavailable(
                "Email-link sign-in is not configured in this build."
            )

            XcAccountSignInMethod.CONFIGURED_DEV_TOKEN -> {
                val token = configuredDevBearerToken.trim()
                if (token.isBlank()) {
                    XcAccountAuthResult.Unavailable(
                        "No configured dev bearer token is available in this build."
                    )
                } else {
                    XcAccountAuthResult.Success(
                        XcAccountSession(
                            accessToken = token,
                            authMethod = XcAccountAuthMethod.CONFIGURED_DEV_TOKEN
                        )
                    )
                }
            }
        }
    }
}
