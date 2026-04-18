package com.trust3.xcpro.adsb

import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbTrafficRepositoryRuntimePolicyTest {

    @Test
    fun pollIntervalsAndFloors_areMonotonic() {
        assertTrue(POLL_INTERVAL_HOT_MS < POLL_INTERVAL_WARM_MS)
        assertTrue(POLL_INTERVAL_WARM_MS < POLL_INTERVAL_COLD_MS)
        assertTrue(POLL_INTERVAL_COLD_MS < POLL_INTERVAL_QUIET_MS)
        assertTrue(POLL_INTERVAL_QUIET_MS <= POLL_INTERVAL_MAX_MS)
        assertTrue(ANONYMOUS_POLL_FLOOR_MS >= POLL_INTERVAL_HOT_MS)
        assertTrue(AUTH_FAILED_POLL_FLOOR_MS >= ANONYMOUS_POLL_FLOOR_MS)
    }

    @Test
    fun requestBudgetThresholds_areMonotonic() {
        assertTrue(REQUESTS_PER_HOUR_GUARDED < REQUESTS_PER_HOUR_LOW)
        assertTrue(REQUESTS_PER_HOUR_LOW < REQUESTS_PER_HOUR_CRITICAL)
        assertTrue(BUDGET_FLOOR_GUARDED_MS <= BUDGET_FLOOR_LOW_MS)
        assertTrue(BUDGET_FLOOR_LOW_MS <= BUDGET_FLOOR_CRITICAL_MS)
        assertTrue(CREDIT_FLOOR_CRITICAL < CREDIT_FLOOR_LOW)
        assertTrue(CREDIT_FLOOR_LOW < CREDIT_FLOOR_GUARDED)
    }

    @Test
    fun ownshipReselectPolicyBounds_areSane() {
        assertTrue(OWN_ALTITUDE_RESELECT_MIN_INTERVAL_MS < OWN_ALTITUDE_RESELECT_MAX_INTERVAL_MS)
        assertTrue(OWN_ALTITUDE_RESELECT_MIN_DELTA_METERS > 0.0)
        assertTrue(OWN_ALTITUDE_RESELECT_FORCE_DELTA_METERS > OWN_ALTITUDE_RESELECT_MIN_DELTA_METERS)
        assertTrue(OWNSHIP_REFERENCE_STALE_AFTER_MS >= EXPIRY_AFTER_SEC * 1_000L)
    }
}
