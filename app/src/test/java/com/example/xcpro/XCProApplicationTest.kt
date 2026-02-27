package com.example.xcpro

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class XCProApplicationTest {

    @Test
    fun `workManagerConfiguration does not crash when workerFactory is not injected yet`() {
        val application = XCProApplication()
        val configuration = application.workManagerConfiguration
        assertNotNull(configuration)
    }
}
