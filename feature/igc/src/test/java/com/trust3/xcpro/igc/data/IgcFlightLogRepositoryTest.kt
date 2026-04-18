package com.trust3.xcpro.igc.data

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import com.trust3.xcpro.common.documents.DocumentRef
import com.trust3.xcpro.igc.domain.IgcFileNamingPolicy
import com.trust3.xcpro.igc.domain.IgcGRecordSigner
import com.trust3.xcpro.igc.domain.IgcSecuritySignatureProfile
import com.trust3.xcpro.igc.domain.StrictIgcLintValidator
import com.trust3.xcpro.igc.usecase.IgcLintMessageMapper
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
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
class IgcFlightLogRepositoryTest {

    @Test
    fun finalizeSession_returnsEmptyPayloadFailure_whenNoLines() {
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        whenever(context.filesDir).thenReturn(Files.createTempDirectory("igc-empty").toFile())
        val downloads = FakeDownloadsRepository()
        val repository = newRepository(context = context, downloads = downloads)

        val result = repository.finalizeSession(
            request = baseRequest(sessionId = 101L, lines = emptyList())
        )

        assertTrue(result is IgcFinalizeResult.Failure)
        result as IgcFinalizeResult.Failure
        assertEquals(IgcFinalizeResult.ErrorCode.EMPTY_PAYLOAD, result.code)
        verify(resolver, never()).insert(any(), any())
        assertEquals(0, downloads.refreshCalls)
    }

    @Test
    fun finalizeSession_publishesToMediaStore_andRefreshesIndex() {
        val filesDir = Files.createTempDirectory("igc-publish").toFile()
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        whenever(context.filesDir).thenReturn(filesDir)
        val downloads = FakeDownloadsRepository()
        val repository = newRepository(context = context, downloads = downloads)

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val itemUri = Uri.parse("content://downloads/public_downloads/42")
        val output = ByteArrayOutputStream()
        whenever(resolver.insert(eq(collection), any())).thenReturn(itemUri)
        whenever(resolver.openOutputStream(eq(itemUri), eq("w"))).thenReturn(output)
        whenever(resolver.update(eq(itemUri), any(), isNull(), isNull())).thenReturn(1)
        val modifiedCursor = MatrixCursor(arrayOf(MediaStore.Downloads.DATE_MODIFIED)).apply {
            addRow(arrayOf(1_742_000_000L))
        }
        whenever(resolver.query(eq(itemUri), any(), isNull(), isNull(), isNull())).thenReturn(modifiedCursor)

        val result = repository.finalizeSession(
            request = baseRequest(
                sessionId = 7L,
                lines = listOf(
                    "AXCP000007",
                    "HFDTEDATE:090326,01",
                    "B1200003746494N12225164WA0012300145"
                )
            )
        )

        assertTrue(result is IgcFinalizeResult.Published)
        result as IgcFinalizeResult.Published
        assertEquals("2025-03-09-XCP-000777-01.IGC", result.fileName)
        assertTrue(output.toByteArray().decodeToString().contains("\r\n"))
        assertEquals(1, downloads.refreshCalls)
        verify(resolver).insert(eq(collection), any())
        verify(resolver).openOutputStream(eq(itemUri), eq("w"))
        verify(resolver).update(eq(itemUri), any(), isNull(), isNull())
    }

