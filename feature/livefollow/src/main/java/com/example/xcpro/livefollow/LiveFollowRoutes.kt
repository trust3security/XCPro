package com.example.xcpro.livefollow

object LiveFollowRoutes {
    const val MAP_ROUTE = "map"
    const val PILOT = "livefollow/pilot"
    const val WATCH_SESSION_ID_ARG = "sessionId"
    const val WATCH_ENTRY = "livefollow/watch/{$WATCH_SESSION_ID_ARG}"

    fun watchEntry(sessionId: String): String = "livefollow/watch/${sessionId.trim()}"
}

internal fun normalizeLiveFollowSessionId(rawSessionId: String?): String? {
    return rawSessionId?.trim()?.takeIf { sessionId -> sessionId.isNotEmpty() }
}
