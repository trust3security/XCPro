package com.example.xcpro.service

import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VarioForegroundServiceTest {

    @Ignore("Foreground service lifecycle now depends on API 33+ notification APIs; needs instrumentation")
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
