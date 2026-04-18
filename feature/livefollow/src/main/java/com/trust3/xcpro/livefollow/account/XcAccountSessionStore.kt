package com.trust3.xcpro.livefollow.account

interface XcAccountSessionStore {
    suspend fun loadSession(): XcAccountSession?

    suspend fun saveSession(session: XcAccountSession)

    suspend fun clearSession()
}
