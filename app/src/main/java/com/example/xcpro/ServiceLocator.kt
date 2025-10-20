package com.example.xcpro

import com.example.xcpro.map.LocationManager
import java.util.concurrent.atomic.AtomicInteger

object ServiceLocator {
    @Volatile
    var locationManager: LocationManager? = null

    private val hawkDashboardClients = AtomicInteger(0)

    fun registerHawkDashboardClient() {
        hawkDashboardClients.incrementAndGet()
    }

    fun unregisterHawkDashboardClient() {
        val remaining = hawkDashboardClients.updateAndGet { current ->
            if (current <= 0) 0 else current - 1
        }
        if (remaining == 0) {
            locationManager?.stopLocationTracking(force = true)
        }
    }

    fun hasHawkDashboardClient(): Boolean = hawkDashboardClients.get() > 0
}
