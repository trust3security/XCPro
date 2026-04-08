package com.example.xcpro.service

import android.os.Looper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VarioForegroundServiceTest {

    @Test
    fun `onStartCommand ensures manager is running and onDestroy stops manager`() {
        val controller = Robolectric.buildService(VarioForegroundService::class.java)
        val service = controller.get()

        controller.create()
        shadowOf(Looper.getMainLooper()).idle()
        val shadowService = shadowOf(service)
        assertEquals(42, shadowService.lastForegroundNotificationId)
        assertNotNull(shadowService.lastForegroundNotification)
        assertTrue(shadowService.isLastForegroundNotificationAttached)

        val fakeManager = mock<com.example.xcpro.vario.VarioServiceManager>()
        runBlocking {
            whenever(fakeManager.start(any())).thenReturn(true)
        }
        service.manager = fakeManager

        service.onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        service.onStartCommand(null, 0, 2)
        shadowOf(Looper.getMainLooper()).idle()

        runBlocking {
            verify(fakeManager, times(2)).start(any())
        }

        controller.destroy()
        verify(fakeManager).stop()
    }
}
