package com.example.xcpro.ogn.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.xcpro.ogn.domain.OgnNetworkAvailabilityPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class AndroidOgnNetworkAvailabilityAdapter @Inject constructor(
    @ApplicationContext context: Context
) : OgnNetworkAvailabilityPort {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val tracker = OgnNetworkAvailabilityTracker()

    override val isOnline: StateFlow<Boolean> = tracker.isOnline

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            tracker.onAvailable(currentOnlineState())
        }

        override fun onLost(network: Network) {
            tracker.onLost(currentOnlineState())
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            tracker.onCapabilitiesChanged(networkCapabilities.hasUsableInternet())
        }

        override fun onUnavailable() {
            tracker.onUnavailable()
        }
    }

    override fun currentOnlineState(): Boolean = readCurrentOnlineState()

    init {
        val manager = connectivityManager
        if (manager == null) {
            tracker.onRegistrationFailure()
        } else {
            tracker.onCapabilitiesChanged(currentOnlineState())
            runCatching {
                manager.registerDefaultNetworkCallback(callback)
            }.onFailure {
                tracker.onRegistrationFailure()
            }
        }
    }

    private fun readCurrentOnlineState(): Boolean {
        val manager = connectivityManager ?: return true
        val activeNetwork = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasUsableInternet()
    }

    private fun NetworkCapabilities.hasUsableInternet(): Boolean {
        return hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
