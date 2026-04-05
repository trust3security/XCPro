package com.example.xcpro.map

import android.view.Choreographer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class AdsbOverlayFrameLoopControllerTest {

    @Test
    fun schedule_postsOnlyOnceUntilDispatched() {
        val choreographer: Choreographer = mock()
        val controller = AdsbOverlayFrameLoopController(
            minRenderIntervalMs = 66L,
            choreographer = choreographer
        )
        val callback = Choreographer.FrameCallback { }

        assertTrue(controller.schedule(callback))
        assertFalse(controller.schedule(callback))
        verify(choreographer, times(1)).postFrameCallback(callback)

        controller.onFrameDispatched()

        assertTrue(controller.schedule(callback))
        verify(choreographer, times(2)).postFrameCallback(callback)
    }
}
