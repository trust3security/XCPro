package com.trust3.xcpro.profiles

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.trust3.xcpro.core.time.Clock
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal val MANAGED_PROFILE_BACKUP_RELATIVE_PATH =
    "${Environment.DIRECTORY_DOWNLOADS}/XCPro/profiles/"

interface ProfileBackupSink {
    suspend fun syncSnapshot(
        profiles: List<UserProfile>,
        activeProfileId: String?,
        settingsSnapshot: ProfileSettingsSnapshot,
        sequenceNumber: Long
    )
}

class NoOpProfileBackupSink : ProfileBackupSink {
    override suspend fun syncSnapshot(
        profiles: List<UserProfile>,
        activeProfileId: String?,
        settingsSnapshot: ProfileSettingsSnapshot,
        sequenceNumber: Long
    ) = Unit
}

class DownloadsProfileBackupSink @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val clock: Clock
) : ProfileBackupSink {

    private companion object {
        private const val PROFILE_FILE_SUFFIX = ".json"
        private val NON_FILE_CHAR_REGEX = Regex("[^a-z0-9._-]")
        private val NON_NAMESPACE_CHAR_REGEX = Regex("[^a-z0-9._-]")
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val writeMutex = Mutex()
    private val namespacePrefix = appContext.packageName
        .trim()
        .lowercase(Locale.ROOT)
        .replace(NON_NAMESPACE_CHAR_REGEX, "_")
        .ifBlank { "xcpro" }
    private val indexFileName = "${namespacePrefix}_profiles_index.json"
    private val settingsFileName = "${namespacePrefix}_profile_settings.json"
    private val bundleLatestFileName = "${namespacePrefix}_bundle_latest.json"
    private val profileFilePrefix = "${namespacePrefix}_profile_"
    private var lastAppliedSequenceNumber: Long = 0L

    override suspend fun syncSnapshot(
        profiles: List<UserProfile>,
        activeProfileId: String?,
        settingsSnapshot: ProfileSettingsSnapshot,
        sequenceNumber: Long
    ) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                if (sequenceNumber <= lastAppliedSequenceNumber) {
                    return@withLock
                }
                val resolver = appContext.contentResolver
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val generatedAtWallMs = clock.nowWallMs()

                val payload = buildManagedBackupPayload(
                    gson = gson,
                    generatedAtWallMs = generatedAtWallMs,
                    sequenceNumber = sequenceNumber,
                    indexFileName = indexFileName,
                    settingsFileName = settingsFileName,
                    bundleFileName = bundleLatestFileName,
                    profiles = profiles,
                    activeProfileId = activeProfileId,
                    settingsSnapshot = settingsSnapshot,
                    profileFileNameForId = ::profileFileNameForId
                )

                payload.profileFiles.forEach { entry ->
                    writeJsonFile(
                        resolver = resolver,
                        collection = collection,
                        fileName = entry.fileName,
                        payloadJson = entry.payloadJson
                    )
                }

                writeJsonFile(
                    resolver = resolver,
                    collection = collection,
                    fileName = payload.settingsFileName,
                    payloadJson = payload.settingsJson
                )

                writeJsonFile(
                    resolver = resolver,
                    collection = collection,
                    fileName = payload.bundleFileName,
                    payloadJson = payload.bundleJson
                )

                writeJsonFile(
                    resolver = resolver,
                    collection = collection,
                    fileName = payload.indexFileName,
                    payloadJson = payload.indexJson
                )

                cleanupStaleManagedFiles(
                    resolver = resolver,
                    collection = collection,
                    expectedManagedFiles = payload.expectedManagedFiles()
                )
                lastAppliedSequenceNumber = sequenceNumber
            }
        }
    }

    private fun writeJsonFile(
        resolver: ContentResolver,
        collection: Uri,
        fileName: String,
        payloadJson: String
    ) {
        val stagedFileName = "$fileName.tmp.${clock.nowWallMs()}"

        val pendingValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, stagedFileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, MANAGED_PROFILE_BACKUP_RELATIVE_PATH)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val stagedUri = resolver.insert(collection, pendingValues)
            ?: error("Unable to create profile backup file: $fileName")

        val writeResult = runCatching {
            resolver.openOutputStream(stagedUri)?.use { output ->
                output.write(payloadJson.toByteArray(StandardCharsets.UTF_8))
            } ?: error("Unable to open profile backup output stream: $fileName")
            resolver.update(
                stagedUri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )

            val existingUri = findExistingFileUri(
                resolver = resolver,
                collection = collection,
                fileName = fileName
            )
            var finalized = resolver.update(
                stagedUri,
                ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, fileName) },
                null,
                null
            )
            if (finalized <= 0 && existingUri != null) {
                finalized = finalizeWithSafeReplacement(
                    resolver = resolver,
                    existingUri = existingUri,
                    stagedUri = stagedUri,
                    fileName = fileName
                )
            }
            check(finalized > 0) { "Failed to finalize staged profile backup file: $fileName" }

            if (existingUri != null) {
                val removed = resolver.delete(existingUri, null, null)
                check(removed > 0) { "Failed to remove previous profile backup file: $fileName" }
            }
        }
        if (writeResult.isFailure) {
            runCatching { resolver.delete(stagedUri, null, null) }
            throw writeResult.exceptionOrNull()
                ?: error("Failed to write profile backup file: $fileName")
        }
    }

    private fun finalizeWithSafeReplacement(
        resolver: ContentResolver,
        existingUri: Uri,
        stagedUri: Uri,
        fileName: String
    ): Int {
        val displacedName = "$fileName.previous.${clock.nowWallMs()}"
        val displaced = resolver.update(
            existingUri,
            ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, displacedName) },
            null,
            null
        )
        check(displaced > 0) { "Failed to stage previous profile backup file: $fileName" }

        val finalized = resolver.update(
            stagedUri,
            ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, fileName) },
            null,
            null
        )
        if (finalized > 0) {
            return finalized
        }

        runCatching {
            resolver.update(
                existingUri,
                ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, fileName) },
                null,
                null
            )
        }
        error("Failed to finalize staged profile backup file: $fileName")
    }

    private fun cleanupStaleManagedFiles(
        resolver: ContentResolver,
        collection: Uri,
        expectedManagedFiles: Set<String>
    ) {
        resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
            "${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(MANAGED_PROFILE_BACKUP_RELATIVE_PATH),
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameColumn) ?: continue
                if (!isManagedBackupFile(displayName)) continue
                if (expectedManagedFiles.contains(displayName)) continue
                val rowId = cursor.getLong(idColumn)
                val rowUri = ContentUris.withAppendedId(collection, rowId)
                resolver.delete(rowUri, null, null)
            }
        }
    }

    private fun findExistingFileUri(
        resolver: ContentResolver,
        collection: Uri,
        fileName: String
    ): Uri? {
        resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(fileName, MANAGED_PROFILE_BACKUP_RELATIVE_PATH),
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val rowId = cursor.getLong(idColumn)
            return ContentUris.withAppendedId(collection, rowId)
        }
        return null
    }

    private fun isManagedBackupFile(fileName: String): Boolean {
        return fileName == indexFileName ||
            fileName == settingsFileName ||
            fileName == bundleLatestFileName ||
            (fileName.startsWith(profileFilePrefix) && fileName.endsWith(PROFILE_FILE_SUFFIX))
    }

    private fun profileFileNameForId(profileId: String): String {
        val normalized = profileId.trim().lowercase(Locale.ROOT)
        val safeId = normalized
            .replace(NON_FILE_CHAR_REGEX, "_")
            .trim('_')
            .ifBlank { "profile" }
            .take(48)
        val hashSuffix = profileId
            .toByteArray(StandardCharsets.UTF_8)
            .fold(1125899906842597L) { acc, value ->
                (31L * acc) + (value.toInt() and 0xff).toLong()
            }
            .toULong()
            .toString(16)
        return "$profileFilePrefix${safeId}_$hashSuffix$PROFILE_FILE_SUFFIX"
    }
}

