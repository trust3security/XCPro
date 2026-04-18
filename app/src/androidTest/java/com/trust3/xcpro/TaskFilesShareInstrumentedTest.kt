package com.trust3.xcpro

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trust3.xcpro.common.documents.DocumentRef
import com.trust3.xcpro.tasks.ShareRequest
import com.trust3.xcpro.tasks.shareRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskFilesShareInstrumentedTest {

    @Test
    fun multiDocumentShareUsesSingleChooserSendMultipleIntent() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val recordingContext = RecordingContext(baseContext)

        val jsonUri = "content://com.trust3.xcpro.test.provider/cache/task_1.xcp.json"
        val cupUri = "content://com.trust3.xcpro.test.provider/cache/task_1.cup"
        val request = ShareRequest(
            document = DocumentRef(uri = jsonUri, displayName = "task_1.xcp.json"),
            mime = "*/*",
            subject = "Task share",
            text = "Share task files",
            chooserTitle = "Share Task",
            additionalDocuments = listOf(
                DocumentRef(uri = cupUri, displayName = "task_1.cup")
            )
        )

        shareRequest(recordingContext, request)

        val chooserIntent = recordingContext.startedIntent
        assertNotNull("Expected chooser intent to be launched", chooserIntent)
        requireNotNull(chooserIntent)
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)

        val chooserPayload = chooserIntent.payloadIntent()
        assertNotNull("Expected chooser payload intent", chooserPayload)
        requireNotNull(chooserPayload)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, chooserPayload.action)
        assertEquals("*/*", chooserPayload.type)
        assertEquals("Task share", chooserPayload.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Share task files", chooserPayload.getStringExtra(Intent.EXTRA_TEXT))

        val streams = chooserPayload.streamUris()
        assertNotNull("Expected multi-stream payload", streams)
        requireNotNull(streams)
        assertEquals(2, streams.size)
        assertTrue(streams.contains(Uri.parse(jsonUri)))
        assertTrue(streams.contains(Uri.parse(cupUri)))

        val clipData = chooserPayload.clipData
        assertNotNull("Expected clipData for URI grants", clipData)
        requireNotNull(clipData)
        assertEquals(2, clipData.itemCount)
        assertTrue(
            chooserPayload.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        )
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var startedIntent: Intent? = null

        override fun startActivity(intent: Intent) {
            startedIntent = intent
        }
    }
}

private fun Intent.payloadIntent(): Intent? {
    @Suppress("DEPRECATION")
    return getParcelableExtra(Intent.EXTRA_INTENT)
}

private fun Intent.streamUris(): ArrayList<Uri>? {
    @Suppress("DEPRECATION")
    return getParcelableArrayListExtra(Intent.EXTRA_STREAM)
}
