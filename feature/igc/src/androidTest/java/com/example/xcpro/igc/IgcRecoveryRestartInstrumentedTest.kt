package com.example.xcpro.igc

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.xcpro.igc.data.IgcDownloadsStoragePaths
import com.example.xcpro.igc.data.IgcFlightLogRepository
import com.example.xcpro.igc.data.IgcExportValidationAdapter
import com.example.xcpro.igc.data.IgcFlightLogPublishTransport
import com.example.xcpro.igc.data.IgcRecoveryFinalizedEntryResolver
import com.example.xcpro.igc.data.IgcRecoveryStagingStore
import com.example.xcpro.igc.data.IgcRecoveryMetadataStore
import com.example.xcpro.igc.data.MediaStoreIgcRecoveryDownloadsLookup
import com.example.xcpro.igc.data.MediaStoreIgcDownloadsRepository
import com.example.xcpro.igc.data.MediaStoreIgcFlightLogRepository
import com.example.xcpro.igc.domain.IgcFileNamingPolicy
import com.example.xcpro.igc.domain.IgcGRecordSigner
import com.example.xcpro.igc.domain.IgcRecoveryErrorCode
import com.example.xcpro.igc.domain.IgcRecoveryMetadata
import com.example.xcpro.igc.domain.IgcRecoveryResult
import com.example.xcpro.igc.domain.IgcSecuritySignatureProfile
import com.example.xcpro.igc.domain.StrictIgcLintValidator
import com.example.xcpro.igc.usecase.IgcLintMessageMapper
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class IgcRecoveryRestartInstrumentedTest {

    @Test
    fun recoveryRestart_deletesPendingRow_andPublishesSingleFinalFile_onRealMediaStore() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionId = 710001L
        val fileName = "2026-03-09-XCP-710001-01.IGC"
        val stageFile = stagedFile(context, sessionId)
        val metadataStore = InMemoryRecoveryMetadataStore().apply {
            saveMetadata(
                sessionId = sessionId,
                metadata = IgcRecoveryMetadata(
                    manufacturerId = "XCP",
                    sessionSerial = "710001",
                    sessionStartWallTimeMs = 1_773_014_400_000L,
                    firstValidFixWallTimeMs = 1_773_057_600_000L,
                    signatureProfile = IgcSecuritySignatureProfile.NONE
                )
            )
        }
        val downloadsRepository = MediaStoreIgcDownloadsRepository(context)
        val recoveryDownloadsLookup = MediaStoreIgcRecoveryDownloadsLookup(context)
        val repository: IgcFlightLogRepository = MediaStoreIgcFlightLogRepository(
            downloadsRepository = downloadsRepository,
            recoveryMetadataStore = metadataStore,
            namingPolicy = IgcFileNamingPolicy(),
            exportValidationAdapter = newValidationAdapter(),
            gRecordSigner = IgcGRecordSigner(),
            stagingStore = IgcRecoveryStagingStore(context),
            publishTransport = IgcFlightLogPublishTransport(context),
            recoveryFinalizedEntryResolver = IgcRecoveryFinalizedEntryResolver(
                context,
                recoveryDownloadsLookup
            )
        )

        cleanupRows(context, fileName)
        stageFile.parentFile?.mkdirs()
        stageFile.writeText(
            listOf(
                "AXCP710001",
                "HFDTEDATE:090326,01",
                "B1200003746494N12225164WA0012300145"
            ).joinToString(separator = "\n")
        )
        val pendingUri = insertPendingRow(context, fileName)

        try {
            val result = repository.recoverSession(sessionId = sessionId)

            assertEquals(IgcRecoveryResult.Recovered(fileName), result)
            assertEquals(0, queryPendingRows(context, fileName).size)
            assertEquals(1, queryFinalRows(context, fileName).size)
            assertTrue(!stageFile.exists())

            val rerunRepository: IgcFlightLogRepository = MediaStoreIgcFlightLogRepository(
                downloadsRepository = MediaStoreIgcDownloadsRepository(context),
                recoveryMetadataStore = InMemoryRecoveryMetadataStore(),
                namingPolicy = IgcFileNamingPolicy(),
                exportValidationAdapter = newValidationAdapter(),
                gRecordSigner = IgcGRecordSigner(),
                stagingStore = IgcRecoveryStagingStore(context),
                publishTransport = IgcFlightLogPublishTransport(context),
                recoveryFinalizedEntryResolver = IgcRecoveryFinalizedEntryResolver(
                    context,
                    MediaStoreIgcRecoveryDownloadsLookup(context)
                )
            )
            val rerun = rerunRepository.recoverSession(sessionId = sessionId)

            assertEquals(
                IgcRecoveryResult.Failure(
                    code = IgcRecoveryErrorCode.STAGING_MISSING,
                    message = "Recovery staging file missing for session 710001"
                ),
                rerun
            )
            assertEquals(0, queryPendingRows(context, fileName).size)
            assertEquals(1, queryFinalRows(context, fileName).size)
        } finally {
            cleanupRows(context, fileName)
            stageFile.delete()
            metadataStore.clearMetadata(sessionId)
            runCatching { context.contentResolver.delete(pendingUri, null, null) }
        }
    }

    private fun insertPendingRow(context: Context, fileName: String) =
        requireNotNull(
            context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.fai.igc")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${IgcDownloadsStoragePaths.DOWNLOAD_RELATIVE_PATH}/"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            )
        )

    private fun queryPendingRows(context: Context, fileName: String): List<Long> {
        return queryRowIds(
            context = context,
            fileName = fileName,
            pendingValue = 1
        )
    }

    private fun queryFinalRows(context: Context, fileName: String): List<Long> {
        return queryRowIds(
            context = context,
            fileName = fileName,
            pendingValue = 0
        )
    }

    private fun queryRowIds(context: Context, fileName: String, pendingValue: Int): List<Long> {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = buildString {
            append("${MediaStore.Downloads.DISPLAY_NAME} = ?")
            append(" AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?")
            append(" AND ${MediaStore.Downloads.IS_PENDING} = ?")
        }
        val selectionArgs = arrayOf(
            fileName,
            "${IgcDownloadsStoragePaths.DOWNLOAD_RELATIVE_PATH}%",
            pendingValue.toString()
        )
        val ids = mutableListOf<Long>()
        context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                ids += cursor.getLong(idColumn)
            }
        }
        return ids
    }

    private fun cleanupRows(context: Context, fileName: String) {
        val rowIds = queryPendingRows(context, fileName) + queryFinalRows(context, fileName)
        rowIds.forEach { rowId ->
            val rowUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, rowId)
            runCatching { context.contentResolver.delete(rowUri, null, null) }
        }
    }

    private fun stagedFile(context: Context, sessionId: Long): File {
        return File(File(context.filesDir, "igc/staging"), "session_${sessionId}.igc.tmp")
    }

    private class InMemoryRecoveryMetadataStore : IgcRecoveryMetadataStore {
        private val metadataBySessionId = mutableMapOf<Long, IgcRecoveryMetadata>()

        override fun saveMetadata(sessionId: Long, metadata: IgcRecoveryMetadata) {
            metadataBySessionId[sessionId] = metadata
        }

        override fun loadMetadata(sessionId: Long): IgcRecoveryMetadata? {
            return metadataBySessionId[sessionId]
        }

        override fun clearMetadata(sessionId: Long) {
            metadataBySessionId.remove(sessionId)
        }
    }

    private fun newValidationAdapter(): IgcExportValidationAdapter {
        return IgcExportValidationAdapter(
            lintValidator = StrictIgcLintValidator(),
            lintMessageMapper = IgcLintMessageMapper()
        )
    }
}
