package com.example.xcpro.igc.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.igc.domain.IgcFileNamingPolicy
import com.example.xcpro.igc.domain.IgcGRecordSigner
import com.example.xcpro.igc.domain.IgcRecoveryMetadata
import com.example.xcpro.igc.domain.IgcRecoveryResult
import com.example.xcpro.igc.domain.IgcSecuritySignatureProfile
import com.example.xcpro.igc.domain.StrictIgcLintValidator
import com.example.xcpro.igc.usecase.IgcLintMessageMapper
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IgcFlightLogRepositoryRecoveryKillPointTest {

    @Test
    fun k2_crashAfterStagedWriteBeforeMediaStoreInsert_recoversByPublishingFromStaging() {
        val filesDir = Files.createTempDirectory("igc-k2").toFile()
        writeValidStagedFile(filesDir, sessionId = 102L)
        val resolver: ContentResolver = mock()
        val publishedUri = Uri.parse("content://downloads/public_downloads/102")
        val output = ByteArrayOutputStream()
        whenever(resolver.insert(eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI), any())).thenReturn(publishedUri)
        whenever(resolver.openOutputStream(eq(publishedUri), eq("w"))).thenReturn(output)
        whenever(resolver.update(eq(publishedUri), any(), isNull(), isNull())).thenReturn(1)
        whenever(resolver.query(eq(publishedUri), any(), isNull(), isNull(), isNull()))
            .thenReturn(MatrixCursor(arrayOf(MediaStore.Downloads.DATE_MODIFIED)).apply { addRow(arrayOf(1_773_100_000L)) })

        val metadataStore = metadataStoreFor(sessionId = 102L)
        val downloads = FakeDownloadsRepository()
        val repository = newRepository(
            filesDir = filesDir,
            resolver = resolver,
            downloads = downloads,
            metadataStore = metadataStore,
            recoveryDownloadsLookup = ForwardingRecoveryDownloadsLookup(downloads)
        )

        val result = repository.recoverSession(sessionId = 102L)

        assertEquals(IgcRecoveryResult.Recovered("2026-03-09-XCS-000102-01.IGC"), result)
        assertContainsSignedPayload(output)
        assertNull(metadataStore.loadMetadata(102L))
    }

    @Test
    fun k3_crashAfterPendingRowInsert_deletesPendingRowAndRepublishes() {
        assertPendingRowRecovery(
            sessionId = 103L,
            pendingRowId = 903L
        )
    }

    @Test
    fun k4_crashMidByteCopy_deletesPendingRowAndRepublishes() {
        assertPendingRowRecovery(
            sessionId = 104L,
            pendingRowId = 904L
        )
    }

    @Test
    fun k5_crashAfterByteCopyBeforePendingFinalize_deletesPendingRowAndRepublishes() {
        assertPendingRowRecovery(
            sessionId = 105L,
            pendingRowId = 905L
        )
    }

    @Test
    fun k6_crashAfterPublishBeforeSnapshotClear_recoversExistingFinalizedEntry() {
        val filesDir = Files.createTempDirectory("igc-k6").toFile()
        writeValidStagedFile(filesDir, sessionId = 106L)
        val metadataStore = metadataStoreFor(sessionId = 106L)
        val downloads = FakeDownloadsRepository().apply {
            entriesState.value = listOf(
                finalizedEntry("2026-03-09-XCS-000106-01.IGC")
            )
        }
        val repository = newRepository(
            filesDir = filesDir,
            resolver = mock(),
            downloads = downloads,
            metadataStore = metadataStore,
            recoveryDownloadsLookup = ForwardingRecoveryDownloadsLookup(downloads)
        )

        val result = repository.recoverSession(sessionId = 106L)

        assertEquals(IgcRecoveryResult.Recovered("2026-03-09-XCS-000106-01.IGC"), result)
        assertNull(metadataStore.loadMetadata(106L))
    }

    private fun assertPendingRowRecovery(sessionId: Long, pendingRowId: Long) {
        val filesDir = Files.createTempDirectory("igc-k${sessionId}").toFile()
        writeValidStagedFile(filesDir, sessionId = sessionId)
        val resolver: ContentResolver = mock()
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val publishedUri = Uri.parse("content://downloads/public_downloads/$sessionId")
        whenever(
            resolver.query(
                eq(collection),
                any(),
                any(),
                any(),
                isNull()
            )
        ).thenReturn(MatrixCursor(arrayOf(MediaStore.Downloads._ID)).apply { addRow(arrayOf(pendingRowId)) })
        whenever(resolver.insert(eq(collection), any())).thenReturn(publishedUri)
        whenever(resolver.openOutputStream(eq(publishedUri), eq("w"))).thenReturn(ByteArrayOutputStream())
        whenever(resolver.update(eq(publishedUri), any(), isNull(), isNull())).thenReturn(1)
        whenever(resolver.query(eq(publishedUri), any(), isNull(), isNull(), isNull()))
            .thenReturn(MatrixCursor(arrayOf(MediaStore.Downloads.DATE_MODIFIED)).apply { addRow(arrayOf(1_773_100_000L)) })

        val metadataStore = metadataStoreFor(sessionId)
        val downloads = FakeDownloadsRepository()
        val repository = newRepository(
            filesDir = filesDir,
            resolver = resolver,
            downloads = downloads,
            metadataStore = metadataStore,
            recoveryDownloadsLookup = ForwardingRecoveryDownloadsLookup(downloads)
        )

        val result = repository.recoverSession(sessionId = sessionId)

        assertEquals(
            IgcRecoveryResult.Recovered("2026-03-09-XCS-${sessionId.toString().padStart(6, '0')}-01.IGC"),
            result
        )
        verify(resolver, times(1)).delete(
            eq(ContentUris.withAppendedId(collection, pendingRowId)),
            isNull(),
            isNull()
        )
        assertNull(metadataStore.loadMetadata(sessionId))
    }

    private fun metadataStoreFor(sessionId: Long): InMemoryRecoveryMetadataStore {
        return InMemoryRecoveryMetadataStore().apply {
            saveMetadata(
                sessionId = sessionId,
                metadata = IgcRecoveryMetadata(
                    manufacturerId = "XCS",
                    sessionSerial = sessionId.toString().padStart(6, '0'),
                    sessionStartWallTimeMs = 1_773_014_400_000L,
                    firstValidFixWallTimeMs = 1_773_057_600_000L,
                    signatureProfile = IgcSecuritySignatureProfile.XCS
                )
            )
        }
    }

    private fun newRepository(
        filesDir: File,
        resolver: ContentResolver,
        downloads: FakeDownloadsRepository,
        metadataStore: InMemoryRecoveryMetadataStore,
        recoveryDownloadsLookup: IgcRecoveryDownloadsLookup = ForwardingRecoveryDownloadsLookup(downloads)
    ): MediaStoreIgcFlightLogRepository {
        val context: Context = mock()
        whenever(context.filesDir).thenReturn(filesDir)
        whenever(context.contentResolver).thenReturn(resolver)
        return MediaStoreIgcFlightLogRepository(
            downloadsRepository = downloads,
            recoveryMetadataStore = metadataStore,
            namingPolicy = IgcFileNamingPolicy(),
            exportValidationAdapter = IgcExportValidationAdapter(
                lintValidator = StrictIgcLintValidator(),
                lintMessageMapper = IgcLintMessageMapper()
            ),
            gRecordSigner = IgcGRecordSigner(),
            stagingStore = IgcRecoveryStagingStore(context),
            publishTransport = IgcFlightLogPublishTransport(context),
            recoveryFinalizedEntryResolver = IgcRecoveryFinalizedEntryResolver(
                context,
                recoveryDownloadsLookup
            )
        )
    }

    private fun writeValidStagedFile(filesDir: File, sessionId: Long) {
        val sessionSerial = sessionId.toString().padStart(6, '0')
        val stagingDir = File(filesDir, "igc/staging")
        stagingDir.mkdirs()
        val signedLines = IgcGRecordSigner().sign(
            lines = listOf(
                "AXCS$sessionSerial",
                "HFDTEDATE:090326,01",
                "HFFTYFRTYPE:XCPro,SignedMobile",
                "LXCSDECLARATION_OMITTED:NO_TASK_AT_START",
                "B1200003746494N12225164WA0012300145"
            ),
            profile = IgcSecuritySignatureProfile.XCS
        )
        File(stagingDir, "session_${sessionId}.igc.tmp").writeText(
            signedLines.joinToString(separator = "\n")
        )
    }

    private fun assertContainsSignedPayload(output: ByteArrayOutputStream) {
        val payload = output.toByteArray().decodeToString()
        assertEquals(1, payload.lineSequence().count { it.startsWith("AXCS") })
        assertTrue(payload.lineSequence().any { it.startsWith("G") })
    }

    private fun finalizedEntry(displayName: String): IgcLogEntry {
        return IgcLogEntry(
            document = DocumentRef(
                uri = "content://downloads/public_downloads/${displayName.hashCode()}",
                displayName = displayName
            ),
            displayName = displayName,
            sizeBytes = 100L,
            lastModifiedEpochMillis = 1L,
            utcDate = LocalDate.of(2026, 3, 9),
            durationSeconds = 60L
        )
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

    private class FakeDownloadsRepository : IgcDownloadsRepository {
        val entriesState = MutableStateFlow<List<IgcLogEntry>>(emptyList())
        override val entries: StateFlow<List<IgcLogEntry>> = entriesState
        var refreshCalls: Int = 0

        override fun refreshEntries() {
            refreshCalls += 1
        }

        override fun listExistingNamesForUtcDate(utcDate: LocalDate): Set<String> = emptySet()

        override fun copyToDestination(source: DocumentRef, destinationUri: String): Result<Unit> {
            return Result.success(Unit)
        }

        override fun readDocumentBytes(document: DocumentRef): IgcDocumentReadResult {
            return IgcDocumentReadResult.Failure(
                code = IgcDocumentReadResult.ErrorCode.OPEN_FAILED,
                message = "not used in test"
            )
        }
    }

    private class ForwardingRecoveryDownloadsLookup(
        private val downloadsRepository: IgcDownloadsRepository
    ) : IgcRecoveryDownloadsLookup {
        override fun findFinalizedEntriesByPrefix(
            expectedPrefix: String,
            utcDate: LocalDate
        ): List<IgcLogEntry> {
            return downloadsRepository.entries.value
                .filter { entry ->
                    entry.displayName.startsWith(expectedPrefix) &&
                        entry.displayName.endsWith(".IGC", ignoreCase = true)
                }
                .sortedBy { it.displayName }
        }
    }
}
