package com.example.xcpro.igc.data

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IgcFlightLogRepositoryIdempotencyTest {

    @Test
    fun finalizeSession_secondCallForSameSession_returnsAlreadyPublishedWithoutSecondInsert() {
        val filesDir = Files.createTempDirectory("igc-idempotent").toFile()
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        whenever(context.filesDir).thenReturn(filesDir)
        val downloads = FakeDownloadsRepository()
        val repository = MediaStoreIgcFlightLogRepository(
            appContext = context,
            downloadsRepository = downloads,
            recoveryMetadataStore = NoopIgcRecoveryMetadataStore,
            namingPolicy = IgcFileNamingPolicy()
        )

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val itemUri = Uri.parse("content://downloads/public_downloads/77")
        whenever(resolver.insert(eq(collection), any())).thenReturn(itemUri)
        whenever(resolver.openOutputStream(eq(itemUri), eq("w"))).thenReturn(ByteArrayOutputStream())
        whenever(resolver.update(eq(itemUri), any(), isNull(), isNull())).thenReturn(1)
        val modifiedCursor = MatrixCursor(arrayOf(MediaStore.Downloads.DATE_MODIFIED)).apply {
            addRow(arrayOf(1_742_000_123L))
        }
        whenever(resolver.query(eq(itemUri), any(), isNull(), isNull(), isNull())).thenReturn(modifiedCursor)

        val request = IgcFinalizeRequest(
            sessionId = 909L,
            sessionStartWallTimeMs = 1_741_483_200_000L,
            firstValidFixWallTimeMs = null,
            manufacturerId = "XCP",
            sessionSerial = "909",
            lines = listOf(
                "AXCP000909",
                "HFDTEDATE:090326,01",
                "B1200003746494N12225164WA0012300145"
            )
        )

        val first = repository.finalizeSession(request)
        val second = repository.finalizeSession(request)

        assertTrue(first is IgcFinalizeResult.Published)
        assertTrue(second is IgcFinalizeResult.AlreadyPublished)
        assertEquals(1, downloads.refreshCalls)
        verify(resolver, times(1)).insert(eq(collection), any())
        verify(resolver, times(1)).openOutputStream(eq(itemUri), eq("w"))
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
