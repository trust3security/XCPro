package com.trust3.xcpro.livefollow.account

import java.util.Locale

private const val UNAUTHENTICATED_ERROR_CODE = "unauthenticated"
private val XCPRO_HANDLE_REGEX = Regex("^[a-z0-9._]{3,24}$")

const val XCPRO_HANDLE_RULE_MESSAGE =
    "Handle must be 3-24 chars of lowercase letters, digits, underscore, or dot."

enum class XcDiscoverability(
    val wireValue: String,
    val title: String,
    val subtitle: String
) {
    SEARCHABLE(
        wireValue = "searchable",
        title = "Searchable",
        subtitle = "Allow your pilot profile to appear in future XCPro search."
    ),
    HIDDEN(
        wireValue = "hidden",
        title = "Hidden",
        subtitle = "Hide your pilot profile from future XCPro search."
    );

    companion object {
        fun fromWireValue(rawValue: String): XcDiscoverability {
            return entries.firstOrNull { it.wireValue == rawValue.trim() }
                ?: throw IllegalArgumentException("Unsupported discoverability: $rawValue")
        }
    }
}

enum class XcFollowPolicy(
    val wireValue: String,
    val title: String,
    val subtitle: String
) {
    APPROVAL_REQUIRED(
        wireValue = "approval_required",
        title = "Approval required",
        subtitle = "Followers must be approved before they can access future follower-only live flights."
    ),
    AUTO_APPROVE(
        wireValue = "auto_approve",
        title = "Allow all XCPro followers",
        subtitle = "Future followers are auto-approved, but this does not make live access public."
    ),
    CLOSED(
        wireValue = "closed",
        title = "Closed",
        subtitle = "Do not allow new followers in the future private-follow lane."
    );

    companion object {
        fun fromWireValue(rawValue: String): XcFollowPolicy {
            return entries.firstOrNull { it.wireValue == rawValue.trim() }
                ?: throw IllegalArgumentException("Unsupported follow policy: $rawValue")
        }
    }
}

enum class XcDefaultLiveVisibility(
    val wireValue: String,
    val title: String,
    val subtitle: String
) {
    OFF(
        wireValue = "off",
        title = "Off",
        subtitle = "Do not expose future authenticated live sessions by default."
    ),
    FOLLOWERS(
        wireValue = "followers",
        title = "Followers",
        subtitle = "Default future authenticated live sessions to follower visibility."
    ),
    PUBLIC(
        wireValue = "public",
        title = "Public",
        subtitle = "Default future authenticated live sessions to the public lane."
    );

    companion object {
        fun fromWireValue(rawValue: String): XcDefaultLiveVisibility {
            return entries.firstOrNull { it.wireValue == rawValue.trim() }
                ?: throw IllegalArgumentException("Unsupported default live visibility: $rawValue")
        }
    }
}

enum class XcConnectionListVisibility(
    val wireValue: String,
    val title: String,
    val subtitle: String
) {
    OWNER_ONLY(
        wireValue = "owner_only",
        title = "Owner only",
        subtitle = "Only you can view future follower/following lists."
    ),
    MUTUALS_ONLY(
        wireValue = "mutuals_only",
        title = "Mutuals only",
        subtitle = "Only mutual connections can view future follower/following lists."
    ),
    PUBLIC(
        wireValue = "public",
        title = "Public",
        subtitle = "Anyone can view future follower/following lists."
    );

    companion object {
        fun fromWireValue(rawValue: String): XcConnectionListVisibility {
            return entries.firstOrNull { it.wireValue == rawValue.trim() }
                ?: throw IllegalArgumentException("Unsupported connection list visibility: $rawValue")
        }
    }
}

