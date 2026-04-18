package com.trust3.xcpro

import com.trust3.xcpro.ogn.OgnTrailSelectionPreferencesRepository
import com.trust3.xcpro.ogn.OgnTrafficPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking

class SciaStartupResetterTest {

    @Test
    fun `resetForFreshProcessStart disables SCIA clears target and clears selected aircraft`() = runTest {
        val trafficPreferencesRepository = mock<OgnTrafficPreferencesRepository>()
        val trailSelectionPreferencesRepository = mock<OgnTrailSelectionPreferencesRepository>()
        val resetter = SciaStartupResetter(
            ognTrafficPreferencesRepository = trafficPreferencesRepository,
            ognTrailSelectionPreferencesRepository = trailSelectionPreferencesRepository
        )

        resetter.resetForFreshProcessStart()

        verifyBlocking(trafficPreferencesRepository) { setShowSciaEnabled(false) }
        verifyBlocking(trafficPreferencesRepository) { clearTargetSelection() }
        verifyBlocking(trailSelectionPreferencesRepository) { clearSelectedAircraft() }
    }
}
