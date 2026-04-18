package com.trust3.xcpro.screens.navdrawer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.profiles.ProfileUseCase
import com.trust3.xcpro.profiles.UserProfile
import com.trust3.xcpro.weglide.domain.WeGlideAccountLink
import com.trust3.xcpro.weglide.domain.WeGlideAircraft
import com.trust3.xcpro.weglide.domain.WeGlideAircraftMapping
import com.trust3.xcpro.weglide.domain.WeGlideUploadPreferences
import com.trust3.xcpro.weglide.domain.WeGlideUploadQueueRecord
import com.trust3.xcpro.weglide.domain.WeGlideUploadState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WeGlideSettingsViewModel @Inject constructor(
    private val clock: Clock,
    profileUseCase: ProfileUseCase,
    private val useCase: WeGlideSettingsUseCase
) : ViewModel() {
    sealed interface Event {
        data class LaunchAuthorization(val uri: Uri) : Event
    }

    private data class AccountSection(
        val profiles: List<UserProfile>,
        val activeProfile: UserProfile?,
        val accountLink: WeGlideAccountLink?,
        val preferences: WeGlideUploadPreferences
    )

    private data class DataSection(
        val mappings: List<WeGlideAircraftMapping>,
        val aircraft: List<WeGlideAircraft>,
        val queue: List<WeGlideUploadQueueRecord>,
        val syncUiState: AircraftSyncUiState
    )

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events: SharedFlow<Event> = _events.asSharedFlow()
    private val aircraftSyncUiState = MutableStateFlow(AircraftSyncUiState())

    private val accountSectionFlow = combine(
        profileUseCase.profiles,
        profileUseCase.activeProfile,
        useCase.accountLinkFlow,
        useCase.preferencesFlow
    ) { profiles, activeProfile, accountLink, preferences ->
        AccountSection(
            profiles = profiles,
            activeProfile = activeProfile,
            accountLink = accountLink,
            preferences = preferences
        )
    }

    private val dataSectionFlow = combine(
        useCase.mappingsFlow,
        useCase.aircraftFlow,
        useCase.queueFlow,
        aircraftSyncUiState
    ) { mappings, aircraft, queue, syncUiState ->
        DataSection(
            mappings = mappings,
            aircraft = aircraft,
            queue = queue,
            syncUiState = syncUiState
        )
    }

    val uiState: StateFlow<WeGlideSettingsUiState> = combine(
        accountSectionFlow,
        dataSectionFlow
    ) { accountSection, dataSection ->
        val mappingsByProfileId = dataSection.mappings.associateBy { item -> item.localProfileId }
        val aircraftById = dataSection.aircraft.associateBy { item -> item.aircraftId }
        val aircraftOptions = dataSection.aircraft.map { item ->
            WeGlideAircraftOptionUiState(
                aircraftId = item.aircraftId,
                name = item.name,
                secondaryLabel = listOfNotNull(item.kind, item.scoringClass)
                    .joinToString(" | ")
                    .ifBlank { null }
            )
        }
        val mappingItems = accountSection.profiles.map { profile ->
            val mapping = mappingsByProfileId[profile.id]
            val remoteAircraftLabel = when {
                mapping == null -> "Not mapped"
                aircraftById[mapping.weglideAircraftId] != null ->
                    aircraftById.getValue(mapping.weglideAircraftId).name
                else -> "${mapping.weglideAircraftName} (not in cache)"
            }
            WeGlideProfileMappingUiState(
                profileId = profile.id,
                profileName = profile.name,
                localAircraftLabel = profile.getDisplayName(),
                remoteAircraftId = mapping?.weglideAircraftId,
                remoteAircraftLabel = remoteAircraftLabel,
                isActive = profile.id == accountSection.activeProfile?.id
            )
        }

        val pendingQueueCount = dataSection.queue.count { item ->
            item.uploadState == WeGlideUploadState.QUEUED ||
                item.uploadState == WeGlideUploadState.UPLOADING ||
                item.uploadState == WeGlideUploadState.FAILED_RETRYABLE
        }
        val uploadedCount = dataSection.queue.count { item ->
            item.uploadState == WeGlideUploadState.UPLOADED ||
                item.uploadState == WeGlideUploadState.SKIPPED_DUPLICATE
        }
        val lastFailureMessage = dataSection.queue
            .asSequence()
            .filter { item -> item.lastErrorMessage != null }
            .maxByOrNull { item -> item.updatedAtEpochMs }
            ?.lastErrorMessage

        WeGlideSettingsUiState(
            isConnected = accountSection.accountLink != null,
            oauthConfigured = useCase.isOAuthConfigured(),
            accountDisplayName = accountSection.accountLink?.displayName,
            accountEmail = accountSection.accountLink?.email,
            authModeLabel = accountSection.accountLink?.authMode?.name,
            autoUploadFinishedFlights = accountSection.preferences.autoUploadFinishedFlights,
            uploadOnWifiOnly = accountSection.preferences.uploadOnWifiOnly,
            retryOnMobileData = accountSection.preferences.retryOnMobileData,
            showCompletionNotification = accountSection.preferences.showCompletionNotification,
            debugEnabled = accountSection.preferences.debugEnabled,
            aircraftOptions = aircraftOptions,
            isAircraftSyncInProgress = dataSection.syncUiState.inProgress,
            lastAircraftSyncMessage = dataSection.syncUiState.message,
            profileMappings = mappingItems,
            cachedAircraftCount = dataSection.aircraft.size,
            pendingQueueCount = pendingQueueCount,
            uploadedCount = uploadedCount,
            lastFailureMessage = lastFailureMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = WeGlideSettingsUiState()
    )

    fun setAutoUploadFinishedFlights(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setAutoUploadFinishedFlights(enabled)
        }
    }

    fun setUploadOnWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setUploadOnWifiOnly(enabled)
        }
    }

    fun setRetryOnMobileData(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setRetryOnMobileData(enabled)
        }
    }

    fun setShowCompletionNotification(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setShowCompletionNotification(enabled)
        }
    }

    fun setDebugEnabled(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setDebugEnabled(enabled)
        }
    }

    fun connect() {
        viewModelScope.launch {
            useCase.beginConnect().getOrNull()?.let { uri ->
                _events.tryEmit(Event.LaunchAuthorization(uri))
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            useCase.disconnect()
        }
    }

    fun refreshAircraft() {
        if (aircraftSyncUiState.value.inProgress) return
        aircraftSyncUiState.value = AircraftSyncUiState(
            inProgress = true,
            message = "Syncing aircraft..."
        )
        viewModelScope.launch {
            val result = useCase.syncAircraft()
            aircraftSyncUiState.value = result.fold(
                onSuccess = { count ->
                    AircraftSyncUiState(
                        inProgress = false,
                        message = "Synced $count aircraft"
                    )
                },
                onFailure = { error ->
                    AircraftSyncUiState(
                        inProgress = false,
                        message = error.message ?: "Aircraft sync failed"
                    )
                }
            )
        }
    }

    fun setProfileAircraftMapping(profileId: String, aircraftId: Long) {
        viewModelScope.launch {
            val selectedAircraft = uiState.value.aircraftOptions
                .firstOrNull { item -> item.aircraftId == aircraftId }
                ?: return@launch
            useCase.setProfileAircraftMapping(
                profileId = profileId,
                aircraft = WeGlideAircraft(
                    aircraftId = selectedAircraft.aircraftId,
                    name = selectedAircraft.name,
                    kind = selectedAircraft.secondaryLabel,
                    scoringClass = null
                ),
                updatedAtEpochMs = clock.nowWallMs()
            )
        }
    }

    fun clearProfileAircraftMapping(profileId: String) {
        viewModelScope.launch {
            useCase.clearProfileAircraftMapping(profileId)
        }
    }

    private data class AircraftSyncUiState(
        val inProgress: Boolean = false,
        val message: String? = null
    )
}
