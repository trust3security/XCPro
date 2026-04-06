package com.example.xcpro.profiles

import com.example.xcpro.core.time.FakeClock
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

internal class RecordingProfileBundleSnapshotProvider : ProfileSettingsSnapshotProvider {
    val requestedProfileIds = mutableListOf<Set<String>>()
    val requestedSectionIds = mutableListOf<Set<String>>()
    val requestedSectionOrders = mutableListOf<List<String>>()

    override suspend fun buildSnapshot(
        profileIds: Set<String>,
        sectionIds: Set<String>
    ): ProfileSettingsSnapshot {
        requestedProfileIds += profileIds
        requestedSectionIds += sectionIds
        val sectionOrder = sectionIds.toList()
        requestedSectionOrders += sectionOrder
        val sections = linkedMapOf<String, JsonPrimitive>()
        for (sectionId in sectionOrder) {
            sections[sectionId] = JsonPrimitive(sectionId)
        }
        return ProfileSettingsSnapshot(sections = sections)
    }
}

internal class RecordingProfileBundleRestoreApplier : ProfileSettingsRestoreApplier {
    data class Call(
        val settingsSnapshot: ProfileSettingsSnapshot,
        val importedProfileIdMap: Map<String, String>
    )

    val calls = mutableListOf<Call>()
    val failSections = mutableSetOf<String>()

    override suspend fun apply(
        settingsSnapshot: ProfileSettingsSnapshot,
        importedProfileIdMap: Map<String, String>
    ): ProfileSettingsRestoreResult {
        calls += Call(settingsSnapshot, importedProfileIdMap)
        val failed = settingsSnapshot.sections.keys
            .filter { sectionId -> failSections.contains(sectionId) }
            .associateWith { "forced failure" }
        return ProfileSettingsRestoreResult(
            appliedSections = settingsSnapshot.sections.keys - failed.keys,
            failedSections = failed
        )
    }
}

internal class ProfileRepositoryBundleHarness(scope: CoroutineScope) {
    val snapshotState = MutableStateFlow(
        ProfileStorageSnapshot(
            profilesJson = null,
            activeProfileId = null,
            readStatus = ProfileStorageReadStatus.OK
        )
    )
    val storage = object : ProfileStorage {
        override val snapshotFlow = snapshotState

        override suspend fun writeProfilesJson(json: String?) {
            snapshotState.value = snapshotState.value.copy(
                profilesJson = json,
                readStatus = ProfileStorageReadStatus.OK
            )
        }

        override suspend fun writeActiveProfileId(id: String?) {
            snapshotState.value = snapshotState.value.copy(
                activeProfileId = id,
                readStatus = ProfileStorageReadStatus.OK
            )
        }

        override suspend fun writeState(profilesJson: String?, activeProfileId: String?) {
            snapshotState.value = snapshotState.value.copy(
                profilesJson = profilesJson,
                activeProfileId = activeProfileId,
                readStatus = ProfileStorageReadStatus.OK
            )
        }
    }
    val snapshotProvider = RecordingProfileBundleSnapshotProvider()
    val restoreApplier = RecordingProfileBundleRestoreApplier()
    val clock = FakeClock(wallMs = 1_773_878_400_000L)
    val diagnosticsEvents = mutableListOf<Pair<String, Map<String, String>>>()
    private val diagnosticsReporter = object : ProfileDiagnosticsReporter {
        override fun report(event: String, attributes: Map<String, String>) {
            diagnosticsEvents += event to attributes
        }
    }
    val repository = createTestProfileRepository(
        storage = storage,
        scope = scope,
        profileBackupSink = NoOpProfileBackupSink(),
        profileSettingsSnapshotProvider = snapshotProvider,
        profileSettingsRestoreApplier = restoreApplier,
        profileDiagnosticsReporter = diagnosticsReporter,
        clock = clock
    )
}
