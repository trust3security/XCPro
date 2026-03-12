package com.example.xcpro.profiles

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ProfileRepositoryBackupSyncCoordinator(
    private val profileBackupSink: ProfileBackupSink,
    private val profileSettingsSnapshotProvider: ProfileSettingsSnapshotProvider,
    private val internalScope: CoroutineScope,
    private val onError: (String, Throwable) -> Unit,
    private val reportDiagnostic: (String, Map<String, String>) -> Unit
) {
    private val backupSyncSequence = AtomicLong(0L)

    fun scheduleProfileBackupSync(profiles: List<UserProfile>, activeProfileId: String?) {
        val snapshotProfiles = profiles.toList()
        val snapshotProfileIds = snapshotProfiles.mapTo(linkedSetOf()) { it.id }
        val sequenceNumber = backupSyncSequence.incrementAndGet()
        internalScope.launch {
            runCatching {
                val settingsSnapshot = captureSettingsSnapshot(
                    profileIds = snapshotProfileIds,
                    sectionIds = ProfileSettingsSectionSets.CAPTURED_SECTION_IDS
                )
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
                onError("Failed to sync profile backup folder", error)
                reportDiagnostic(
                    "profile_backup_sync_failure",
                    mapOf(
                        "sequenceNumber" to sequenceNumber.toString(),
                        "error" to (error.message ?: "unknown")
                    )
                )
            }
        }
    }

    suspend fun captureSettingsSnapshot(
        profileIds: Set<String>,
        sectionIds: Set<String> = ProfileSettingsSectionSets.CAPTURED_SECTION_IDS
    ): ProfileSettingsSnapshot {
        return runCatching {
            profileSettingsSnapshotProvider.buildSnapshot(profileIds, sectionIds)
        }.getOrElse { error ->
            if (error is CancellationException) {
                throw error
            }
            onError("Failed to capture profile settings snapshot", error)
            reportDiagnostic(
                "profile_settings_snapshot_failure",
                mapOf("error" to (error.message ?: "unknown"))
            )
            ProfileSettingsSnapshot.empty()
        }
    }
}
