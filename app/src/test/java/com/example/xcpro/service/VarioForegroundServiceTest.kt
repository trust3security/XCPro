package com.example.xcpro.service

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VarioForegroundServiceTest {

    @Test
    fun `onCreate starts foreground and onDestroy stops manager`() {
        val controller = Robolectric.buildService(VarioForegroundService::class.java)
        val service = controller.get()

        controller.create()
        shadowOf(Looper.getMainLooper()).idle()
        val shadowService = shadowOf(service)
        assertEquals(42, shadowService.lastForegroundNotificationId)
        assertNotNull(shadowService.lastForegroundNotification)
        assertTrue(shadowService.isLastForegroundNotificationAttached)

        val fakeManager = mock<com.example.xcpro.vario.VarioServiceManager>()
        service.manager = fakeManager
        controller.destroy()
        verify(fakeManager).stop()
    }
}
