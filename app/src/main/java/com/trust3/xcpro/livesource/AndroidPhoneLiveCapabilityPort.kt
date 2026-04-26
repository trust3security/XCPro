package com.trust3.xcpro.livesource

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AndroidPhoneLiveCapabilityPort @Inject constructor(
    @ApplicationContext private val context: Context
) : PhoneLiveCapabilityPort {
    private val appContext = context.applicationContext
    private val capabilityState = MutableStateFlow(resolveCapability(appContext))

    override val capability: StateFlow<PhoneLiveCapability> = capabilityState.asStateFlow()

    private val providerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshAndGetCapability()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                providerStateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(providerStateReceiver, filter)
        }
    }

    override fun refreshAndGetCapability(): PhoneLiveCapability {
        val resolvedCapability = resolveCapability(appContext)
        capabilityState.value = resolvedCapability
        return resolvedCapability
    }

    private fun resolveCapability(appContext: Context): PhoneLiveCapability {
        val hasPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return PhoneLiveCapability.Unavailable(
                PhoneLiveCapabilityReason.LOCATION_PERMISSION_MISSING
            )
        }

        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return PhoneLiveCapability.Unavailable(
                PhoneLiveCapabilityReason.PLATFORM_RUNTIME_UNAVAILABLE
            )

        val gpsEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
        if (!gpsEnabled) {
            return PhoneLiveCapability.Unavailable(
                PhoneLiveCapabilityReason.LOCATION_PROVIDER_DISABLED
            )
        }

        return PhoneLiveCapability.Ready
    }
}
