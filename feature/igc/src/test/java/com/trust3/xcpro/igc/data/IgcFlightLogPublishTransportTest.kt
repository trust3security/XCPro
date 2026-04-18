package com.trust3.xcpro.igc.data

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class IgcFlightLogPublishTransportTest {

    @Test
    fun publish_publishesToMediaStoreAndReturnsEntry() {
        val filesDir = Files.createTempDirectory("igc-transport-success").toFile()
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.filesDir).thenReturn(filesDir)
        whenever(context.contentResolver).thenReturn(resolver)
        val transport = IgcFlightLogPublishTransport(context)
        val itemUri = Uri.parse("content://downloads/public_downloads/42")
        val output = ByteArrayOutputStream()
        whenever(resolver.insert(eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI), any())).thenReturn(itemUri)
        whenever(resolver.openOutputStream(eq(itemUri), eq("w"))).thenReturn(output)
        whenever(resolver.update(eq(itemUri), any(), isNull(), isNull())).thenReturn(1)
        whenever(resolver.query(eq(itemUri), any(), isNull(), isNull(), isNull()))
            .thenReturn(MatrixCursor(arrayOf(MediaStore.Downloads.DATE_MODIFIED)).apply {
                addRow(arrayOf(1_742_000_000L))
            })

        val result = transport.publish(
            fileName = "2025-03-09-XCP-000777-01.IGC",
            payload = "AXCP000777\r\nB1200003746494N12225164WA0012300145\r\n".toByteArray(),
            utcDate = LocalDate.of(2025, 3, 9)
        )

        assertTrue(result is IgcFinalizeResult.Published)
        result as IgcFinalizeResult.Published
        assertEquals("2025-03-09-XCP-000777-01.IGC", result.fileName)
        assertTrue(output.toByteArray().decodeToString().contains("\r\n"))
        verify(resolver).insert(eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI), any())
        verify(resolver).update(eq(itemUri), any(), isNull(), isNull())
    }

    @Test
    fun publish_deletesPendingRowWhenOutputStreamCannotBeOpened() {
        val filesDir = Files.createTempDirectory("igc-transport-failure").toFile()
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.filesDir).thenReturn(filesDir)
        whenever(context.contentResolver).thenReturn(resolver)
        val transport = IgcFlightLogPublishTransport(context)
        val itemUri = Uri.parse("content://downloads/public_downloads/100")
        whenever(resolver.insert(eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI), any())).thenReturn(itemUri)
        whenever(resolver.openOutputStream(eq(itemUri), eq("w"))).thenReturn(null)

        val result = transport.publish(
            fileName = "2025-03-09-XCP-000777-01.IGC",
            payload = "payload".toByteArray(),
            utcDate = LocalDate.of(2025, 3, 9)
        )

        assertTrue(result is IgcFinalizeResult.Failure)
        result as IgcFinalizeResult.Failure
        assertEquals(IgcFinalizeResult.ErrorCode.WRITE_FAILED, result.code)
        verify(resolver).delete(eq(itemUri), isNull(), isNull())
    }
}
