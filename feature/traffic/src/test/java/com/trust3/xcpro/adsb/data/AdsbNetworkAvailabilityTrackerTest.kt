package com.trust3.xcpro.adsb.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbNetworkAvailabilityTrackerTest {

    @Test
    fun callbackFlapping_updatesOnlineStateDeterministically() {
        val tracker = AdsbNetworkAvailabilityTracker(initialOnline = true)

        tracker.onCapabilitiesChanged(hasUsableInternet = false)
        assertFalse(tracker.isOnline.value)

        tracker.onAvailable(currentOnlineState = true)
        assertTrue(tracker.isOnline.value)

        tracker.onLost(currentOnlineState = false)
        assertFalse(tracker.isOnline.value)

        tracker.onCapabilitiesChanged(hasUsableInternet = true)
        assertTrue(tracker.isOnline.value)
    }

    @Test
    fun registrationFailure_usesFailOpenState() {
        val tracker = AdsbNetworkAvailabilityTracker(initialOnline = false)

        tracker.onRegistrationFailure()

        assertTrue(tracker.isOnline.value)
    }

    @Test
    fun unavailable_forcesOfflineUntilConnectivityRestores() {
        val tracker = AdsbNetworkAvailabilityTracker(initialOnline = true)

        tracker.onUnavailable()
        assertFalse(tracker.isOnline.value)

        tracker.onCapabilitiesChanged(hasUsableInternet = true)
        assertTrue(tracker.isOnline.value)
    }
}
