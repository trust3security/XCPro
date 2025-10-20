package com.example.xcpro

import com.example.xcpro.map.LocationManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object ServiceLocator {
    @Volatile
    var locationManager: LocationManager? = null

    private val hawkDashboardClients = AtomicInteger(0)
    private val hawkDashboardPending = AtomicBoolean(false)

    fun prepareHawkDashboardClient() {
        hawkDashboardPending.set(true)
    }

    fun finalizeHawkDashboardClient(): Boolean {
        val shouldRegister = hawkDashboardPending.getAndSet(false)
        if (shouldRegister) {
            hawkDashboardClients.incrementAndGet()
            return true
        }
        return false
    }

    fun cancelHawkDashboardPreparation() {
        hawkDashboardPending.set(false)
    }

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

    fun hasHawkDashboardClient(): Boolean =
        hawkDashboardPending.get() || hawkDashboardClients.get() > 0
}
