package com.trust3.xcpro.livefollow.account

internal data class XcAccountEditorState(
    val handle: String = "",
    val displayName: String = "",
    val compNumber: String = "",
    val privacy: XcPrivacySettings = XcPrivacySettings.DEFAULT
)

internal data class XcAccountRelationshipState(
    val searchQuery: String = "",
    val searchResults: List<XcSearchPilot> = emptyList(),
    val hasSearchedUsers: Boolean = false
)

internal data class XcAccountOperationState(
    val isSigningIn: Boolean = false,
    val isSavingProfile: Boolean = false,
    val isSavingPrivacy: Boolean = false,
    val isSearchingUsers: Boolean = false,
    val isUpdatingRelationships: Boolean = false,
    val isRefreshing: Boolean = false,
    val statusMessage: String? = null
) {
    val isBusy: Boolean
        get() = isSigningIn ||
            isSavingProfile ||
            isSavingPrivacy ||
            isSearchingUsers ||
            isUpdatingRelationships ||
            isRefreshing
}

internal fun XcAccountActionResult.failureMessage(): String? {
    return (this as? XcAccountActionResult.Failure)?.message
}
