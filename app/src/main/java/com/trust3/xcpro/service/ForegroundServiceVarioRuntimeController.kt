package com.trust3.xcpro.service

import android.content.Context
import com.trust3.xcpro.livesource.LiveSourceStatePort
import com.trust3.xcpro.livesource.LiveStartupRequirement
import com.trust3.xcpro.map.VarioRuntimeControlPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundServiceVarioRuntimeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val liveSourceStatePort: LiveSourceStatePort
) : VarioRuntimeControlPort {
    override fun ensureRunningIfPermitted(): Boolean {
        return when (liveSourceStatePort.refreshAndGetState().startupRequirement) {
            LiveStartupRequirement.NONE -> {
                VarioForegroundService.start(context)
                true
            }

            LiveStartupRequirement.ANDROID_FINE_LOCATION_PERMISSION -> false
        }
    }

    override fun requestStop() {
        VarioForegroundService.stop(context)
    }
}
