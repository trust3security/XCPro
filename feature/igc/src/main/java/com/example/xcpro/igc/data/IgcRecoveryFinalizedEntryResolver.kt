package com.example.xcpro.igc.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.example.xcpro.igc.domain.IgcRecoveryMetadata
import com.example.xcpro.igc.domain.IgcSessionFileIdentityCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject

sealed interface IgcRecoveryFinalizedEntryMatch {
    data object None : IgcRecoveryFinalizedEntryMatch
    data class Single(val entry: IgcLogEntry) : IgcRecoveryFinalizedEntryMatch
    data class Duplicate(val entries: List<IgcLogEntry>) : IgcRecoveryFinalizedEntryMatch
}

class IgcRecoveryFinalizedEntryResolver @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val recoveryDownloadsLookup: IgcRecoveryDownloadsLookup
) {

    fun findExistingFinalizedMatch(metadata: IgcRecoveryMetadata): IgcRecoveryFinalizedEntryMatch {
        val utcDate = resolveRecoveryUtcDate(metadata) ?: return IgcRecoveryFinalizedEntryMatch.None
        val expectedPrefix = buildExpectedPrefix(
            utcDate = utcDate,
            manufacturerId = metadata.manufacturerId,
            sessionSerial = metadata.sessionSerial
        )
        val matches = recoveryDownloadsLookup.findFinalizedEntriesByPrefix(
            expectedPrefix = expectedPrefix,
            utcDate = utcDate
        )
        return when (matches.size) {
            0 -> IgcRecoveryFinalizedEntryMatch.None
            1 -> IgcRecoveryFinalizedEntryMatch.Single(matches.first())
            else -> IgcRecoveryFinalizedEntryMatch.Duplicate(matches)
        }
    }

    fun cleanupPendingRows(metadata: IgcRecoveryMetadata?) {
        val safeMetadata = metadata ?: return
        val utcDate = resolveRecoveryUtcDate(safeMetadata) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val resolver = appContext.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = buildString {
            append("${MediaStore.Downloads.DISPLAY_NAME} LIKE ?")
            append(" AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?")
            append(" AND ${MediaStore.Downloads.IS_PENDING} = 1")
        }
        val selectionArgs = arrayOf(
            "${buildExpectedPrefix(utcDate, safeMetadata.manufacturerId, safeMetadata.sessionSerial)}%",
            "${IgcDownloadsStoragePaths.DOWNLOAD_RELATIVE_PATH}%"
        )

        runCatching {
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                while (cursor.moveToNext()) {
                    val rowUri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                    resolver.delete(rowUri, null, null)
                }
            }
        }
    }

    private fun buildExpectedPrefix(
        utcDate: LocalDate,
        manufacturerId: String,
        sessionSerial: String
    ): String {
        return buildString {
            append(
                IgcSessionFileIdentityCodec.buildSessionPrefix(
                    utcDate = utcDate,
                    manufacturerId = manufacturerId,
                    sessionSerial = sessionSerial
                )
            )
            append('-')
        }
    }

    private fun resolveRecoveryUtcDate(metadata: IgcRecoveryMetadata): LocalDate? {
        val wallTime = metadata.firstValidFixWallTimeMs ?: metadata.sessionStartWallTimeMs
        if (wallTime <= 0L) return null
        return IgcSessionFileIdentityCodec.utcDateFromWallTime(wallTime)
    }
}