    @Test
    fun finalizeSession_appendsGRecordsForXcsProfile_beforePublishing() {
        val filesDir = Files.createTempDirectory("igc-publish-xcs").toFile()
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        whenever(context.filesDir).thenReturn(filesDir)
        val downloads = FakeDownloadsRepository()
        val repository = newRepository(context = context, downloads = downloads)

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val itemUri = Uri.parse("content://downloads/public_downloads/43")
        val output = ByteArrayOutputStream()
        whenever(resolver.insert(eq(collection), any())).thenReturn(itemUri)
        whenever(resolver.openOutputStream(eq(itemUri), eq("w"))).thenReturn(output)
        whenever(resolver.update(eq(itemUri), any(), isNull(), isNull())).thenReturn(1)
        val modifiedCursor = MatrixCursor(arrayOf(MediaStore.Downloads.DATE_MODIFIED)).apply {
            addRow(arrayOf(1_773_100_000L))
        }
        whenever(resolver.query(eq(itemUri), any(), isNull(), isNull(), isNull())).thenReturn(modifiedCursor)

        val result = repository.finalizeSession(
            request = IgcFinalizeRequest(
                sessionId = 43L,
                sessionStartWallTimeMs = 1_773_014_400_000L,
                firstValidFixWallTimeMs = 1_773_057_600_000L,
                manufacturerId = "XCS",
                sessionSerial = "043043",
                signatureProfile = IgcSecuritySignatureProfile.XCS,
                lines = listOf(
                    "AXCS043043",
                    "HFDTEDATE:090326,01",
                    "HFFTYFRTYPE:XCPro,SignedMobile",
                    "LXCSDECLARATION_OMITTED:NO_TASK_AT_START",
                    "B1200003746494N12225164WA0012300145"
                )
            )
        )

        assertTrue(result is IgcFinalizeResult.Published)
        result as IgcFinalizeResult.Published
        assertEquals("2026-03-09-XCS-043043-01.IGC", result.fileName)
        val payload = output.toByteArray().decodeToString()
        assertTrue(payload.contains("AXCS043043\r\n"))
        assertTrue(payload.contains("\r\nG"))
        assertEquals(1, downloads.refreshCalls)
    }

    @Test
    fun finalizeSession_returnsLintFailure_beforePublishWhenPayloadInvalid() {
        val filesDir = Files.createTempDirectory("igc-lint-failure").toFile()
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        whenever(context.filesDir).thenReturn(filesDir)
        val downloads = FakeDownloadsRepository()
        val repository = newRepository(context = context, downloads = downloads)

        val result = repository.finalizeSession(
            request = baseRequest(
                sessionId = 8L,
                lines = listOf(
                    "HFDTEDATE:090326,01",
                    "AXCP000008",
                    "B1200003746494N12225164WA0012300145"
                )
            )
        )

        require(result is IgcFinalizeResult.Failure)
        assertEquals(IgcFinalizeResult.ErrorCode.LINT_VALIDATION_FAILED, result.code)
        assertTrue(result.lintIssues.any { it.code.name == "A_RECORD_NOT_FIRST" })
        verify(resolver, never()).insert(any(), any())
        assertEquals(0, downloads.refreshCalls)
    }

    private fun baseRequest(
        sessionId: Long,
        lines: List<String>
    ): IgcFinalizeRequest {
        return IgcFinalizeRequest(
            sessionId = sessionId,
            sessionStartWallTimeMs = 1_741_483_200_000L,
            firstValidFixWallTimeMs = null,
            manufacturerId = "XCP",
            sessionSerial = "777",
            signatureProfile = IgcSecuritySignatureProfile.NONE,
            lines = lines
        )
    }

    private fun newRepository(
        context: Context,
        downloads: FakeDownloadsRepository,
        recoveryMetadataStore: IgcRecoveryMetadataStore = NoopIgcRecoveryMetadataStore
    ): MediaStoreIgcFlightLogRepository {
        return MediaStoreIgcFlightLogRepository(
            downloadsRepository = downloads,
            recoveryMetadataStore = recoveryMetadataStore,
            namingPolicy = IgcFileNamingPolicy(),
            exportValidationAdapter = newValidationAdapter(),
            gRecordSigner = IgcGRecordSigner(),
            stagingStore = IgcRecoveryStagingStore(context),
            publishTransport = IgcFlightLogPublishTransport(context),
            recoveryFinalizedEntryResolver = IgcRecoveryFinalizedEntryResolver(
                context,
                FakeRecoveryDownloadsLookup()
            )
        )
    }

    private class FakeDownloadsRepository : IgcDownloadsRepository {
        private val state = MutableStateFlow<List<IgcLogEntry>>(emptyList())
        override val entries: StateFlow<List<IgcLogEntry>> = state
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

    private class FakeRecoveryDownloadsLookup : IgcRecoveryDownloadsLookup {
        override fun findFinalizedEntriesByPrefix(
            expectedPrefix: String,
            utcDate: LocalDate
        ): List<IgcLogEntry> = emptyList()
    }

    private fun newValidationAdapter(): IgcExportValidationAdapter {
        return IgcExportValidationAdapter(
            lintValidator = StrictIgcLintValidator(),
            lintMessageMapper = IgcLintMessageMapper()
        )
    }
}
