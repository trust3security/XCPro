package com.trust3.xcpro.weglide.domain

data class WeGlideUploadPreferences(
    val autoUploadFinishedFlights: Boolean = false,
    val uploadOnWifiOnly: Boolean = false,
    val retryOnMobileData: Boolean = true,
    val showCompletionNotification: Boolean = true,
    val debugEnabled: Boolean = false
)
