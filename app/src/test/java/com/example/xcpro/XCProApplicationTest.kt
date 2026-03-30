package com.example.xcpro

import com.example.xcpro.ogn.OgnSciaStartupResetCoordinator
import com.example.xcpro.ogn.OgnSciaStartupResetState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    @Test
    fun `onCreate does not crash when SCIA startup resetter is not injected yet`() {
        val application = XCProApplication()

        application.onCreate()
    }

    @Test
    fun `onCreate does not crash when SCIA startup reset coordinator is injected`() {
        val application = XCProApplication()
        application.sciaStartupResetCoordinator = RecordingSciaStartupResetCoordinator()

        application.onCreate()
    }

    private class RecordingSciaStartupResetCoordinator : OgnSciaStartupResetCoordinator {
        private val state = MutableStateFlow(OgnSciaStartupResetState.PENDING)

        override val resetState: StateFlow<OgnSciaStartupResetState> = state

        override fun startIfNeeded() = Unit
    }
}
