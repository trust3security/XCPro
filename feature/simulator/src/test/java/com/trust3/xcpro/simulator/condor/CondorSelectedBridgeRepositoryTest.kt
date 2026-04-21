package com.trust3.xcpro.simulator.condor

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CondorSelectedBridgeRepositoryTest {

    @Test
    fun selected_bridge_persists_across_repository_recreation() = runTest {
        val storage = FakeCondorSelectedBridgeStorage()
        val firstRepository = CondorSelectedBridgeRepository(storage, Unit)

        firstRepository.setSelectedBridge(TEST_CONDOR_BRIDGE_A)

        val recreatedRepository = CondorSelectedBridgeRepository(storage, Unit)
        assertEquals(TEST_CONDOR_BRIDGE_A.stableId, recreatedRepository.selectedBridge.value?.stableId)
        assertEquals(
            TEST_CONDOR_BRIDGE_A.displayName,
            recreatedRepository.selectedBridge.value?.displayNameSnapshot
        )
    }
}
