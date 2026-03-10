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
import com.example.xcpro.igc.domain.IgcRecoveryErrorCode
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IgcFlightLogRepositoryRecoveryTest {

    @Test
    fun parseStagedRecoveryMetadata_usesDteDateAndFirstBTime() {
        val filesDir = Files.createTempDirectory("igc-recover-metadata").toFile()
        writeStagedFile(
            filesDir = filesDir,
            sessionId = 55L,
            lines = listOf(
                "AXCP000055",
                "HFDTEDATE:090326,01",
                "B1200003746494N12225164WA0012300145"
            )
        )
        val context = repositoryContext(filesDir)
        val repository = MediaStoreIgcFlightLogRepository(
            appContext = context,
            downloadsRepository = FakeDownloadsRepository(),
            recoveryMetadataStore = NoopIgcRecoveryMetadataStore,
            namingPolicy = IgcFileNamingPolicy(),
            exportValidationAdapter = newValidationAdapter(),
            gRecordSigner = IgcGRecordSigner()
        )

        val metadata = repository.parseStagedRecoveryMetadata(55L)

        requireNotNull(metadata)
        assertEquals("XCP", metadata.manufacturerId)
        assertEquals("000055", metadata.sessionSerial)
        assertEquals(1_773_014_400_000L, metadata.sessionStartWallTimeMs)
        assertEquals(1_773_057_600_000L, metadata.firstValidFixWallTimeMs)
    }

    @Test
    fun parseStagedRecoveryMetadata_supportsShortHfdte() {
        val filesDir = Files.createTempDirectory("igc-recover-short-dte").toFile()
        writeStagedFile(
            filesDir = filesDir,
            sessionId = 56L,
            lines = listOf(
                "AXCP000056",
                "HFDTE090326",
                "B1200003746494N12225164WA0012300145"
            )
        )
        val context = repositoryContext(filesDir)
        val repository = MediaStoreIgcFlightLogRepository(
            appContext = context,
            downloadsRepository = FakeDownloadsRepository(),
            recoveryMetadataStore = NoopIgcRecoveryMetadataStore,
            namingPolicy = IgcFileNamingPolicy(),
            exportValidationAdapter = newValidationAdapter(),
            gRecordSigner = IgcGRecordSigner()
        )

        val metadata = repository.parseStagedRecoveryMetadata(56L)

        requireNotNull(metadata)
        assertEquals("XCP", metadata.manufacturerId)
        assertEquals("000056", metadata.sessionSerial)
        assertEquals(1_773_014_400_000L, metadata.sessionStartWallTimeMs)
        assertEquals(1_773_057_600_000L, metadata.firstValidFixWallTimeMs)
    }

    @Test
    fun parseStagedRecoveryMetadata_detectsXcsSignatureProfileFromManufacturer() {
        val filesDir = Files.createTempDirectory("igc-recover-xcs-profile").toFile()
        writeStagedFile(
            filesDir = filesDir,
            sessionId = 57L,
            lines = listOf(
                "AXCS000057",
                "HFDTEDATE:090326,01",
                "B1200003746494N12225164WA0012300145"
            )
        )
        val context = repositoryContext(filesDir)
        val repository = MediaStoreIgcFlightLogRepository(
            appContext = context,
            downloadsRepository = FakeDownloadsRepository(),
            recoveryMetadataStore = NoopIgcRecoveryMetadataStore,
            namingPolicy = IgcFileNamingPolicy(),
            exportValidationAdapter = newValidationAdapter(),
            gRecordSigner = IgcGRecordSigner()
        )

        val metadata = repository.parseStagedRecoveryMetadata(57L)

        requireNotNull(metadata)
        assertEquals("XCS", metadata.manufacturerId)
        assertEquals(IgcSecuritySignatureProfile.XCS, metadata.signatureProfile)
    }

    @Test
    fun recoverSession_returnsExistingFinalizedEntry_andDeletesRecoveryArtifacts() {
        val filesDir = Files.createTempDirectory("igc-recover-existing").toFile()
        writeStagedFile(
            filesDir = filesDir,
            sessionId = 77L,
            lines = listOf(
                "AXCP000077",
                "HFDTEDATE:090326,01",
                "B1200003746494N12225164WA0012300145"
            )
        )
        val metadataStore = InMemoryRecoveryMetadataStore().apply {
            saveMetadata(
                sessionId = 77L,
                metadata = IgcRecoveryMetadata(
                    manufacturerId = "XCP",
                    sessionSerial = "000077",
                    sessionStartWallTimeMs = 1_773_014_400_000L,
                    firstValidFixWallTimeMs = 1_773_057_600_000L,
                    signatureProfile = IgcSecuritySignatureProfile.NONE
                )
            )
        }
        val downloads = FakeDownloadsRepository().apply {
            entriesState.value = listOf(
                finalizedEntry("2026-03-09-XCP-000077-01.IGC")
            )
        }
        val repository = MediaStoreIgcFlightLogRepository(
            appContext = repositoryContext(filesDir),
            downloadsRepository = downloads,
            recoveryMetadataStore = metadataStore,
            namingPolicy = IgcFileNamingPolicy(),
            exportValidationAdapter = newValidationAdapter(),
            gRecordSigner = IgcGRecordSigner()
        )

        val result = repository.recoverSession(sessionId = 77L)

        assertTrue(result is IgcRecoveryResult.Recovered)
        assertEquals(1, downloads.refreshCalls)
        assertTrue(!stagedFile(filesDir, 77L).exists())
        assertNull(metadataStore.loadMetadata(77L))
    }

    @Test
    fun recoverSession_usesStoredMetadataForExistingFinalizedLookup_whenStagedMetadataIsUnreadable() {
        val filesDir = Files.createTempDirectory("igc-recover-stored-existing").toFile()
        writeStagedFile(
            filesDir = filesDir,
            sessionId = 88L,
            lines = listOf("BROKEN")
        )
        val metadataStore = InMemoryRecoveryMetadataStore().apply {
            saveMetadata(
                sessionId = 88L,
                metadata = IgcRecoveryMetadata(
                    manufacturerId = "XCP",
                    sessionSerial = "000088",
                    sessionStartWallTimeMs = 1_773_014_400_000L,
                    firstValidFixWallTimeMs = null,
                    signatureProfile = IgcSecuritySignatureProfile.NONE
                )
            )
        }
        val downloads = FakeDownloadsRepository().apply {
            entriesState.value = listOf(
                finalizedEntry("2026-03-09-XCP-000088-01.IGC")
            )
        }
        val repository = MediaStoreIgcFlightLogRepository(
            appContext = repositoryContext(filesDir),
            downloadsRepository = downloads,
            recoveryMetadataStore = metadataStore,
            namingPolicy = IgcFileNamingPolicy(),
            exportValidationAdapter = newValidationAdapter(),
            gRecordSigner = IgcGRecordSigner()
        )

        val result = repository.recoverSession(sessionId = 88L)

        assertEquals(IgcRecoveryResult.Recovered("2026-03-09-XCP-000088-01.IGC"), result)
        assertTrue(!stagedFile(filesDir, 88L).exists())
        assertNull(metadataStore.loadMetadata(88L))
    }

    @Test
    fun recoverSession_returnsDuplicateGuardFailure_whenMultipleFinalizedEntriesMatch() {
        val filesDir = Files.createTempDirectory("igc-recover-duplicate").toFile()
        val metadataStore = InMemoryRecoveryMetadataStore().apply {
            saveMetadata(
                sessionId = 89L,
                metadata = IgcRecoveryMetadata(
                    manufacturerId = "XCP",
                    sessionSerial = "000089",
                    sessionStartWallTimeMs = 1_773_014_400_000L,
                    firstValidFixWallTimeMs = null,
                    signatureProfile = IgcSecuritySignatureProfile.NONE
                )
            )
        }
        val downloads = FakeDownloadsRepository().apply {
            entriesState.value = listOf(
                finalizedEntry("2026-03-09-XCP-000089-01.IGC"),
                finalizedEntry("2026-03-09-XCP-000089-02.IGC")
            )
        }
        val repository = MediaStoreIgcFlightLogRepository(
            appContext = repositoryContext(filesDir),
            downloadsRepository = downloads,
            recoveryMetadataStore = metadataStore,
            namingPolicy = IgcFileNamingPolicy(),
            exportValidationAdapter = newValidationAdapter(),
            gRecordSigner = IgcGRecordSigner()
        )

        val result = repository.recoverSession(sessionId = 89L)

        assertEquals(
            IgcRecoveryResult.Failure(
                code = IgcRecoveryErrorCode.DUPLICATE_SESSION_GUARD,
                message = "Multiple finalized IGC files matched session 89"
            ),
            result
        )
        assertNull(metadataStore.loadMetadata(89L))
    }

    @Test
    fun recoverSession_cleansPendingRowsFromStoredMetadata_whenStagedMetadataUnreadable() {
        val filesDir = Files.createTempDirectory("igc-recover-cleanup").toFile()
        writeStagedFile(
            filesDir = filesDir,
            sessionId = 90L,
            lines = listOf("BROKEN")
        )
        val resolver: ContentResolver = mock()
        val pendingCursor = MatrixCursor(arrayOf(MediaStore.Downloads._ID)).apply {
            addRow(arrayOf(45L))
        }
        whenever(
            resolver.query(
                eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI),
                any(),
                any(),
                any(),
                isNull()
            )
        ).thenReturn(pendingCursor)
        val context = repositoryContext(filesDir, resolver)
        val metadataStore = InMemoryRecoveryMetadataStore().apply {
            saveMetadata(
                sessionId = 90L,
                metadata = IgcRecoveryMetadata(
                    manufacturerId = "XCP",
                    sessionSerial = "000090",
                    sessionStartWallTimeMs = 1_773_014_400_000L,
                    firstValidFixWallTimeMs = null,
                    signatureProfile = IgcSecuritySignatureProfile.NONE
                )
            )
        }
        val repository = MediaStoreIgcFlightLogRepository(
            appContext = context,
            downloadsRepository = FakeDownloadsRepository(),
            recoveryMetadataStore = metadataStore,
            namingPolicy = IgcFileNamingPolicy(),
            exportValidationAdapter = newValidationAdapter(),
            gRecordSigner = IgcGRecordSigner()
        )

        val result = repository.recoverSession(sessionId = 90L)

        assertEquals(
            IgcRecoveryResult.Failure(
                code = IgcRecoveryErrorCode.STAGING_CORRUPT,
                message = "Recovery staging metadata could not be parsed for session 90"
            ),
            result
        )
        verify(resolver).delete(eq(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, 45L)), isNull(), isNull())
        assertTrue(!stagedFile(filesDir, 90L).exists())
        assertNull(metadataStore.loadMetadata(90L))
    }

    @Test
    fun finalizeSession_deletesPendingRow_whenOutputStreamCannotBeOpened() {
        val filesDir = Files.createTempDirectory("igc-recover-open").toFile()
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        whenever(context.filesDir).thenReturn(filesDir)
        val downloads = FakeDownloadsRepository()
        val repository = MediaStoreIgcFlightLogRepository(
            appContext = context,
            downloadsRepository = downloads,
            recoveryMetadataStore = NoopIgcRecoveryMetadataStore,
            namingPolicy = IgcFileNamingPolicy(),
            exportValidationAdapter = newValidationAdapter(),
            gRecordSigner = IgcGRecordSigner()
        )
        val itemUri = Uri.parse("content://downloads/public_downloads/100")
        whenever(resolver.insert(eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI), any())).thenReturn(itemUri)
        whenever(resolver.openOutputStream(eq(itemUri), eq("w"))).thenReturn(null)

        val result = repository.finalizeSession(validRequest(sessionId = 100L))

        assertTrue(result is IgcFinalizeResult.Failure)
        result as IgcFinalizeResult.Failure
        assertEquals(IgcFinalizeResult.ErrorCode.WRITE_FAILED, result.code)
        verify(resolver).delete(eq(itemUri), isNull(), isNull())
        verify(resolver, never()).update(eq(itemUri), any(), isNull(), isNull())
        assertEquals(0, downloads.refreshCalls)
    }

    @Test
    fun finalizeSession_deletesPendingRow_whenPendingFinalizeUpdateFails() {
        val filesDir = Files.createTempDirectory("igc-recover-update").toFile()
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        whenever(context.filesDir).thenReturn(filesDir)
        val downloads = FakeDownloadsRepository()
        val repository = MediaStoreIgcFlightLogRepository(
            appContext = context,
            downloadsRepository = downloads,
            recoveryMetadataStore = NoopIgcRecoveryMetadataStore,
            namingPolicy = IgcFileNamingPolicy(),
            exportValidationAdapter = newValidationAdapter(),
            gRecordSigner = IgcGRecordSigner()
        )
        val itemUri = Uri.parse("content://downloads/public_downloads/101")
        whenever(resolver.insert(eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI), any())).thenReturn(itemUri)
        whenever(resolver.openOutputStream(eq(itemUri), eq("w"))).thenReturn(ByteArrayOutputStream())
        whenever(resolver.update(eq(itemUri), any(), isNull(), isNull())).thenReturn(0)

        val result = repository.finalizeSession(validRequest(sessionId = 101L))

        assertTrue(result is IgcFinalizeResult.Failure)
        result as IgcFinalizeResult.Failure
        assertEquals(IgcFinalizeResult.ErrorCode.WRITE_FAILED, result.code)
        verify(resolver).delete(eq(itemUri), isNull(), isNull())
        assertEquals(0, downloads.refreshCalls)
    }

    private fun repositoryContext(filesDir: File, resolver: ContentResolver = mock()): Context {
        val context: Context = mock()
        whenever(context.filesDir).thenReturn(filesDir)
        whenever(context.contentResolver).thenReturn(resolver)
        return context
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

    private fun validRequest(sessionId: Long): IgcFinalizeRequest {
        return IgcFinalizeRequest(
            sessionId = sessionId,
            sessionStartWallTimeMs = 1_741_483_200_000L,
            firstValidFixWallTimeMs = null,
            manufacturerId = "XCP",
            sessionSerial = "456",
            signatureProfile = IgcSecuritySignatureProfile.NONE,
            lines = listOf(
                "AXCP000456",
                "HFDTEDATE:090326,01",
                "B1200003746494N12225164WA0012300145"
            )
        )
    }

    private fun writeStagedFile(filesDir: File, sessionId: Long, lines: List<String>) {
        val stagingDir = File(filesDir, "igc/staging")
        stagingDir.mkdirs()
        stagedFile(filesDir, sessionId).writeText(lines.joinToString(separator = "\n"))
    }

    private fun stagedFile(filesDir: File, sessionId: Long): File =
        File(File(filesDir, "igc/staging"), "session_${sessionId}.igc.tmp")

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

    private fun newValidationAdapter(): IgcExportValidationAdapter {
        return IgcExportValidationAdapter(
            lintValidator = StrictIgcLintValidator(),
            lintMessageMapper = IgcLintMessageMapper()
        )
    }
}
