package com.trust3.xcpro.weglide.auth

interface WeGlideTokenStore {
    suspend fun getTokens(): WeGlideTokenBundle?
    suspend fun saveTokens(tokens: WeGlideTokenBundle)
    suspend fun clearTokens()
    suspend fun savePendingPkce(pkce: PendingPkce)
    suspend fun getPendingPkce(): PendingPkce?
    suspend fun clearPendingPkce()
}
