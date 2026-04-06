package com.example.xcpro.profiles

import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow

private fun emptyProfileStorageSnapshot(): ProfileStorageSnapshot = ProfileStorageSnapshot(
    profilesJson = null,
    activeProfileId = null,
    readStatus = ProfileStorageReadStatus.OK
)

internal class RecordingProfileStorage(
    initialSnapshot: ProfileStorageSnapshot = emptyProfileStorageSnapshot()
) : ProfileStorage {
    val snapshotState = MutableStateFlow(initialSnapshot)
    var writeProfilesCalls = 0
        private set
    var writeActiveCalls = 0
        private set
    var writeStateCalls = 0
        private set

    override val snapshotFlow = snapshotState

    override suspend fun writeProfilesJson(json: String?) {
        writeProfilesCalls++
        snapshotState.value = snapshotState.value.copy(
            profilesJson = json,
            readStatus = ProfileStorageReadStatus.OK
        )
    }

    override suspend fun writeActiveProfileId(id: String?) {
        writeActiveCalls++
        snapshotState.value = snapshotState.value.copy(
            activeProfileId = id,
            readStatus = ProfileStorageReadStatus.OK
        )
    }

    override suspend fun writeState(profilesJson: String?, activeProfileId: String?) {
        writeStateCalls++
        snapshotState.value = snapshotState.value.copy(
            profilesJson = profilesJson,
            activeProfileId = activeProfileId,
            readStatus = ProfileStorageReadStatus.OK
        )
    }
}

internal class RecordingProfileDiagnosticsReporter : ProfileDiagnosticsReporter {
    val events = mutableListOf<Pair<String, Map<String, String>>>()

    override fun report(event: String, attributes: Map<String, String>) {
        events += event to attributes
    }
}

internal fun createTestProfileRepository(
    storage: ProfileStorage,
    scope: CoroutineScope,
    profileBackupSink: ProfileBackupSink = NoOpProfileBackupSink(),
    profileSettingsSnapshotProvider: ProfileSettingsSnapshotProvider = NoOpProfileSettingsSnapshotProvider(),
    profileSettingsRestoreApplier: ProfileSettingsRestoreApplier = NoOpProfileSettingsRestoreApplier(),
    profileScopedDataCleaner: ProfileScopedDataCleaner = NoOpProfileScopedDataCleaner(),
    profileDiagnosticsReporter: ProfileDiagnosticsReporter = NoOpProfileDiagnosticsReporter(),
    clock: FakeClock = FakeClock(),
    profileIdGenerator: ProfileIdGenerator = ProfileIdGenerator()
): ProfileRepository = ProfileRepository(
    storage = storage,
    profileBackupSink = profileBackupSink,
    profileSettingsSnapshotProvider = profileSettingsSnapshotProvider,
    profileSettingsRestoreApplier = profileSettingsRestoreApplier,
    profileScopedDataCleaner = profileScopedDataCleaner,
    profileDiagnosticsReporter = profileDiagnosticsReporter,
    clock = clock,
    profileIdGenerator = profileIdGenerator,
    internalScope = scope
)

internal class ProfileRepositoryTestHarness private constructor(
    val storage: RecordingProfileStorage,
    val repository: ProfileRepository,
    val clock: FakeClock,
    val profileIdGenerator: ProfileIdGenerator,
    private val diagnosticsReporter: RecordingProfileDiagnosticsReporter?
) {
    val snapshotState
        get() = storage.snapshotState

    val writeProfilesCalls: Int
        get() = storage.writeProfilesCalls

    val writeActiveCalls: Int
        get() = storage.writeActiveCalls

    val writeStateCalls: Int
        get() = storage.writeStateCalls

    val diagnosticsEvents: List<Pair<String, Map<String, String>>>
        get() = diagnosticsReporter?.events ?: emptyList()

    companion object {
        fun convenience(
            initialSnapshot: ProfileStorageSnapshot = emptyProfileStorageSnapshot(),
            clock: FakeClock = FakeClock(),
            profileIdGenerator: ProfileIdGenerator = ProfileIdGenerator()
        ): ProfileRepositoryTestHarness {
            val storage = RecordingProfileStorage(initialSnapshot)
            val repository = createTestProfileRepository(
                storage = storage,
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
                clock = clock,
                profileIdGenerator = profileIdGenerator
            )
            return ProfileRepositoryTestHarness(
                storage = storage,
                repository = repository,
                clock = clock,
                profileIdGenerator = profileIdGenerator,
                diagnosticsReporter = null
            )
        }

        fun explicitScope(
            scope: CoroutineScope,
            initialSnapshot: ProfileStorageSnapshot = emptyProfileStorageSnapshot(),
            clock: FakeClock = FakeClock(),
            profileIdGenerator: ProfileIdGenerator = ProfileIdGenerator()
        ): ProfileRepositoryTestHarness {
            val storage = RecordingProfileStorage(initialSnapshot)
            val diagnosticsReporter = RecordingProfileDiagnosticsReporter()
            val repository = createTestProfileRepository(
                storage = storage,
                scope = scope,
                profileDiagnosticsReporter = diagnosticsReporter,
                clock = clock,
                profileIdGenerator = profileIdGenerator
            )
            return ProfileRepositoryTestHarness(
                storage = storage,
                repository = repository,
                clock = clock,
                profileIdGenerator = profileIdGenerator,
                diagnosticsReporter = diagnosticsReporter
            )
        }
    }
}

internal fun createProfileRepositoryTestHarness(
    initialSnapshot: ProfileStorageSnapshot = emptyProfileStorageSnapshot(),
    clock: FakeClock = FakeClock(),
    profileIdGenerator: ProfileIdGenerator = ProfileIdGenerator()
): ProfileRepositoryTestHarness = ProfileRepositoryTestHarness.convenience(
    initialSnapshot = initialSnapshot,
    clock = clock,
    profileIdGenerator = profileIdGenerator
)

internal fun createScopedProfileRepositoryTestHarness(
    scope: CoroutineScope,
    initialSnapshot: ProfileStorageSnapshot = emptyProfileStorageSnapshot(),
    clock: FakeClock = FakeClock(),
    profileIdGenerator: ProfileIdGenerator = ProfileIdGenerator()
): ProfileRepositoryTestHarness = ProfileRepositoryTestHarness.explicitScope(
    scope = scope,
    initialSnapshot = initialSnapshot,
    clock = clock,
    profileIdGenerator = profileIdGenerator
)

internal suspend fun createReadyScopedProfileRepositoryTestHarness(
    scope: CoroutineScope,
    initialSnapshot: ProfileStorageSnapshot = emptyProfileStorageSnapshot(),
    clock: FakeClock = FakeClock(),
    profileIdGenerator: ProfileIdGenerator = ProfileIdGenerator()
): ProfileRepositoryTestHarness = createScopedProfileRepositoryTestHarness(
    scope = scope,
    initialSnapshot = initialSnapshot,
    clock = clock,
    profileIdGenerator = profileIdGenerator
).also { harness ->
    harness.repository.bootstrapComplete.first { it }
}

internal suspend fun ProfileRepositoryTestHarness.completeFirstLaunch(
    aircraftType: AircraftType = AircraftType.PARAGLIDER
): UserProfile = repository.completeFirstLaunch(aircraftType).getOrThrow()
