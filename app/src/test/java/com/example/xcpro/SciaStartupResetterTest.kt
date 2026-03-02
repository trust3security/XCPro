package com.example.xcpro

import com.example.xcpro.ogn.OgnTrailSelectionPreferencesRepository
import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking

class SciaStartupResetterTest {

    @Test
    fun `resetForFreshProcessStart disables SCIA and clears selected aircraft`() = runTest {
        val trafficPreferencesRepository = mock<OgnTrafficPreferencesRepository>()
        val trailSelectionPreferencesRepository = mock<OgnTrailSelectionPreferencesRepository>()
        val resetter = SciaStartupResetter(
            ognTrafficPreferencesRepository = trafficPreferencesRepository,
            ognTrailSelectionPreferencesRepository = trailSelectionPreferencesRepository
        )

        resetter.resetForFreshProcessStart()

        verifyBlocking(trafficPreferencesRepository) { setShowSciaEnabled(false) }
        verifyBlocking(trailSelectionPreferencesRepository) { clearSelectedAircraft() }
    }
}