data class XcPrivacySettings(
    val discoverability: XcDiscoverability,
    val followPolicy: XcFollowPolicy,
    val defaultLiveVisibility: XcDefaultLiveVisibility,
    val connectionListVisibility: XcConnectionListVisibility
) {
    companion object {
        val DEFAULT = XcPrivacySettings(
            discoverability = XcDiscoverability.SEARCHABLE,
            followPolicy = XcFollowPolicy.APPROVAL_REQUIRED,
            defaultLiveVisibility = XcDefaultLiveVisibility.FOLLOWERS,
            connectionListVisibility = XcConnectionListVisibility.OWNER_ONLY
        )
    }
}

data class XcPilotProfile(
    val userId: String,
    val handle: String?,
    val displayName: String?,
    val compNumber: String?
) {
    val isOnboardingComplete: Boolean
        get() = !handle.isNullOrBlank() && !displayName.isNullOrBlank()
}

enum class XcAccountAuthMethod(
    val storageValue: String,
    val label: String
) {
    GOOGLE(
        storageValue = "google",
        label = "Google"
    ),
    EMAIL_LINK(
        storageValue = "email_link",
        label = "Email link"
    ),
    CONFIGURED_DEV_TOKEN(
        storageValue = "configured_dev_token",
        label = "Configured dev token"
    );

    companion object {
        fun fromStorageValue(rawValue: String?): XcAccountAuthMethod {
            return entries.firstOrNull { it.storageValue == rawValue?.trim() }
                ?: CONFIGURED_DEV_TOKEN
        }
    }
}

data class XcAccountSession(
    val accessToken: String,
    val authMethod: XcAccountAuthMethod
)

enum class XcAccountSignInMethod {
    GOOGLE,
    EMAIL_LINK,
    CONFIGURED_DEV_TOKEN
}

data class XcAccountSignInCapability(
    val method: XcAccountSignInMethod,
    val title: String,
    val description: String,
    val isAvailable: Boolean,
    val availabilityNote: String? = null
)

data class XcAccountSnapshot(
    val isLoading: Boolean = true,
    val session: XcAccountSession? = null,
    val profile: XcPilotProfile? = null,
    val privacy: XcPrivacySettings? = null,
    val incomingFollowRequests: List<XcFollowRequestItem> = emptyList(),
    val outgoingFollowRequests: List<XcFollowRequestItem> = emptyList(),
    val signInCapabilities: List<XcAccountSignInCapability> = emptyList(),
    val errorMessage: String? = null
) {
    val isSignedIn: Boolean
        get() = session != null

    val needsProfileCompletion: Boolean
        get() = profile?.isOnboardingComplete == false
}

data class XcAccountApiError(
    val message: String,
    val code: String? = null,
    val httpCode: Int? = null
) {
    val isUnauthenticated: Boolean
        get() = httpCode == 401 || code == UNAUTHENTICATED_ERROR_CODE
}

sealed interface XcAccountActionResult {
    data object Success : XcAccountActionResult

    data class Failure(
        val message: String,
        val code: String? = null
    ) : XcAccountActionResult
}

sealed interface XcAccountValueResult<out T> {
    data class Success<T>(
        val value: T
    ) : XcAccountValueResult<T>

    data class Failure(
        val message: String,
        val code: String? = null
    ) : XcAccountValueResult<Nothing>
}

data class XcProfileUpdateRequest(
    val handle: String,
    val displayName: String,
    val compNumber: String?
)

data class XcPrivacyUpdateRequest(
    val discoverability: XcDiscoverability,
    val followPolicy: XcFollowPolicy,
    val defaultLiveVisibility: XcDefaultLiveVisibility,
    val connectionListVisibility: XcConnectionListVisibility
)

fun normalizeXcHandleCandidate(rawValue: String?): String? {
    val normalized = rawValue
        ?.trim()
        ?.lowercase(Locale.US)
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    return normalized.takeIf(XCPRO_HANDLE_REGEX::matches)
}

fun normalizeXcDisplayNameCandidate(rawValue: String?): String? {
    return rawValue?.trim()?.takeIf { it.isNotEmpty() }
}

fun normalizeXcCompNumberCandidate(rawValue: String?): String? {
    return rawValue?.trim()?.takeIf { it.isNotEmpty() }
}
