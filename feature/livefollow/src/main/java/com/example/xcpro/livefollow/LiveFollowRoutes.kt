package com.example.xcpro.livefollow

import java.util.Locale

object LiveFollowRoutes {
    const val MAP_ROUTE = "map"
    const val PILOT = "livefollow/pilot"
    const val WATCH_SHARE_FORM = "livefollow/watch/share"
    const val WATCH_SESSION_ID_ARG = "sessionId"
    const val WATCH_SHARE_CODE_ARG = "shareCode"
    const val WATCH_ENTRY = "livefollow/watch/{$WATCH_SESSION_ID_ARG}"
    const val WATCH_SHARE_ENTRY = "livefollow/watch/share/{$WATCH_SHARE_CODE_ARG}"

    fun watchEntry(sessionId: String): String = "livefollow/watch/${sessionId.trim()}"

    fun watchShareEntry(shareCode: String): String =
        "livefollow/watch/share/${shareCode.trim().uppercase(Locale.US)}"
}

internal fun normalizeLiveFollowSessionId(rawSessionId: String?): String? {
    return rawSessionId?.trim()?.takeIf { sessionId -> sessionId.isNotEmpty() }
}

internal fun normalizeLiveFollowShareCode(rawShareCode: String?): String? {
    val normalized = rawShareCode
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    return normalized.takeIf { LIVEFOLLOW_SHARE_CODE_REGEX.matches(it) }
}

private val LIVEFOLLOW_SHARE_CODE_REGEX = Regex("^[A-Z0-9]{8}$")
