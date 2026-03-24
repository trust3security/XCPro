package com.example.xcpro.livefollow.account

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfiguredBuildTokenXcAccountAuthProviderTest {

    @Test
    fun signInCapabilities_hiddenWhenDevAuthDisabled() {
        val provider = ConfiguredBuildTokenXcAccountAuthProvider(
            configuredDevBearerAuthEnabled = false,
            configuredDevBearerToken = "dev-token"
        )

        assertTrue(provider.signInCapabilities().isEmpty())
    }

    @Test
    fun signInCapabilities_hiddenWhenTokenMissing() {
        val provider = ConfiguredBuildTokenXcAccountAuthProvider(
            configuredDevBearerAuthEnabled = true,
            configuredDevBearerToken = ""
        )

        assertTrue(provider.signInCapabilities().isEmpty())
    }

    @Test
    fun signIn_returnsUnavailableWhenDevAuthDisabled() = runTest {
        val provider = ConfiguredBuildTokenXcAccountAuthProvider(
            configuredDevBearerAuthEnabled = false,
            configuredDevBearerToken = "dev-token"
        )

        val result = provider.signIn(XcAccountSignInMethod.CONFIGURED_DEV_TOKEN)

        assertEquals(
            XcAccountAuthResult.Unavailable(
                "Configured dev bearer auth is unavailable in this build."
            ),
            result
        )
    }

    @Test
    fun signIn_returnsSessionWhenDebugTokenConfigured() = runTest {
        val provider = ConfiguredBuildTokenXcAccountAuthProvider(
            configuredDevBearerAuthEnabled = true,
            configuredDevBearerToken = "dev-token"
        )

        val result = provider.signIn(XcAccountSignInMethod.CONFIGURED_DEV_TOKEN)

        assertEquals(
            XcAccountAuthResult.Success(
                XcAccountSession(
                    accessToken = "dev-token",
                    authMethod = XcAccountAuthMethod.CONFIGURED_DEV_TOKEN
                )
            ),
            result
        )
    }
}
