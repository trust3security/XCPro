package com.trust3.xcpro.livefollow.data.session

enum class LiveFollowWatchLookupType {
    SESSION_ID,
    AUTHENTICATED_SESSION_ID,
    SHARE_CODE
}

data class LiveFollowWatchLookup(
    val type: LiveFollowWatchLookupType,
    val value: String
)

internal fun liveFollowSessionIdLookup(sessionId: String): LiveFollowWatchLookup =
    LiveFollowWatchLookup(
        type = LiveFollowWatchLookupType.SESSION_ID,
        value = sessionId
    )

internal fun liveFollowAuthenticatedSessionIdLookup(sessionId: String): LiveFollowWatchLookup =
    LiveFollowWatchLookup(
        type = LiveFollowWatchLookupType.AUTHENTICATED_SESSION_ID,
        value = sessionId
    )

internal fun liveFollowShareCodeLookup(shareCode: String): LiveFollowWatchLookup =
    LiveFollowWatchLookup(
        type = LiveFollowWatchLookupType.SHARE_CODE,
        value = shareCode
    )

internal fun deriveLiveFollowWatchLookup(
    explicitLookup: LiveFollowWatchLookup?,
    shareCode: String?,
    sessionId: String?
): LiveFollowWatchLookup? {
    explicitLookup?.let { return it }
    // AI-NOTE: Public watch transport must prefer share-code lookup when it is
    // present so watch polling stays on the share-code endpoint instead of
    // silently collapsing back to a session-id poll.
    shareCode
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(::liveFollowShareCodeLookup)
        ?.let { return it }
    return sessionId
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(::liveFollowSessionIdLookup)
}
