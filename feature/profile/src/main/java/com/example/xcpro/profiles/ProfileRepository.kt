package com.example.xcpro.profiles

import android.util.Log
import com.google.gson.GsonBuilder
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
            val type = object : com.google.gson.reflect.TypeToken<List<UserProfile?>>() {}.type
            gson.fromJson<List<UserProfile?>>(json, type) ?: emptyList()
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
