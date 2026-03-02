package com.example.xcpro.profiles

import android.util.Log
import com.example.xcpro.core.time.TimeBridge
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val storage: ProfileStorage
) {

    private val gson = GsonBuilder().create()

    private companion object {
        private const val TAG = "ProfileRepository"
    }

    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
            }
        }
    }

    private suspend fun handleStorageSnapshot(snapshot: ProfileStorageSnapshot) {
        when (snapshot.readStatus) {
            ProfileStorageReadStatus.OK -> hydrateFromSnapshot(snapshot)
            ProfileStorageReadStatus.IO_ERROR -> markReadError("Failed to read stored profiles (I/O).")
            ProfileStorageReadStatus.UNKNOWN_ERROR -> markReadError("Failed to read stored profiles.")
        }
    }

    private suspend fun hydrateFromSnapshot(snapshot: ProfileStorageSnapshot) {
        val parseResult = parseProfiles(snapshot.profilesJson, fallback = lastKnownGoodProfiles)
        val loadedProfiles = parseResult.profiles
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
        if (!parseResult.parseFailed && snapshot.activeProfileId != resolvedActiveId) {
            runCatching {
                persistActiveProfileId(resolvedActiveId)
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                logError("Failed to repair active profile id during hydration", error)
                message = mergeMessages(message, "Failed to persist active profile selection.")
            }
        }

        lastKnownGoodProfiles = loadedProfiles
        lastKnownGoodActiveProfileId = resolvedActiveId
        _bootstrapError.value = message
        _bootstrapComplete.value = true
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

    private fun resolveActiveProfile(id: String?, profiles: List<UserProfile>): UserProfile? {
        return when {
            profiles.isEmpty() -> null
            id.isNullOrBlank() -> profiles.firstOrNull()
            else -> profiles.find { it.id == id } ?: profiles.firstOrNull()
        }
    }

    private fun mergeMessages(primary: String?, secondary: String): String {
        if (primary.isNullOrBlank()) {
            return secondary
        }
        return "$primary $secondary"
    }

    private fun logError(message: String, error: Throwable) {
        runCatching { Log.e(TAG, message, error) }
    }

    private suspend fun persistState(profiles: List<UserProfile>, activeProfileId: String?) {
        storage.writeState(
            profilesJson = gson.toJson(profiles),
            activeProfileId = activeProfileId
        )
    }

    private suspend fun persistActiveProfileId(id: String?) {
        storage.writeActiveProfileId(id)
    }

    private fun commitState(profiles: List<UserProfile>, activeProfile: UserProfile?) {
        _profiles.value = profiles
        _activeProfile.value = activeProfile
        lastKnownGoodProfiles = profiles
        lastKnownGoodActiveProfileId = activeProfile?.id
        _bootstrapError.value = null
        _bootstrapComplete.value = true
    }

    suspend fun createProfile(request: ProfileCreationRequest): Result<UserProfile> = mutationMutex.withLock {
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
            val resolvedActive = when {
                previousProfiles.isEmpty() -> newProfile
                else -> updatedProfiles.find { it.id == currentActiveId } ?: updatedProfiles.firstOrNull()
            }

            persistState(updatedProfiles, resolvedActive?.id)
            commitState(updatedProfiles, resolvedActive)
            newProfile
        }
    }

    suspend fun importProfiles(request: ProfileImportRequest): Result<ProfileImportResult> = mutationMutex.withLock {
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
            val importedProfiles = mutableListOf<UserProfile>()

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

                val preferredId = incoming.id.trim()
                val generatedId = generateUniqueId(knownIds, preferredId)
                val resolvedName = resolveImportedName(
                    baseName = normalizedName,
                    knownNames = knownNames,
                    policy = request.nameCollisionPolicy
                )

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
                importedProfiles += imported
            }

            if (importedProfiles.isEmpty()) {
                return@runCatching ProfileImportResult(
                    requestedCount = request.profiles.size,
                    importedCount = 0,
                    skippedCount = request.profiles.size,
                    failures = failures.toList(),
                    activeProfileBefore = activeBeforeId,
                    activeProfileAfter = activeBeforeId
                )
            }

            val mergedProfiles = currentProfiles + importedProfiles
            val resolvedActive = resolveImportActiveProfile(
                profiles = mergedProfiles,
                currentActiveId = activeBeforeId,
                importedProfiles = importedProfiles,
                keepCurrentActive = request.keepCurrentActive
            )

            persistState(mergedProfiles, resolvedActive?.id)
            commitState(mergedProfiles, resolvedActive)

            ProfileImportResult(
                requestedCount = request.profiles.size,
                importedCount = importedProfiles.size,
                skippedCount = request.profiles.size - importedProfiles.size,
                failures = failures.toList(),
                activeProfileBefore = activeBeforeId,
                activeProfileAfter = resolvedActive?.id
            )
        }
    }

    suspend fun setActiveProfile(profile: UserProfile): Result<Unit> = mutationMutex.withLock {
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

    suspend fun updateProfile(updatedProfile: UserProfile): Result<Unit> = mutationMutex.withLock {
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

    suspend fun deleteProfile(profileId: String): Result<Unit> = mutationMutex.withLock {
        runCatching {
            val currentProfiles = _profiles.value
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

            persistState(remaining, nextActive?.id)
            commitState(remaining, nextActive)
        }
    }

    fun hasProfiles(): Boolean = _profiles.value.isNotEmpty()

    fun hasActiveProfile(): Boolean = _activeProfile.value != null

    private fun generateUniqueId(knownIds: MutableSet<String>, preferredId: String): String {
        if (preferredId.isNotBlank() && knownIds.add(preferredId)) {
            return preferredId
        }
        var generatedId: String
        do {
            generatedId = UUID.randomUUID().toString()
        } while (!knownIds.add(generatedId))
        return generatedId
    }

    private fun resolveImportedName(
        baseName: String,
        knownNames: MutableSet<String>,
        policy: ProfileNameCollisionPolicy
    ): String {
        val normalizedBase = baseName.trim()
        val baseKey = normalizedBase.lowercase(Locale.ROOT)
        if (knownNames.add(baseKey)) {
            return normalizedBase
        }

        return when (policy) {
            ProfileNameCollisionPolicy.KEEP_BOTH_SUFFIX ->
                resolveImportedNameWithSuffix(normalizedBase, knownNames)
        }
    }

    private fun resolveImportedNameWithSuffix(
        normalizedBase: String,
        knownNames: MutableSet<String>
    ): String {
        var suffix = 1
        while (true) {
            val candidate = if (suffix == 1) {
                "$normalizedBase (Imported)"
            } else {
                "$normalizedBase (Imported $suffix)"
            }
            val candidateKey = candidate.lowercase(Locale.ROOT)
            if (knownNames.add(candidateKey)) {
                return candidate
            }
            suffix++
        }
    }

    private fun resolveImportActiveProfile(
        profiles: List<UserProfile>,
        currentActiveId: String?,
        importedProfiles: List<UserProfile>,
        keepCurrentActive: Boolean
    ): UserProfile? {
        if (profiles.isEmpty()) {
            return null
        }

        if (keepCurrentActive && !currentActiveId.isNullOrBlank()) {
            return profiles.find { it.id == currentActiveId } ?: profiles.firstOrNull()
        }

        if (!keepCurrentActive) {
            val newestImportedId = importedProfiles.lastOrNull()?.id
            if (!newestImportedId.isNullOrBlank()) {
                return profiles.find { it.id == newestImportedId } ?: profiles.firstOrNull()
            }
        }

        return if (!currentActiveId.isNullOrBlank()) {
            profiles.find { it.id == currentActiveId } ?: profiles.firstOrNull()
        } else {
            profiles.firstOrNull()
        }
    }

    private data class ParseProfilesResult(
        val profiles: List<UserProfile>,
        val bootstrapMessage: String?,
        val parseFailed: Boolean
    )

    private data class SanitizedProfilesResult(
        val profiles: List<UserProfile>,
        val droppedInvalidEntries: Boolean
    )
}
