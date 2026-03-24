package com.example.xcpro.livefollow.data.session

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
