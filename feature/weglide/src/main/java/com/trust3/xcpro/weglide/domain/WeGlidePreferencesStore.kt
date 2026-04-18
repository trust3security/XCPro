package com.trust3.xcpro.weglide.domain

import kotlinx.coroutines.flow.Flow

interface WeGlidePreferencesStore {
    val preferences: Flow<WeGlideUploadPreferences>

    suspend fun setAutoUploadFinishedFlights(enabled: Boolean)

    suspend fun setUploadOnWifiOnly(enabled: Boolean)

    suspend fun setRetryOnMobileData(enabled: Boolean)

    suspend fun setShowCompletionNotification(enabled: Boolean)

    suspend fun setDebugEnabled(enabled: Boolean)
}
