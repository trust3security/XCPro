package com.trust3.xcpro.service

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.livesource.EffectiveLiveSource
import com.trust3.xcpro.livesource.LiveSourceKind
import com.trust3.xcpro.livesource.LiveSourceStatePort
import com.trust3.xcpro.livesource.LiveSourceStatus
import com.trust3.xcpro.livesource.LiveStartupRequirement
import com.trust3.xcpro.livesource.ResolvedLiveSourceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ForegroundServiceVarioRuntimeControllerTest {

    @Test
    fun ensureRunningIfPermitted_startsForegroundService_when_startupRequirement_is_none() {
        val context = RecordingContext(ApplicationProvider.getApplicationContext())
        val controller = ForegroundServiceVarioRuntimeController(
            context = context,
            liveSourceStatePort = fixedLiveSourceStatePort(
                ResolvedLiveSourceState(
                    effectiveSource = EffectiveLiveSource.CONDOR2,
                    startupRequirement = LiveStartupRequirement.NONE,
                    status = LiveSourceStatus.CondorReady,
                    kind = LiveSourceKind.SIMULATOR_CONDOR2
                )
            )
        )

        assertTrue(controller.ensureRunningIfPermitted())
        assertNotNull(context.lastForegroundStartedService)
        assertEquals(
            VarioForegroundService::class.java.name,
            context.lastForegroundStartedService?.component?.className
        )
        assertNull(context.lastStartedService)
    }

    @Test
    fun ensureRunningIfPermitted_returns_false_without_service_start_when_permission_is_required() {
        val context = RecordingContext(ApplicationProvider.getApplicationContext())
        val controller = ForegroundServiceVarioRuntimeController(
            context = context,
            liveSourceStatePort = fixedLiveSourceStatePort(
                ResolvedLiveSourceState(
                    effectiveSource = EffectiveLiveSource.PHONE,
                    startupRequirement = LiveStartupRequirement.ANDROID_FINE_LOCATION_PERMISSION,
                    status = LiveSourceStatus.PhoneReady,
                    kind = LiveSourceKind.PHONE
                )
            )
        )

        assertFalse(controller.ensureRunningIfPermitted())
        assertNull(context.lastForegroundStartedService)
        assertNull(context.lastStartedService)
    }

    @Test
    fun requestStop_stopsForegroundService() {
        val context = RecordingContext(ApplicationProvider.getApplicationContext())
        val controller = ForegroundServiceVarioRuntimeController(
            context = context,
            liveSourceStatePort = fixedLiveSourceStatePort()
        )

        controller.requestStop()

        assertNotNull(context.lastStoppedService)
        assertEquals(
            VarioForegroundService::class.java.name,
            context.lastStoppedService?.component?.className
        )
    }

    private fun fixedLiveSourceStatePort(
        state: ResolvedLiveSourceState = ResolvedLiveSourceState()
    ): LiveSourceStatePort = object : LiveSourceStatePort {
        private val mutableState = MutableStateFlow(state)

        override val state: StateFlow<ResolvedLiveSourceState> = mutableState.asStateFlow()

        override fun refreshAndGetState(): ResolvedLiveSourceState = mutableState.value
    }

    private class RecordingContext(
        baseContext: Context
    ) : ContextWrapper(baseContext) {
        var lastStartedService: Intent? = null
            private set
        var lastForegroundStartedService: Intent? = null
            private set
        var lastStoppedService: Intent? = null
            private set

        override fun startService(service: Intent): ComponentName {
            lastStartedService = service
            return ComponentName(this, VarioForegroundService::class.java)
        }

        override fun startForegroundService(service: Intent): ComponentName {
            lastForegroundStartedService = service
            return ComponentName(this, VarioForegroundService::class.java)
        }

        override fun stopService(name: Intent): Boolean {
            lastStoppedService = name
            return true
        }
    }
}
