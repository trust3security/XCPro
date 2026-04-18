package com.trust3.xcpro.screens.replay

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trust3.xcpro.common.documents.DocumentRef
import com.trust3.xcpro.igc.usecase.IgcShareRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IgcFilesShareIntentsInstrumentedTest {

    @Test
    fun buildIgcShareChooserIntent_setsReadGrantClipDataAndStreamUri() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val request = IgcShareRequest(
            document = DocumentRef(
                uri = "content://downloads/public_downloads/42",
                displayName = "2025-03-09-XCP-000042-01.IGC"
            ),
            mime = "application/vnd.fai.igc",
            subject = "IGC flight log: 2025-03-09-XCP-000042-01.IGC",
            text = "XCPro IGC file 2025-03-09-XCP-000042-01.IGC",
            chooserTitle = "Share IGC File"
        )

        val chooser = buildIgcShareChooserIntent(context, request)
        val sendIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            chooser.getParcelableExtra(Intent.EXTRA_INTENT)
        }

        assertNotNull(sendIntent)
        requireNotNull(sendIntent)
        assertEquals(Intent.ACTION_SEND, sendIntent.action)
        assertEquals("application/vnd.fai.igc", sendIntent.type)
        assertTrue((sendIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sendIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            sendIntent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        assertEquals(Uri.parse(request.document.uri), streamUri)
        assertNotNull(sendIntent.clipData)
        assertEquals(Uri.parse(request.document.uri), sendIntent.clipData?.getItemAt(0)?.uri)
    }
}
