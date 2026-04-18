package com.trust3.xcpro.screens.navdrawer

data class WeGlideAircraftOptionUiState(
    val aircraftId: Long,
    val name: String,
    val secondaryLabel: String?
)

data class WeGlideProfileMappingUiState(
    val profileId: String,
    val profileName: String,
    val localAircraftLabel: String,
    val remoteAircraftId: Long?,
    val remoteAircraftLabel: String,
    val isActive: Boolean
)

data class WeGlideSettingsUiState(
    val isConnected: Boolean = false,
    val oauthConfigured: Boolean = false,
    val accountDisplayName: String? = null,
    val accountEmail: String? = null,
    val authModeLabel: String? = null,
    val autoUploadFinishedFlights: Boolean = false,
    val uploadOnWifiOnly: Boolean = false,
    val retryOnMobileData: Boolean = true,
    val showCompletionNotification: Boolean = true,
    val debugEnabled: Boolean = false,
    val aircraftOptions: List<WeGlideAircraftOptionUiState> = emptyList(),
    val isAircraftSyncInProgress: Boolean = false,
    val lastAircraftSyncMessage: String? = null,
    val profileMappings: List<WeGlideProfileMappingUiState> = emptyList(),
    val cachedAircraftCount: Int = 0,
    val pendingQueueCount: Int = 0,
    val uploadedCount: Int = 0,
    val lastFailureMessage: String? = null
)
