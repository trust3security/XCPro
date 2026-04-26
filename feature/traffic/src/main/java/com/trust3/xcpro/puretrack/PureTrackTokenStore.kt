package com.trust3.xcpro.puretrack

interface PureTrackTokenStore {
    suspend fun readSession(): PureTrackStoredSession?
    suspend fun saveSession(session: PureTrackStoredSession)
    suspend fun clearSession()
}