internal data class ManagedProfileBackupFile(
    val profileId: String,
    val fileName: String,
    val payloadJson: String
)

internal data class ManagedBackupPayload(
    val profileFiles: List<ManagedProfileBackupFile>,
    val settingsFileName: String,
    val settingsJson: String,
    val bundleFileName: String,
    val bundleJson: String,
    val indexFileName: String,
    val indexJson: String
) {
    fun expectedManagedFiles(): Set<String> {
        return profileFiles.mapTo(mutableSetOf()) { it.fileName }
            .apply {
                add(indexFileName)
                add(settingsFileName)
                add(bundleFileName)
            }
    }
}

internal fun buildManagedBackupPayload(
    gson: Gson,
    generatedAtWallMs: Long,
    sequenceNumber: Long,
    indexFileName: String,
    settingsFileName: String,
    bundleFileName: String,
    profiles: List<UserProfile>,
    activeProfileId: String?,
    settingsSnapshot: ProfileSettingsSnapshot,
    profileFileNameForId: (String) -> String
): ManagedBackupPayload {
    val profileFiles = profiles.map { profile ->
        val fileName = profileFileNameForId(profile.id)
        ManagedProfileBackupFile(
            profileId = profile.id,
            fileName = fileName,
            payloadJson = gson.toJson(
                ProfileBackupDocument(
                    generatedAtWallMs = generatedAtWallMs,
                    sequenceNumber = sequenceNumber,
                    isActive = profile.id == activeProfileId,
                    profile = profile
                )
            )
        )
    }

    val settingsJson = gson.toJson(
        ProfileSettingsBackupDocument(
            generatedAtWallMs = generatedAtWallMs,
            sequenceNumber = sequenceNumber,
            settings = settingsSnapshot
        )
    )
    val bundleJson = ProfileBundleCodec.serialize(
        ProfileBundleDocument(
            exportedAtWallMs = generatedAtWallMs,
            activeProfileId = activeProfileId,
            profiles = profiles,
            settings = settingsSnapshot
        )
    )
    val indexJson = gson.toJson(
        ProfileBackupIndex(
            generatedAtWallMs = generatedAtWallMs,
            sequenceNumber = sequenceNumber,
            activeProfileId = activeProfileId,
            settingsFileName = settingsFileName,
            bundleFileName = bundleFileName,
            profileFiles = profileFiles.map { entry ->
                ProfileBackupIndexEntry(
                    profileId = entry.profileId,
                    fileName = entry.fileName
                )
            }
        )
    )
    return ManagedBackupPayload(
        profileFiles = profileFiles,
        settingsFileName = settingsFileName,
        settingsJson = settingsJson,
        bundleFileName = bundleFileName,
        bundleJson = bundleJson,
        indexFileName = indexFileName,
        indexJson = indexJson
    )
}

private data class ProfileBackupIndex(
    val version: String = "1.0",
    val generatedAtWallMs: Long,
    val sequenceNumber: Long,
    val activeProfileId: String?,
    val settingsFileName: String?,
    val bundleFileName: String?,
    val profileFiles: List<ProfileBackupIndexEntry>
)

private data class ProfileBackupIndexEntry(
    val profileId: String,
    val fileName: String
)

private data class ProfileBackupDocument(
    val version: String = "1.0",
    val generatedAtWallMs: Long,
    val sequenceNumber: Long,
    val isActive: Boolean,
    val profile: UserProfile
)

private data class ProfileSettingsBackupDocument(
    val version: String = "1.0",
    val generatedAtWallMs: Long,
    val sequenceNumber: Long,
    val settings: ProfileSettingsSnapshot
)
