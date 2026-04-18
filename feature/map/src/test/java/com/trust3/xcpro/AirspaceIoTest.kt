package com.trust3.xcpro

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AirspaceIoTest {

    private lateinit var testFilesDir: File

    @Before
    fun setUp() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        testFilesDir = File(appContext.cacheDir, "airspace_io_${System.nanoTime()}").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        testFilesDir.deleteRecursively()
    }

    @Test
    fun copyFileToInternalStorage_fallsBackWhenDisplayNameColumnMissing() {
        val payload = "AC D\nDP 4621.200N 00608.100E\n"
        val uri = Uri.parse("content://airspace/missing-column")
        val resolver: ContentResolver = mock()
        val context = mockContext(resolver)
        val cursor = MatrixCursor(arrayOf("unused_column")).apply {
            addRow(arrayOf("unused"))
        }
        whenever(
            resolver.query(eq(uri), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        ).thenReturn(cursor)
        whenever(resolver.openInputStream(eq(uri))).thenReturn(ByteArrayInputStream(payload.toByteArray()))

        val fileName = copyFileToInternalStorage(context, uri)

        assertTrue(fileName.startsWith("file_"))
        assertTrue(fileName.endsWith(".txt"))
        assertEquals(payload, File(testFilesDir, fileName).readText())
    }

    @Test
    fun copyFileToInternalStorage_fallsBackWhenDisplayNameIsBlank() {
        val payload = "AC C\nDP 4622.200N 00609.100E\n"
        val uri = Uri.parse("content://airspace/blank-name")
        val resolver: ContentResolver = mock()
        val context = mockContext(resolver)
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
            addRow(arrayOf("   "))
        }
        whenever(
            resolver.query(eq(uri), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        ).thenReturn(cursor)
        whenever(resolver.openInputStream(eq(uri))).thenReturn(ByteArrayInputStream(payload.toByteArray()))

        val fileName = copyFileToInternalStorage(context, uri)

        assertTrue(fileName.startsWith("file_"))
        assertTrue(fileName.endsWith(".txt"))
        assertEquals(payload, File(testFilesDir, fileName).readText())
    }

    private fun mockContext(resolver: ContentResolver): Context {
        val context: Context = mock()
        whenever(context.contentResolver).thenReturn(resolver)
        whenever(context.filesDir).thenReturn(testFilesDir)
        return context
    }
}
