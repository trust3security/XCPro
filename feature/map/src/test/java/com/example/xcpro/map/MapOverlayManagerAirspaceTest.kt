package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.map.trail.SnailTrailManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MapOverlayManagerAirspaceTest {

    @Test
    fun refreshAirspace_secondRequestStartsLatestApplyCycle() = runTest {
        val airspaceUseCase: AirspaceUseCase = mock()
        val waypointFilesUseCase: WaypointFilesUseCase = mock()
        val document = DocumentRef(uri = "file:///tmp/a.txt", displayName = "a.txt")

        var loadCalls = 0
        whenever(airspaceUseCase.loadAirspaceFiles()).thenAnswer {
            runBlocking {
                loadCalls += 1
                if (loadCalls == 1) {
                    delay(1)
                }
                Pair(listOf(document), mutableMapOf("a.txt" to true))
            }
        }
        whenever(airspaceUseCase.loadSelectedClasses()).thenReturn(mutableMapOf("D" to true))
        whenever(waypointFilesUseCase.loadWaypointFiles()).thenReturn(Pair(emptyList(), mutableMapOf()))

        val mapStateStore = MapStateStore(initialStyleName = "Terrain")
        val manager = MapOverlayManager(
            context = mock<Context>(),
            mapState = MapScreenState(),
            mapStateReader = mapStateStore,
            taskRenderSyncCoordinator = mock<TaskRenderSyncCoordinator>(),
            taskWaypointCountProvider = { 0 },
            stateActions = MapStateActionsDelegate(mapStateStore),
            snailTrailManager = mock<SnailTrailManager>(),
            coroutineScope = this,
            airspaceUseCase = airspaceUseCase,
            waypointFilesUseCase = waypointFilesUseCase
        )

        manager.refreshAirspace(map = null)
        runCurrent()
        manager.refreshAirspace(map = null)
        advanceUntilIdle()

        verify(airspaceUseCase, times(2)).loadAirspaceFiles()
        verify(airspaceUseCase, times(2)).loadSelectedClasses()
        verify(airspaceUseCase, times(0)).buildGeoJson(any(), any())
    }
}
