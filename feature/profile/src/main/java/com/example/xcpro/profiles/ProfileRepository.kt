package com.example.xcpro.profiles

import android.util.Log
import com.example.xcpro.core.time.Clock
import com.example.xcpro.core.time.DefaultClockProvider
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository(
    private val storage: ProfileStorage,
    private val profileBackupSink: ProfileBackupSink,
    private val profileSettingsSnapshotProvider: ProfileSettingsSnapshotProvider,
    private val profileSettingsRestoreApplier: ProfileSettingsRestoreApplier,
    private val profileScopedDataCleaner: ProfileScopedDataCleaner = NoOpProfileScopedDataCleaner(),
    private val profileDiagnosticsReporter: ProfileDiagnosticsReporter = LogcatProfileDiagnosticsReporter(),
    private val clock: Clock = DefaultClockProvider(),
    private val profileIdGenerator: ProfileIdGenerator = ProfileIdGenerator(),
    private val internalScope: CoroutineScope
) {
    @Inject
    constructor(
        storage: ProfileStorage,
        profileBackupSink: ProfileBackupSink,
        profileSettingsSnapshotProvider: ProfileSettingsSnapshotProvider,
        profileSettingsRestoreApplier: ProfileSettingsRestoreApplier,
        profileScopedDataCleaner: ProfileScopedDataCleaner,
        profileDiagnosticsReporter: ProfileDiagnosticsReporter,
        clock: Clock,
        profileIdGenerator: ProfileIdGenerator
    ) : this(
        storage,
        profileBackupSink,
        profileSettingsSnapshotProvider,
        profileSettingsRestoreApplier,
        profileScopedDataCleaner,
        profileDiagnosticsReporter,
        clock,
        profileIdGenerator,
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )
    constructor(
        storage: ProfileStorage,
        clock: Clock = DefaultClockProvider(),
        profileIdGenerator: ProfileIdGenerator = ProfileIdGenerator()
    ) : this(
        storage,
        NoOpProfileBackupSink(),
        NoOpProfileSettingsSnapshotProvider(),
        NoOpProfileSettingsRestoreApplier(),
        NoOpProfileScopedDataCleaner(),
        NoOpProfileDiagnosticsReporter(),
        clock,
        profileIdGenerator,
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )
    constructor(
        storage: ProfileStorage,
        internalScope: CoroutineScope,
        profileDiagnosticsReporter: ProfileDiagnosticsReporter = NoOpProfileDiagnosticsReporter(),
        clock: Clock = DefaultClockProvider(),
        profileIdGenerator: ProfileIdGenerator = ProfileIdGenerator()
    ) : this(
        storage,
        NoOpProfileBackupSink(),
        NoOpProfileSettingsSnapshotProvider(),
        NoOpProfileSettingsRestoreApplier(),
        NoOpProfileScopedDataCleaner(),
        profileDiagnosticsReporter,
        clock,
        profileIdGenerator,
        internalScope
    )
    private val gson = GsonBuilder().create()
    private companion object {
        private const val TAG = "ProfileRepository"
    }
    private val mutationMutex = Mutex()
    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList()); val profiles: StateFlow<List<UserProfile>> = _profiles.asStateFlow()
    private val _activeProfile = MutableStateFlow<UserProfile?>(null); val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()
    private val _bootstrapComplete = MutableStateFlow(false); val bootstrapComplete: StateFlow<Boolean> = _bootstrapComplete.asStateFlow()
    private val _bootstrapError = MutableStateFlow<String?>(null); val bootstrapError: StateFlow<String?> = _bootstrapError.asStateFlow()
    private var lastKnownGoodProfiles: List<UserProfile> = emptyList()
    private var lastKnownGoodActiveProfileId: String? = null
    private var suppressNextHydrationBackupSync = false
    private val hydrationCoordinator = ProfileRepositoryHydrationCoordinator(
        clock = clock,
        gson = gson,
        onError = ::logError,
        reportDiagnostic = ::reportDiagnostic,
        persistState = ::persistState,
        persistActiveProfileId = ::persistActiveProfileId
    )
    private val backupSyncCoordinator = ProfileRepositoryBackupSyncCoordinator(
        profileBackupSink = profileBackupSink,
        profileSettingsSnapshotProvider = profileSettingsSnapshotProvider,
        internalScope = internalScope,
        onError = ::logError,
        reportDiagnostic = ::reportDiagnostic
    )
    private val importCoordinator = ProfileRepositoryImportCoordinator(
        clock = clock,
        profileIdGenerator = profileIdGenerator
    )
    private val mutationCoordinator = ProfileRepositoryMutationCoordinator(
        profileScopedDataCleaner = profileScopedDataCleaner,
        clock = clock,
        profileIdGenerator = profileIdGenerator
    )
    private val bundleCoordinator = ProfileRepositoryBundleCoordinator(
        clock = clock,
        aircraftProfileSectionIds = ProfileSettingsSectionSets.AIRCRAFT_PROFILE_SECTION_IDS,
        profileSettingsRestoreApplier = profileSettingsRestoreApplier,
        captureSettingsSnapshot = backupSyncCoordinator::captureSettingsSnapshot,
        importProfiles = ::importProfiles
    )
    init {
        internalScope.launch {
            runCatching {
                storage.snapshotFlow.collect { snapshot ->
                    handleStorageSnapshot(snapshot)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _bootstrapError.value = "Profile storage stream failed."
                _bootstrapComplete.value = true
                logError("Profile snapshot stream failed", error)
                reportDiagnostic(
                    event = "profile_bootstrap_stream_failure",
                    attributes = mapOf("error" to (error.message ?: "unknown"))
                )
            }
        }
    }
    private suspend fun handleStorageSnapshot(snapshot: ProfileStorageSnapshot) {
        mutationMutex.withLock {
            when (snapshot.readStatus) {
                ProfileStorageReadStatus.OK -> hydrateFromSnapshot(snapshot)
                ProfileStorageReadStatus.IO_ERROR -> {
                    reportDiagnostic(
                        event = "profile_bootstrap_read_error",
                        attributes = mapOf("status" to "io_error")
                    )
                    markReadError("Failed to read stored profiles (I/O).")
                }
                ProfileStorageReadStatus.UNKNOWN_ERROR -> {
                    reportDiagnostic(
                        event = "profile_bootstrap_read_error",
                        attributes = mapOf("status" to "unknown_error")
                    )
                    markReadError("Failed to read stored profiles.")
                }
            }
        }
    }
    private suspend fun hydrateFromSnapshot(snapshot: ProfileStorageSnapshot) {
        val hydration = hydrationCoordinator.hydrateFromSnapshot(
            snapshot = snapshot,
            lastKnownGoodProfiles = lastKnownGoodProfiles,
            lastKnownGoodActiveProfileId = lastKnownGoodActiveProfileId,
            suppressNextHydrationBackupSync = suppressNextHydrationBackupSync
        )
        _profiles.value = hydration.profiles
        _activeProfile.value = hydration.activeProfile
        lastKnownGoodProfiles = hydration.profiles
        lastKnownGoodActiveProfileId = hydration.activeProfile?.id
        _bootstrapError.value = hydration.bootstrapError
        _bootstrapComplete.value = true
        suppressNextHydrationBackupSync = hydration.suppressNextHydrationBackupSync
        if (!hydration.parseFailed && !suppressNextHydrationBackupSync) {
            backupSyncCoordinator.scheduleProfileBackupSync(
                profiles = hydration.profiles,
                activeProfileId = hydration.activeProfile?.id
            )
        }
    }
    private fun markReadError(message: String) {
        _bootstrapError.value = message
        _bootstrapComplete.value = true
    }
    private fun logError(message: String, error: Throwable) {
        runCatching { Log.e(TAG, message, error) }
    }
    private fun reportDiagnostic(event: String, attributes: Map<String, String> = emptyMap()) {
        runCatching { profileDiagnosticsReporter.report(event = event, attributes = attributes) }
    }
    private suspend fun persistState(profiles: List<UserProfile>, activeProfileId: String?) {
        storage.writeState(
            profilesJson = gson.toJson(profiles),
            activeProfileId = activeProfileId
        )
    }
    private suspend fun persistActiveProfileId(id: String?) = storage.writeActiveProfileId(id)
    private fun commitState(profiles: List<UserProfile>, activeProfile: UserProfile?) {
        _profiles.value = profiles
        _activeProfile.value = activeProfile
        lastKnownGoodProfiles = profiles
        lastKnownGoodActiveProfileId = activeProfile?.id
        _bootstrapError.value = null
        _bootstrapComplete.value = true
        backupSyncCoordinator.scheduleProfileBackupSync(profiles, activeProfile?.id)
    }
    private suspend fun awaitBootstrapCompletion() {
        if (!_bootstrapComplete.value) bootstrapComplete.first { it }
    }
    suspend fun exportBundle(profileIds: Set<String>? = null): Result<ProfileBundleExportArtifact> {
        awaitBootstrapCompletion()
        val exportResult = mutationMutex.withLock {
            bundleCoordinator.exportBundle(
                availableProfiles = _profiles.value,
                activeProfileId = _activeProfile.value?.id,
                selectedProfileIds = profileIds
            )
        }
        exportResult
            .onSuccess { exportArtifact ->
                reportDiagnostic(
                    event = "profile_bundle_export_success",
                    attributes = mapOf("bytes" to exportArtifact.bundleJson.length.toString())
                )
            }
            .onFailure { error ->
                reportDiagnostic(
                    event = "profile_bundle_export_failure",
                    attributes = mapOf("error" to (error.message ?: "unknown"))
                )
        }
        return exportResult
    }
    suspend fun previewBundle(json: String): Result<ProfileBundlePreview> {
        awaitBootstrapCompletion()
        return mutationMutex.withLock {
            bundleCoordinator.previewBundle(
                json = json,
                existingProfiles = _profiles.value
            )
        }
    }
    suspend fun importBundle(request: ProfileBundleImportRequest): Result<ProfileBundleImportResult> {
        awaitBootstrapCompletion()
        val importResult = bundleCoordinator.importBundle(
            request = request,
            onParseFailure = { error ->
                reportDiagnostic(
                    event = "profile_bundle_import_parse_failure",
                    attributes = mapOf("error" to (error.message ?: "unknown"))
                )
            }
        )
        importResult
            .onSuccess { bundleResult ->
                reportDiagnostic(
                    event = "profile_bundle_import_success",
                    attributes = mapOf(
                        "importedCount" to bundleResult.profileImportResult.importedCount.toString(),
                        "failedSettingsSections" to bundleResult.settingsRestoreResult.failedSections.size.toString(),
                        "sourceFormat" to bundleResult.sourceFormat.name
                    )
                )
            }
            .onFailure { error ->
                reportDiagnostic(
                    event = "profile_bundle_import_failure",
                    attributes = mapOf("error" to (error.message ?: "unknown"))
                )
            }
        return importResult
    }
    suspend fun createProfile(request: ProfileCreationRequest): Result<UserProfile> {
        awaitBootstrapCompletion()
        return mutationMutex.withLock {
            runCatching {
                mutationCoordinator.createProfile(
                    request = request,
                    currentProfiles = _profiles.value,
                    currentActiveProfileId = _activeProfile.value?.id,
                    persistState = ::persistState,
                    commitState = ::commitState
                )
            }
        }
    }
    suspend fun importProfiles(request: ProfileImportRequest): Result<ProfileImportResult> {
        awaitBootstrapCompletion()
        return mutationMutex.withLock {
            runCatching {
                importCoordinator.importProfiles(
                    request = request,
                    currentProfiles = _profiles.value,
                    activeBeforeId = _activeProfile.value?.id,
                    persistState = ::persistState,
                    commitState = ::commitState
                )
            }
        }
    }
    suspend fun setActiveProfile(profile: UserProfile): Result<Unit> {
        awaitBootstrapCompletion()
        return mutationMutex.withLock {
            runCatching {
                mutationCoordinator.setActiveProfile(
                    profile = profile,
                    currentProfiles = _profiles.value,
                    persistState = ::persistState,
                    commitState = ::commitState
                )
            }
        }
    }
    suspend fun updateProfile(updatedProfile: UserProfile): Result<Unit> {
        awaitBootstrapCompletion()
        return mutationMutex.withLock {
            runCatching {
                mutationCoordinator.updateProfile(
                    updatedProfile = updatedProfile,
                    currentProfiles = _profiles.value,
                    currentActiveProfileId = _activeProfile.value?.id,
                    persistState = ::persistState,
                    commitState = ::commitState
                )
            }
        }
    }
    suspend fun deleteProfile(profileId: String): Result<Unit> {
        awaitBootstrapCompletion()
        return mutationMutex.withLock {
            runCatching {
                mutationCoordinator.deleteProfile(
                    profileId = profileId,
                    currentProfiles = _profiles.value,
                    currentActiveProfileId = _activeProfile.value?.id,
                    persistState = ::persistState,
                    commitState = ::commitState
                )
            }
        }
    }
    suspend fun recoverWithDefaultProfile(): Result<Unit> {
        awaitBootstrapCompletion()
        reportDiagnostic(
            event = "profile_recovery_start",
            attributes = mapOf(
                "profileCount" to _profiles.value.size.toString(),
                "hasActiveProfile" to (_activeProfile.value != null).toString()
            )
        )
        val recoveryResult = mutationMutex.withLock {
            runCatching {
                mutationCoordinator.recoverWithDefaultProfile(
                    currentProfiles = _profiles.value,
                    persistState = ::persistState,
                    commitState = ::commitState
                )
            }
        }
        recoveryResult
            .onSuccess {
                reportDiagnostic(
                    event = "profile_recovery_success",
                    attributes = mapOf(
                        "profileCount" to _profiles.value.size.toString(),
                        "activeProfileId" to (_activeProfile.value?.id ?: "null")
                    )
                )
            }
            .onFailure { error ->
                reportDiagnostic(
                    event = "profile_recovery_failure",
                    attributes = mapOf("error" to (error.message ?: "unknown"))
                )
            }
        return recoveryResult
    }
    fun hasProfiles(): Boolean = _profiles.value.isNotEmpty()
    fun hasActiveProfile(): Boolean = _activeProfile.value != null
}
