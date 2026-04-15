package com.example.xcpro.profiles

import com.example.xcpro.core.time.Clock
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException

internal data class ProfileHydrationOutcome(
    val profiles: List<UserProfile>,
    val activeProfile: UserProfile?,
    val bootstrapError: String?,
    val parseFailed: Boolean,
    val suppressNextHydrationBackupSync: Boolean
)

internal class ProfileRepositoryHydrationCoordinator(
    private val clock: Clock,
    private val gson: Gson,
    private val onError: (String, Throwable) -> Unit,
    private val reportDiagnostic: (String, Map<String, String>) -> Unit,
    private val persistState: suspend (List<UserProfile>, String?) -> Unit,
    private val persistActiveProfileId: suspend (String?) -> Unit
) {
    suspend fun hydrateFromSnapshot(
        snapshot: ProfileStorageSnapshot,
        lastKnownGoodProfiles: List<UserProfile>,
        lastKnownGoodActiveProfileId: String?,
        suppressNextHydrationBackupSync: Boolean
    ): ProfileHydrationOutcome {
        val parseResult = parseProfiles(
            json = snapshot.profilesJson,
            fallback = lastKnownGoodProfiles,
            gson = gson,
            onParseError = onError
        )
        val defaultProvisioning = if (parseResult.parseFailed) {
            DefaultProfileProvisioningResult(
                profiles = parseResult.profiles,
                insertedDefaultProfile = false,
                migratedLegacyDefaultAlias = false,
                normalizedLegacyAircraftTypes = false
            )
        } else {
            ensureBootstrapProfile(parseResult.profiles, clock)
        }
        val loadedProfiles = defaultProvisioning.profiles

        val activeIdForResolution = if (parseResult.parseFailed) {
            lastKnownGoodActiveProfileId
        } else {
            snapshot.activeProfileId
        }
        val resolvedActive = resolveActiveProfile(activeIdForResolution, loadedProfiles)
        val resolvedActiveId = resolvedActive?.id

        var message = parseResult.bootstrapMessage
        if (parseResult.parseFailed) {
            reportDiagnostic(
                "profile_bootstrap_parse_failed",
                mapOf("fallbackProfileCount" to loadedProfiles.size.toString())
            )
        }

        val shouldRepairSnapshot = if (parseResult.parseFailed) {
            defaultProvisioning.insertedDefaultProfile ||
                defaultProvisioning.migratedLegacyDefaultAlias ||
                defaultProvisioning.normalizedLegacyAircraftTypes
        } else {
            defaultProvisioning.insertedDefaultProfile ||
                defaultProvisioning.migratedLegacyDefaultAlias ||
                defaultProvisioning.normalizedLegacyAircraftTypes ||
                parseResult.normalizedLegacyAircraftTypes ||
                snapshot.activeProfileId != resolvedActiveId
        }

        var suppressBackupSync = suppressNextHydrationBackupSync
        if (parseResult.parseFailed && defaultProvisioning.insertedDefaultProfile) {
            message = mergeMessages(message, "Recovered with a default profile.")
        }
        if (shouldRepairSnapshot) {
            runCatching {
                if (parseResult.parseFailed) {
                    suppressBackupSync = true
                }
                if (
                    defaultProvisioning.insertedDefaultProfile ||
                    defaultProvisioning.migratedLegacyDefaultAlias ||
                    defaultProvisioning.normalizedLegacyAircraftTypes ||
                    parseResult.normalizedLegacyAircraftTypes
                ) {
                    persistState(loadedProfiles, resolvedActiveId)
                } else {
                    persistActiveProfileId(resolvedActiveId)
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                if (parseResult.parseFailed) {
                    suppressBackupSync = false
                }
                onError("Failed to repair profile bootstrap snapshot", error)
                reportDiagnostic(
                    "profile_bootstrap_repair_failure",
                    mapOf("error" to (error.message ?: "unknown"))
                )
                message = mergeMessages(message, "Failed to persist active profile selection.")
            }
        }

        if (!parseResult.parseFailed && suppressBackupSync) {
            suppressBackupSync = false
        }

        return ProfileHydrationOutcome(
            profiles = loadedProfiles,
            activeProfile = resolvedActive,
            bootstrapError = message,
            parseFailed = parseResult.parseFailed,
            suppressNextHydrationBackupSync = suppressBackupSync
        )
    }
}
