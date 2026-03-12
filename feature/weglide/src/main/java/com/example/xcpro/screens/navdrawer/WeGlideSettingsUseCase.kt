package com.example.xcpro.screens.navdrawer

import android.net.Uri
import com.example.xcpro.weglide.auth.WeGlideAuthManager
import com.example.xcpro.weglide.data.WeGlideAircraftSyncService
import com.example.xcpro.weglide.domain.WeGlideAccountStore
import com.example.xcpro.weglide.domain.WeGlideAircraft
import com.example.xcpro.weglide.domain.WeGlideLocalStateRepository
import com.example.xcpro.weglide.domain.WeGlidePreferencesStore
import javax.inject.Inject

class WeGlideSettingsUseCase @Inject constructor(
    private val authManager: WeGlideAuthManager,
    private val aircraftSyncService: WeGlideAircraftSyncService,
    private val accountStore: WeGlideAccountStore,
    private val preferencesStore: WeGlidePreferencesStore,
    private val localStateRepository: WeGlideLocalStateRepository
) {
    val accountLinkFlow = accountStore.accountLink
    val preferencesFlow = preferencesStore.preferences
    val mappingsFlow = localStateRepository.observeMappings()
    val aircraftFlow = localStateRepository.observeAircraft()
    val queueFlow = localStateRepository.observeQueue()

    suspend fun setAutoUploadFinishedFlights(enabled: Boolean) {
        preferencesStore.setAutoUploadFinishedFlights(enabled)
    }

    suspend fun setUploadOnWifiOnly(enabled: Boolean) {
        preferencesStore.setUploadOnWifiOnly(enabled)
    }

    suspend fun setRetryOnMobileData(enabled: Boolean) {
        preferencesStore.setRetryOnMobileData(enabled)
    }

    suspend fun setShowCompletionNotification(enabled: Boolean) {
        preferencesStore.setShowCompletionNotification(enabled)
    }

    suspend fun setDebugEnabled(enabled: Boolean) {
        preferencesStore.setDebugEnabled(enabled)
    }

    suspend fun beginConnect(): Result<Uri> = authManager.buildAuthorizationUri()

    suspend fun disconnect() {
        authManager.disconnect()
    }

    fun isOAuthConfigured(): Boolean = authManager.isConfigured()

    suspend fun syncAircraft(): Result<Int> = aircraftSyncService.sync()

    suspend fun setProfileAircraftMapping(profileId: String, aircraft: WeGlideAircraft, updatedAtEpochMs: Long) {
        localStateRepository.saveMapping(profileId, aircraft, updatedAtEpochMs)
    }

    suspend fun clearProfileAircraftMapping(profileId: String) {
        localStateRepository.clearMapping(profileId)
    }
}
