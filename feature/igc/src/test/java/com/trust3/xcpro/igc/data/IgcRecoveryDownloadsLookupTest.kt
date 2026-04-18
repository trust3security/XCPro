package com.trust3.xcpro.igc.data

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class IgcRecoveryDownloadsLookupTest {

    @Test
    fun findFinalizedEntriesByPrefix_queriesMinimalMediaStoreFields_withoutReadingDocuments() {
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        val lookup = MediaStoreIgcRecoveryDownloadsLookup(context)
        val cursor = MatrixCursor(
            arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED
            )
        ).apply {
            addRow(arrayOf(44L, "2026-03-09-XCP-000089-01.IGC", 123L, 1_773_100_000L))
            addRow(arrayOf(45L, "2026-03-09-XCP-000089-not-igc.txt", 456L, 1_773_100_100L))
        }
        whenever(
            resolver.query(
                eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI),
                any(),
                any(),
                any(),
                eq("${MediaStore.Downloads.DISPLAY_NAME} ASC")
            )
        ).thenReturn(cursor)

        val result = lookup.findFinalizedEntriesByPrefix(
            expectedPrefix = "2026-03-09-XCP-000089-",
            utcDate = LocalDate.of(2026, 3, 9)
        )

        assertEquals(1, result.size)
        assertEquals("2026-03-09-XCP-000089-01.IGC", result.first().displayName)
        assertEquals(123L, result.first().sizeBytes)
        assertEquals(1_773_100_000_000L, result.first().lastModifiedEpochMillis)
        assertEquals(LocalDate.of(2026, 3, 9), result.first().utcDate)
        assertNull(result.first().durationSeconds)
        verify(resolver, never()).openInputStream(any<Uri>())
    }
}
