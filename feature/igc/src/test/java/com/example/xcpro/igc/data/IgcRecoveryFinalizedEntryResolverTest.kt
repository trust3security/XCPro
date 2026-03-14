package com.example.xcpro.igc.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.MatrixCursor
import android.provider.MediaStore
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.igc.domain.IgcRecoveryMetadata
import com.example.xcpro.igc.domain.IgcSecuritySignatureProfile
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IgcRecoveryFinalizedEntryResolverTest {

    @Test
    fun findExistingFinalizedMatch_returnsDuplicate_whenLookupReturnsMultipleEntries() {
        val context: Context = mock()
        whenever(context.contentResolver).thenReturn(mock())
        val lookup = FakeRecoveryDownloadsLookup(
            entries = listOf(
                finalizedEntry("2026-03-09-XCP-000089-01.IGC"),
                finalizedEntry("2026-03-09-XCP-000089-02.IGC")
            )
        )
        val resolver = IgcRecoveryFinalizedEntryResolver(context, lookup)

        val result = resolver.findExistingFinalizedMatch(validMetadata(sessionSerial = "000089"))

        require(result is IgcRecoveryFinalizedEntryMatch.Duplicate)
        assertEquals(2, result.entries.size)
        assertEquals(1, lookup.lookupCalls)
    }

    @Test
    fun cleanupPendingRows_deletesPendingRowsForMetadataIdentity() {
        val context: Context = mock()
        val contentResolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(contentResolver)
        val pendingCursor = MatrixCursor(arrayOf(MediaStore.Downloads._ID)).apply {
            addRow(arrayOf(45L))
            addRow(arrayOf(46L))
        }
        whenever(
            contentResolver.query(
                eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI),
                any(),
                any(),
                any(),
                isNull()
            )
        ).thenReturn(pendingCursor)
        val resolver = IgcRecoveryFinalizedEntryResolver(context, FakeRecoveryDownloadsLookup())

        resolver.cleanupPendingRows(validMetadata(sessionSerial = "000090"))

        verify(contentResolver).delete(
            eq(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, 45L)),
            isNull(),
            isNull()
        )
        verify(contentResolver).delete(
            eq(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, 46L)),
            isNull(),
            isNull()
        )
    }

    private fun validMetadata(sessionSerial: String): IgcRecoveryMetadata {
        return IgcRecoveryMetadata(
            manufacturerId = "XCP",
            sessionSerial = sessionSerial,
            sessionStartWallTimeMs = 1_773_014_400_000L,
            firstValidFixWallTimeMs = 1_773_057_600_000L,
            signatureProfile = IgcSecuritySignatureProfile.NONE
        )
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
            durationSeconds = null
        )
    }

    private class FakeRecoveryDownloadsLookup(
        private val entries: List<IgcLogEntry> = emptyList()
    ) : IgcRecoveryDownloadsLookup {
        var lookupCalls: Int = 0

        override fun findFinalizedEntriesByPrefix(
            expectedPrefix: String,
            utcDate: LocalDate
        ): List<IgcLogEntry> {
            lookupCalls += 1
            return entries
        }
    }
}
