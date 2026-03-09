package com.example.xcpro.igc.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.igc.domain.IgcFileNamingPolicy
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
class IgcFlightLogRepositoryRecoveryTest {

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
            namingPolicy = IgcFileNamingPolicy()
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
            namingPolicy = IgcFileNamingPolicy()
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

    private fun validRequest(sessionId: Long): IgcFinalizeRequest {
        return IgcFinalizeRequest(
            sessionId = sessionId,
            sessionStartWallTimeMs = 1_741_483_200_000L,
            firstValidFixWallTimeMs = null,
            manufacturerId = "XCP",
            sessionSerial = "456",
            lines = listOf(
                "AXCP000456",
                "HFDTEDATE:090326,01",
                "B1200003746494N12225164WA0012300145"
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
    }
}
