package com.example.xcpro.igc.data

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import com.example.xcpro.common.documents.DocumentRef
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IgcDownloadsRepositoryTest {

    @Test
    fun refreshEntries_readsMediaStoreRows_andParsesUtcDateAndDuration() {
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        val repository = MediaStoreIgcDownloadsRepository(context)

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val cursor = MatrixCursor(
            arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED
            )
        ).apply {
            addRow(arrayOf(55L, "2025-03-09-XCP-000111-01.IGC", 256L, 1_742_000_000L))
        }
        whenever(
            resolver.query(
                eq(collection),
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(cursor)

        val rowUri = Uri.withAppendedPath(collection, "55")
        val logBytes = (
            "AXCP000111\r\n" +
                "B2359593746494N12225164WA0012300145\r\n" +
                "B0000103746494N12225164WA0012300145\r\n"
            ).toByteArray()
        whenever(resolver.openInputStream(eq(rowUri))).thenReturn(ByteArrayInputStream(logBytes))

        repository.refreshEntries()
        val entries = repository.entries.value

        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals("2025-03-09-XCP-000111-01.IGC", entry.displayName)
        assertEquals(256L, entry.sizeBytes)
        assertEquals(LocalDate.of(2025, 3, 9), entry.utcDate)
        assertEquals(11L, entry.durationSeconds)
        assertTrue(entry.document.uri.contains("/55"))
    }

    @Test
    fun copyToDestination_copiesSourceContentToDestinationUri() {
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        val repository = MediaStoreIgcDownloadsRepository(context)

        val source = DocumentRef(
            uri = "content://downloads/public_downloads/200",
            displayName = "flight.igc"
        )
        val destinationUri = "content://com.android.externalstorage.documents/document/primary:Download/copy.igc"
        val output = ByteArrayOutputStream()
        whenever(resolver.openInputStream(eq(Uri.parse(source.uri)))).thenReturn(
            ByteArrayInputStream("IGC-CONTENT".toByteArray())
        )
        whenever(resolver.openOutputStream(eq(Uri.parse(destinationUri)), eq("w"))).thenReturn(output)

        val result = repository.copyToDestination(source, destinationUri)

        assertTrue(result.isSuccess)
        assertNotNull(output.toByteArray())
        assertEquals("IGC-CONTENT", output.toByteArray().decodeToString())
    }

    @Test
    fun listExistingNamesForUtcDate_returnsOnlyMatchingDayPrefix() {
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        val repository = MediaStoreIgcDownloadsRepository(context)

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val cursor = MatrixCursor(
            arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED
            )
        ).apply {
            addRow(arrayOf(1L, "2025-03-09-XCP-000111-01.IGC", 100L, 1_742_000_001L))
            addRow(arrayOf(2L, "2025-03-10-XCP-000222-01.IGC", 120L, 1_742_000_002L))
        }
        whenever(
            resolver.query(
                eq(collection),
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(cursor)
        whenever(resolver.openInputStream(any())).thenReturn(null)

        val names = repository.listExistingNamesForUtcDate(LocalDate.of(2025, 3, 9))

        assertEquals(setOf("2025-03-09-XCP-000111-01.IGC"), names)
    }
}
