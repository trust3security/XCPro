package com.example.xcpro.service

import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VarioForegroundServiceTest {

    @Test
    fun `onCreate starts pipeline and onDestroy stops it`() {
        val controller = Robolectric.buildService(VarioForegroundService::class.java)
        val service = controller.get()
        val fakeManager = mock<com.example.xcpro.vario.VarioServiceManager>()
        service.manager = fakeManager

        controller.create()
        verify(fakeManager).start()

        controller.destroy()
        verify(fakeManager).stop()
    }
}

