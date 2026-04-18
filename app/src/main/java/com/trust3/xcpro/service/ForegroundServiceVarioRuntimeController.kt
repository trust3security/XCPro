package com.trust3.xcpro.service

import android.content.Context
import com.trust3.xcpro.map.VarioRuntimeControlPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundServiceVarioRuntimeController @Inject constructor(
    @ApplicationContext private val context: Context
) : VarioRuntimeControlPort {
    override fun ensureRunningIfPermitted(): Boolean =
        VarioForegroundService.startIfPermitted(context)

    override fun requestStop() {
        VarioForegroundService.stop(context)
    }
}
