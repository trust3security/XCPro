package com.example.xcpro.tasks

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.xcpro.common.documents.DocumentRef
import kotlinx.coroutines.test.runTest
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
@Config(sdk = [30])
class TaskFilesRepositoryFailureTest {
    @Test
    fun `saveToDownloads deletes pending row when output stream cannot be opened`() = runTest {
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        val repository = TaskFilesRepository(context)

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val itemUri = Uri.parse("content://downloads/public_downloads/999")
        whenever(resolver.insert(eq(collection), any())).thenReturn(itemUri)
        whenever(resolver.openOutputStream(itemUri)).thenReturn(null)

        val result = repository.saveToDownloads("failure.cup", "payload")

        assertNull(result)
        verify(resolver).delete(eq(itemUri), isNull(), isNull())
        verify(resolver, never()).update(eq(itemUri), any(), isNull(), isNull())
    }

    @Test
    fun `saveToDownloads returns null when MediaStore insert fails`() = runTest {
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        val repository = TaskFilesRepository(context)

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        whenever(resolver.insert(eq(collection), any())).thenReturn(null)

        val result = repository.saveToDownloads("failure.cup", "payload")

        assertNull(result)
        verify(resolver, never()).openOutputStream(any())
    }

    @Test
    fun `readText returns null when URI stream is unavailable`() = runTest {
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        val repository = TaskFilesRepository(context)
        val uri = Uri.parse("content://downloads/public_downloads/1000")
        whenever(resolver.openInputStream(uri)).thenReturn(null)

        val result = repository.readText(
            DocumentRef(
                uri = uri.toString(),
                displayName = "missing.cup"
            )
        )

        assertNull(result)
    }
}
