package com.trust3.xcpro.livefollow.account

import kotlinx.coroutines.flow.MutableStateFlow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal fun signedOutAccountSnapshot(
    errorMessage: String? = null
): XcAccountSnapshot = XcAccountSnapshot(
    isLoading = false,
    errorMessage = errorMessage
)

internal fun signedInAccountSnapshot(
    accessToken: String = "token-abc",
    defaultLiveVisibility: XcDefaultLiveVisibility = XcDefaultLiveVisibility.FOLLOWERS
): XcAccountSnapshot = XcAccountSnapshot(
    isLoading = false,
    session = XcAccountSession(
        accessToken = accessToken,
        authMethod = XcAccountAuthMethod.CONFIGURED_DEV_TOKEN
    ),
    privacy = XcPrivacySettings.DEFAULT.copy(
        defaultLiveVisibility = defaultLiveVisibility
    )
)

internal fun mockXcAccountRepository(
    state: MutableStateFlow<XcAccountSnapshot> = MutableStateFlow(signedOutAccountSnapshot())
): XcAccountRepository {
    val repository: XcAccountRepository = mock()
    whenever(repository.state).thenReturn(state)
    return repository
}
