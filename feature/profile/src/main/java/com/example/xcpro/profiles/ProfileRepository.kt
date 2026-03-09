package com.example.xcpro.profiles

import android.util.Log
import com.example.xcpro.core.time.TimeBridge
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
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
    private val internalScope: CoroutineScope
) {

    @Inject
    constructor(
        storage: ProfileStorage,
        profileBackupSink: ProfileBackupSink,
        profileSettingsSnapshotProvider: ProfileSettingsSnapshotProvider,
        profileSettingsRestoreApplier: ProfileSettingsRestoreApplier,
        profileScopedDataCleaner: ProfileScopedDataCleaner,
        profileDiagnosticsReporter: ProfileDiagnosticsReporter
    ) : this(
        storage = storage,
        profileBackupSink = profileBackupSink,
        profileSettingsSnapshotProvider = profileSettingsSnapshotProvider,
        profileSettingsRestoreApplier = profileSettingsRestoreApplier,
        profileScopedDataCleaner = profileScopedDataCleaner,
        profileDiagnosticsReporter = profileDiagnosticsReporter,
        internalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    constructor(storage: ProfileStorage) : this(
        storage = storage,
        profileBackupSink = NoOpProfileBackupSink(),
        profileSettingsSnapshotProvider = NoOpProfileSettingsSnapshotProvider(),
        profileSettingsRestoreApplier = NoOpProfileSettingsRestoreApplier(),
        profileScopedDataCleaner = NoOpProfileScopedDataCleaner(),
        profileDiagnosticsReporter = NoOpProfileDiagnosticsReporter(),
        internalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    constructor(
        storage: ProfileStorage,
        internalScope: CoroutineScope,
        profileDiagnosticsReporter: ProfileDiagnosticsReporter = NoOpProfileDiagnosticsReporter()
    ) : this(
        storage = storage,
        profileBackupSink = NoOpProfileBackupSink(),
        profileSettingsSnapshotProvider = NoOpProfileSettingsSnapshotProvider(),
        profileSettingsRestoreApplier = NoOpProfileSettingsRestoreApplier(),
        profileScopedDataCleaner = NoOpProfileScopedDataCleaner(),
        profileDiagnosticsReporter = profileDiagnosticsReporter,
        internalScope = internalScope
    )

    private val gson = GsonBuilder().create()

    private companion object {
        private const val TAG = "ProfileRepository"
        private const val DEFAULT_PROFILE_NAME = "Default"
    }

    private val mutationMutex = Mutex()

    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val profiles: StateFlow<List<UserProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<UserProfile?>(null)
    val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()

    private val _bootstrapComplete = MutableStateFlow(false)
    val bootstrapComplete: StateFlow<Boolean> = _bootstrapComplete.asStateFlow()

    private val _bootstrapError = MutableStateFlow<String?>(null)
    val bootstrapError: StateFlow<String?> = _bootstrapError.asStateFlow()

    private var lastKnownGoodProfiles: List<UserProfile> = emptyList()
    private var lastKnownGoodActiveProfileId: String? = null
    private val backupSyncSequence = AtomicLong(0L)
    private var suppressNextHydrationBackupSync = false

    init {
        internalScope.launch {
            runCatching {
                storage.snapshotFlow.collect { snapshot ->
                    handleStorageSnapshot(snapshot)
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
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
                    reportDiagnostic(event = "profile_bootstrap_read_error", attributes = mapOf("status" to "io_error"))
                    markReadError("Failed to read stored profiles (I/O).")
                }
                ProfileStorageReadStatus.UNKNOWN_ERROR -> {
                    reportDiagnostic(event = "profile_bootstrap_read_error", attributes = mapOf("status" to "unknown_error"))
                    markReadError("Failed to read stored profiles.")
                }
            }
        }
    }

    private suspend fun hydrateFromSnapshot(snapshot: ProfileStorageSnapshot) {
        val parseResult = parseProfiles(snapshot.profilesJson, fallback = lastKnownGoodProfiles)
        val defaultProvisioning = ensureBootstrapProfile(parseResult.profiles)
        val loadedProfiles = defaultProvisioning.profiles
        _profiles.value = loadedProfiles

        val activeIdForResolution = if (parseResult.parseFailed) {
            lastKnownGoodActiveProfileId
        } else {
            snapshot.activeProfileId
        }
        val resolvedActive = resolveActiveProfile(activeIdForResolution, loadedProfiles)
        _activeProfile.value = resolvedActive

        val resolvedActiveId = resolvedActive?.id
        var message = parseResult.bootstrapMessage
        if (parseResult.parseFailed) {
            reportDiagnostic(
                event = "profile_bootstrap_parse_failed",
                attributes = mapOf("fallbackProfileCount" to loadedProfiles.size.toString())
            )
        }
        val shouldRepairSnapshot = if (parseResult.parseFailed) {
            defaultProvisioning.insertedDefaultProfile || defaultProvisioning.migratedLegacyDefaultAlias
        } else {
            defaultProvisioning.insertedDefaultProfile ||
                defaultProvisioning.migratedLegacyDefaultAlias ||
                snapshot.activeProfileId != resolvedActiveId
        }
        if (parseResult.parseFailed && defaultProvisioning.insertedDefaultProfile) {
            message = mergeMessages(message, "Recovered with a default profile.")
        }
        if (shouldRepairSnapshot) {
            runCatching {
                if (parseResult.parseFailed) {
                    // Parse-failure recovery should not trigger immediate managed-backup cleanup.
                    suppressNextHydrationBackupSync = true
                }
                if (defaultProvisioning.insertedDefaultProfile || defaultProvisioning.migratedLegacyDefaultAlias) {
                    persistState(loadedProfiles, resolvedActiveId)
                } else {
                    persistActiveProfileId(resolvedActiveId)
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                if (parseResult.parseFailed) {
                    suppressNextHydrationBackupSync = false
                }
                logError("Failed to repair profile bootstrap snapshot", error)
                reportDiagnostic(
                    event = "profile_bootstrap_repair_failure",
                    attributes = mapOf("error" to (error.message ?: "unknown"))
                )
                message = mergeMessages(message, "Failed to persist active profile selection.")
            }
        }

        lastKnownGoodProfiles = loadedProfiles
        lastKnownGoodActiveProfileId = resolvedActiveId
        _bootstrapError.value = message
        _bootstrapComplete.value = true
        if (!parseResult.parseFailed && !suppressNextHydrationBackupSync) {
            scheduleProfileBackupSync(loadedProfiles, resolvedActiveId)
        }
        if (!parseResult.parseFailed && suppressNextHydrationBackupSync) {
            suppressNextHydrationBackupSync = false
        }
    }

    private fun markReadError(message: String) {
        // Keep last-known-good in-memory state unchanged on degraded reads.
        _bootstrapError.value = message
        _bootstrapComplete.value = true
    }

    private fun parseProfiles(json: String?, fallback: List<UserProfile>): ParseProfilesResult {
        if (json.isNullOrBlank()) {
            return ParseProfilesResult(
                profiles = emptyList(),
                bootstrapMessage = null,
                parseFailed = false
            )
        }

        val parsed = runCatching {
            val rootElement = JsonParser.parseString(json)
            if (!rootElement.isJsonArray) {
                error("Profile payload must be a JSON array")
            }
            val parsedProfiles = mutableListOf<UserProfile?>()
            rootElement.asJsonArray.forEach { element ->
                if (element == null || element.isJsonNull) {
                    parsedProfiles += null
                    return@forEach
                }
                val parsedProfile = runCatching {
                    gson.fromJson(element, UserProfile::class.java)
                }.getOrNull()
                parsedProfiles += parsedProfile
            }
            parsedProfiles
        }.getOrElse { error ->
            logError("Failed to parse profiles JSON", error)
            return ParseProfilesResult(
                profiles = fallback,
                bootstrapMessage = "Failed to parse stored profiles.",
                parseFailed = true
            )
        }

        val sanitized = sanitizeProfiles(parsed)
        val message = if (sanitized.droppedInvalidEntries) {
            "Some stored profiles were invalid and were ignored."
        } else {
            null
        }
        return ParseProfilesResult(
            profiles = sanitized.profiles,
            bootstrapMessage = message,
            parseFailed = false
        )
    }

    private fun sanitizeProfiles(rawProfiles: List<UserProfile?>): SanitizedProfilesResult {
        val unique = LinkedHashMap<String, UserProfile>()
        var dropped = false

        rawProfiles.forEach { profile ->
            if (profile == null) {
                dropped = true
                return@forEach
            }
            val validation = runCatching {
                val id = profile.id.trim()
                val name = profile.name.trim()
                val typeValid = profile.aircraftType.name.isNotBlank()
                Triple(id, name, typeValid)
            }.getOrNull()

            if (validation == null) {
                dropped = true
                return@forEach
            }
            val (id, name, typeValid) = validation
            if (id.isBlank() || name.isBlank() || !typeValid) {
                dropped = true
                return@forEach
            }
            if (unique.put(id, profile) != null) {
                dropped = true
            }
        }

        return SanitizedProfilesResult(
            profiles = unique.values.toList(),
            droppedInvalidEntries = dropped
        )
    }

    private fun ensureBootstrapProfile(profiles: List<UserProfile>): DefaultProfileProvisioningResult {
        if (profiles.isEmpty()) {
            return DefaultProfileProvisioningResult(
                profiles = listOf(buildDefaultProfile()),
                insertedDefaultProfile = true,
                migratedLegacyDefaultAlias = false
            )
        }
        if (profiles.any { it.id == ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID }) {
            return DefaultProfileProvisioningResult(
                profiles = profiles,
                insertedDefaultProfile = false,
                migratedLegacyDefaultAlias = false
            )
        }

        val legacyDefaultIndex = profiles.indexOfFirst { ProfileIdResolver.isLegacyDefaultAlias(it.id) }
        if (legacyDefaultIndex >= 0) {
            val migrated = profiles.toMutableList()
            val legacyDefault = migrated[legacyDefaultIndex]
            migrated[legacyDefaultIndex] = legacyDefault.copy(
                id = ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID
            )
            return DefaultProfileProvisioningResult(
                profiles = dedupeProfilesById(migrated),
                insertedDefaultProfile = false,
                migratedLegacyDefaultAlias = true
            )
        }

        return DefaultProfileProvisioningResult(
            profiles = listOf(buildDefaultProfile()) + profiles,
            insertedDefaultProfile = true,
            migratedLegacyDefaultAlias = false
        )
    }

    private fun buildDefaultProfile(): UserProfile =
        UserProfile(
            id = ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
            name = DEFAULT_PROFILE_NAME,
            aircraftType = AircraftType.PARAGLIDER,
            createdAt = TimeBridge.nowWallMs(),
            lastUsed = TimeBridge.nowWallMs()
        )

    private fun dedupeProfilesById(profiles: List<UserProfile>): List<UserProfile> {
        val deduped = LinkedHashMap<String, UserProfile>()
        profiles.forEach { profile ->
            if (!deduped.containsKey(profile.id)) {
                deduped[profile.id] = profile
            }
        }
        return deduped.values.toList()
    }

    private fun fallbackActiveProfile(profiles: List<UserProfile>): UserProfile? =
        profiles.firstOrNull { !ProfileIdResolver.isCanonicalDefault(it.id) } ?: profiles.firstOrNull()

    private fun resolveActiveProfile(id: String?, profiles: List<UserProfile>): UserProfile? {
        val normalizedId = ProfileIdResolver.normalizeOrNull(id)
        return when {
            profiles.isEmpty() -> null
            normalizedId == null -> fallbackActiveProfile(profiles)
            else -> profiles.find { profile ->
                ProfileIdResolver.normalizeOrNull(profile.id) == normalizedId
            } ?: fallbackActiveProfile(profiles)
        }
    }

    private fun mergeMessages(primary: String?, secondary: String): String =
        if (primary.isNullOrBlank()) secondary else "$primary $secondary"

    private fun logError(message: String, error: Throwable) {
        runCatching { Log.e(TAG, message, error) }
    }

    private fun reportDiagnostic(event: String, attributes: Map<String, String> = emptyMap()) {
        runCatching {
            profileDiagnosticsReporter.report(event = event, attributes = attributes)
        }
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
        scheduleProfileBackupSync(profiles, activeProfile?.id)
    }

    private fun scheduleProfileBackupSync(profiles: List<UserProfile>, activeProfileId: String?) {
        val snapshotProfiles = profiles.toList()
        val snapshotProfileIds = snapshotProfiles.mapTo(linkedSetOf()) { it.id }
        val sequenceNumber = backupSyncSequence.incrementAndGet()
        internalScope.launch {
            runCatching {
                val settingsSnapshot = captureSettingsSnapshot(snapshotProfileIds)
                profileBackupSink.syncSnapshot(
                    profiles = snapshotProfiles,
                    activeProfileId = activeProfileId,
                    settingsSnapshot = settingsSnapshot,
                    sequenceNumber = sequenceNumber
                )
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                logError("Failed to sync profile backup folder", error)
                reportDiagnostic(
                    event = "profile_backup_sync_failure",
                    attributes = mapOf(
                        "sequenceNumber" to sequenceNumber.toString(),
                        "error" to (error.message ?: "unknown")
                    )
                )
            }
        }
    }

    private suspend fun captureSettingsSnapshot(profileIds: Set<String>): ProfileSettingsSnapshot {
        return runCatching {
            profileSettingsSnapshotProvider.buildSnapshot(profileIds)
        }.getOrElse { error ->
            if (error is CancellationException) {
                throw error
            }
            logError("Failed to capture profile settings snapshot", error)
            reportDiagnostic(
                event = "profile_settings_snapshot_failure",
                attributes = mapOf("error" to (error.message ?: "unknown"))
            )
            ProfileSettingsSnapshot.empty()
        }
    }

    private suspend fun awaitBootstrapCompletion() {
        if (!_bootstrapComplete.value) bootstrapComplete.first { it }
    }

    suspend fun exportBundle(profileIds: Set<String>? = null): Result<String> {
        awaitBootstrapCompletion()
        val exportResult = mutationMutex.withLock {
            runCatching {
                val availableProfiles = _profiles.value
                require(availableProfiles.isNotEmpty()) { "No profiles available to export." }
                val selectedProfiles = if (profileIds.isNullOrEmpty()) {
                    availableProfiles
                } else {
                    val requested = profileIds.map(ProfileIdResolver::canonicalOrDefault).toSet()
                    availableProfiles.filter { profile -> requested.contains(profile.id) }
                }
                require(selectedProfiles.isNotEmpty()) { "Selected profiles were not found." }
                val selectedProfileIds = selectedProfiles.mapTo(linkedSetOf()) { it.id }
                val activeId = _activeProfile.value?.id?.takeIf { selectedProfileIds.contains(it) }
                val settingsSnapshot = captureSettingsSnapshot(selectedProfileIds)
                ProfileBundleCodec.serialize(
                    ProfileBundleDocument(
                        activeProfileId = activeId,
                        profiles = selectedProfiles,
                        settings = settingsSnapshot
                    )
                )
            }
        }
        exportResult
            .onSuccess { bundleJson ->
                reportDiagnostic(
                    event = "profile_bundle_export_success",
                    attributes = mapOf("bytes" to bundleJson.length.toString())
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

    suspend fun importBundle(request: ProfileBundleImportRequest): Result<ProfileBundleImportResult> {
        awaitBootstrapCompletion()
        val parsed = ProfileBundleCodec.parse(request.json).getOrElse { error ->
            reportDiagnostic(
                event = "profile_bundle_import_parse_failure",
                attributes = mapOf("error" to (error.message ?: "unknown"))
            )
            return Result.failure(error)
        }
        val importResult = importProfiles(
            ProfileImportRequest(
                profiles = parsed.profiles,
                keepCurrentActive = request.keepCurrentActive,
                nameCollisionPolicy = request.nameCollisionPolicy,
                preserveImportedPreferences = request.preserveImportedPreferences,
                preferredImportedActiveSourceId = parsed.activeProfileId
            )
        ).mapCatching { profileImportResult ->
            val settingsRestoreResult = if (
                profileImportResult.importedCount > 0 &&
                    parsed.settingsSnapshot.sections.isNotEmpty()
            ) {
                profileSettingsRestoreApplier.apply(
                    settingsSnapshot = parsed.settingsSnapshot,
                    importedProfileIdMap = profileImportResult.importedProfileIdMap
                )
            } else {
                ProfileSettingsRestoreResult()
            }
            ProfileBundleImportResult(
                profileImportResult = profileImportResult,
                settingsRestoreResult = settingsRestoreResult,
                sourceFormat = parsed.sourceFormat
            )
        }
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
                val normalizedName = request.name.trim()
                require(normalizedName.isNotBlank()) { "Profile name cannot be blank" }

                val newProfile = UserProfile(
                    name = normalizedName,
                    aircraftType = request.aircraftType,
                    aircraftModel = request.aircraftModel,
                    description = request.description
                )

                val previousProfiles = _profiles.value
                val updatedProfiles = previousProfiles + newProfile
                val currentActiveId = _activeProfile.value?.id
                val onlyDefaultBeforeCreate = previousProfiles.size == 1 &&
                    ProfileIdResolver.isCanonicalDefault(previousProfiles.first().id)
                val resolvedActive = when {
                    previousProfiles.isEmpty() -> newProfile
                    onlyDefaultBeforeCreate -> newProfile
                    else -> updatedProfiles.find { it.id == currentActiveId } ?: fallbackActiveProfile(updatedProfiles)
                }

                persistState(updatedProfiles, resolvedActive?.id)
                commitState(updatedProfiles, resolvedActive)
                newProfile
            }
        }
    }

    suspend fun importProfiles(request: ProfileImportRequest): Result<ProfileImportResult> {
        awaitBootstrapCompletion()
        return mutationMutex.withLock {
            runCatching {
                val currentProfiles = _profiles.value
                val activeBeforeId = _activeProfile.value?.id
                if (request.profiles.isEmpty()) {
                    return@runCatching ProfileImportResult(
                        requestedCount = 0,
                        importedCount = 0,
                        skippedCount = 0,
                        failures = emptyList(),
                        activeProfileBefore = activeBeforeId,
                        activeProfileAfter = activeBeforeId
                    )
                }

                val failures = mutableListOf<ProfileImportFailure>()
                val knownIds = currentProfiles.map { it.id }.toMutableSet()
                val knownNames = currentProfiles
                    .map { it.name.trim().lowercase(Locale.ROOT) }
                    .filter { it.isNotBlank() }
                    .toMutableSet()
                val workingProfiles = currentProfiles.toMutableList()
                val importedProfiles = mutableListOf<UserProfile>()
                val importedIdMap = LinkedHashMap<String, String>()

                request.profiles.forEach { incoming ->
                    val normalizedName = incoming.name.trim()
                    if (normalizedName.isBlank()) {
                        failures += ProfileImportFailure(
                            sourceName = incoming.name,
                            reason = ProfileImportFailureReason.INVALID_PROFILE,
                            detail = "Profile name cannot be blank."
                        )
                        return@forEach
                    }

                    val replaceTargetIndex = if (
                        request.nameCollisionPolicy == ProfileNameCollisionPolicy.REPLACE_EXISTING
                    ) {
                        workingProfiles.indexOfFirst { existing ->
                            existing.name.trim().equals(normalizedName, ignoreCase = true)
                        }
                    } else {
                        -1
                    }
                    val preferredIdRaw = incoming.id.trim()
                    val preferredId = ProfileIdResolver.normalizeOrNull(preferredIdRaw)
                        ?: preferredIdRaw
                    val generatedId = if (replaceTargetIndex >= 0) {
                        workingProfiles[replaceTargetIndex].id
                    } else {
                        generateUniqueId(knownIds, preferredId)
                    }
                    val resolvedName = if (replaceTargetIndex >= 0) {
                        normalizedName
                    } else {
                        resolveImportedName(
                            baseName = normalizedName,
                            knownNames = knownNames,
                            policy = request.nameCollisionPolicy
                        )
                    }

                    val imported = incoming.copy(
                        id = generatedId,
                        name = resolvedName,
                        preferences = if (request.preserveImportedPreferences) {
                            incoming.preferences
                        } else {
                            ProfilePreferences()
                        },
                        isActive = false,
                        createdAt = if (request.preserveImportedPreferences) {
                            incoming.createdAt
                        } else {
                            TimeBridge.nowWallMs()
                        },
                        lastUsed = if (request.preserveImportedPreferences) {
                            incoming.lastUsed
                        } else {
                            0L
                        }
                    )
                    if (replaceTargetIndex >= 0) {
                        workingProfiles[replaceTargetIndex] = imported
                    } else {
                        workingProfiles += imported
                    }
                    importedProfiles += imported
                    if (preferredIdRaw.isNotBlank()) {
                        importedIdMap.putIfAbsent(preferredIdRaw, generatedId)
                    }
                    if (preferredId.isNotBlank()) {
                        importedIdMap.putIfAbsent(preferredId, generatedId)
                    }
                }

                if (importedProfiles.isEmpty()) {
                    return@runCatching ProfileImportResult(
                        requestedCount = request.profiles.size,
                        importedCount = 0,
                        skippedCount = request.profiles.size,
                        failures = failures.toList(),
                        activeProfileBefore = activeBeforeId,
                        activeProfileAfter = activeBeforeId,
                        importedProfileIdMap = emptyMap()
                    )
                }

                val mergedProfiles = workingProfiles.toList()
                val preferredImportedActiveId = request.preferredImportedActiveSourceId
                    ?.let { sourceId -> importedIdMap[sourceId] }
                val resolvedActive = resolveImportActiveProfile(
                    profiles = mergedProfiles,
                    currentActiveId = activeBeforeId,
                    importedProfiles = importedProfiles,
                    keepCurrentActive = request.keepCurrentActive,
                    preferredImportedActiveId = preferredImportedActiveId
                )

                persistState(mergedProfiles, resolvedActive?.id)
                commitState(mergedProfiles, resolvedActive)

                ProfileImportResult(
                    requestedCount = request.profiles.size,
                    importedCount = importedProfiles.size,
                    skippedCount = request.profiles.size - importedProfiles.size,
                    failures = failures.toList(),
                    activeProfileBefore = activeBeforeId,
                    activeProfileAfter = resolvedActive?.id,
                    importedProfileIdMap = importedIdMap.toMap()
                )
            }
        }
    }

    suspend fun setActiveProfile(profile: UserProfile): Result<Unit> {
        awaitBootstrapCompletion()
        return mutationMutex.withLock {
            runCatching {
                val currentProfiles = _profiles.value
                val existing = currentProfiles.find { it.id == profile.id }
                val updatedProfiles = if (existing == null) {
                    (currentProfiles + profile).distinctBy { it.id }
                } else {
                    currentProfiles
                }
                val resolvedActive = updatedProfiles.find { it.id == profile.id } ?: profile

                persistState(updatedProfiles, resolvedActive.id)
                commitState(updatedProfiles, resolvedActive)
            }
        }
    }

    suspend fun updateProfile(updatedProfile: UserProfile): Result<Unit> {
        awaitBootstrapCompletion()
        return mutationMutex.withLock {
            runCatching {
                val currentProfiles = _profiles.value
                val index = currentProfiles.indexOfFirst { it.id == updatedProfile.id }
                if (index < 0) {
                    error("Profile not found")
                }
                val updatedProfiles = currentProfiles.toMutableList().apply { this[index] = updatedProfile }
                val updatedActive = if (_activeProfile.value?.id == updatedProfile.id) {
                    updatedProfile
                } else {
                    _activeProfile.value?.let { active -> updatedProfiles.find { it.id == active.id } }
                }

                persistState(updatedProfiles, updatedActive?.id)
                commitState(updatedProfiles, updatedActive)
            }
        }
    }

    suspend fun deleteProfile(profileId: String): Result<Unit> {
        awaitBootstrapCompletion()
        return mutationMutex.withLock {
            runCatching {
                val currentProfiles = _profiles.value
                if (ProfileIdResolver.isCanonicalDefault(profileId)) {
                    error("Cannot delete the default profile")
                }
                if (currentProfiles.size <= 1) {
                    error("Cannot delete the last profile")
                }
                val remaining = currentProfiles.filter { it.id != profileId }
                if (remaining.size == currentProfiles.size) {
                    error("Profile not found")
                }

                val currentActiveId = _activeProfile.value?.id
                val nextActive = if (currentActiveId == profileId) {
                    remaining.firstOrNull()
                } else {
                    remaining.find { it.id == currentActiveId } ?: remaining.firstOrNull()
                }

                profileScopedDataCleaner.clearProfileData(profileId)
                persistState(remaining, nextActive?.id)
                commitState(remaining, nextActive)
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
                val recoveredProfiles = ensureBootstrapProfile(_profiles.value).profiles
                val defaultProfile = recoveredProfiles.firstOrNull {
                    ProfileIdResolver.isCanonicalDefault(it.id)
                } ?: buildDefaultProfile()
                val normalizedProfiles = dedupeProfilesById(
                    listOf(defaultProfile) + recoveredProfiles.filterNot {
                        ProfileIdResolver.isCanonicalDefault(it.id)
                    }
                )

                persistState(normalizedProfiles, defaultProfile.id)
                commitState(normalizedProfiles, defaultProfile)
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

private data class DefaultProfileProvisioningResult(
    val profiles: List<UserProfile>,
    val insertedDefaultProfile: Boolean,
    val migratedLegacyDefaultAlias: Boolean
)
