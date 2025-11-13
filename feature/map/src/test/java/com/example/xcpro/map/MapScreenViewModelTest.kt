package com.example.xcpro.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.dfcards.CardPreferences
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.common.waypoint.WaypointLoader
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.weather.wind.data.WindRepository
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.example.xcpro.map.domain.MapWaypointError
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.replay.IgcReplayController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.Mockito
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val taskManager = com.example.xcpro.tasks.TaskManagerCoordinator(context)
    private val cardPreferences = CardPreferences(context)
    private val mapStyleRepository = MapStyleRepository(context)
    private val unitsRepository = UnitsRepository(context)
    private val qnhPreferencesRepository = QnhPreferencesRepository(context)
    private val varioServiceManager = Mockito.mock(VarioServiceManager::class.java)
    private val flightDataRepository = FlightDataRepository()
    private val windRepository = Mockito.mock(WindRepository::class.java)
    private val replayController = Mockito.mock(IgcReplayController::class.java)
    private val replaySessionFlow = MutableStateFlow(IgcReplayController.SessionState())
    private val replayEventsFlow = MutableSharedFlow<IgcReplayController.ReplayEvent>()

    @After
    fun tearDown() {
        MapFeatureFlags.loadSavedTasksOnInit = true
        // Clean up DataStore artifacts created by the test run.
        context.filesDir.resolve("datastore")?.takeIf { it.exists() }?.deleteRecursively()
    }

    init {
        MapFeatureFlags.loadSavedTasksOnInit = false
        Mockito.`when`(replayController.session).thenReturn(replaySessionFlow)
        Mockito.`when`(replayController.events).thenReturn(replayEventsFlow)
    }

    @Ignore("GliderRepository + TaskManager persistence hangs under Robolectric until injected abstractions are provided")
    @Test
    fun refreshWaypoints_success_updatesState() = runTest(mainDispatcherRule.dispatcher) {
        val expected = listOf(
            WaypointData(
                name = "Test Field",
                code = "TEST",
                country = "NZ",
                latitude = -45.0,
                longitude = 170.0,
                elevation = "500m",
                style = 4,
                runwayDirection = "09/27",
                runwayLength = "1200m",
                frequency = "118.700",
                description = "Glider base"
            )
        )
        val loader = SuccessfulWaypointLoader(expected)

        val viewModel = MapScreenViewModel(
            appContext = context,
            taskManager = taskManager,
            cardPreferences = cardPreferences,
            mapStyleRepository = mapStyleRepository,
            unitsRepository = unitsRepository,
            qnhPreferencesRepository = qnhPreferencesRepository,
            waypointLoader = loader,
            gliderRepository = GliderRepository.getInstance(context),
            varioServiceManager = varioServiceManager,
            flightDataRepository = flightDataRepository,
            windRepository = windRepository,
            igcReplayController = replayController,
            defaultDispatcher = mainDispatcherRule.dispatcher
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(expected, state.waypoints)
        assertTrue(state.isLoadingWaypoints.not())
        assertNull(state.waypointError)
    }

    @Ignore("GliderRepository + TaskManager persistence hangs under Robolectric until injected abstractions are provided")
    @Test
    fun refreshWaypoints_failure_setsError() = runTest(mainDispatcherRule.dispatcher) {
        val loader = FailingWaypointLoader(IllegalStateException("Failed to read waypoints"))

        val viewModel = MapScreenViewModel(
            appContext = context,
            taskManager = taskManager,
            cardPreferences = cardPreferences,
            mapStyleRepository = mapStyleRepository,
            unitsRepository = unitsRepository,
            qnhPreferencesRepository = qnhPreferencesRepository,
            waypointLoader = loader,
            gliderRepository = GliderRepository.getInstance(context),
            varioServiceManager = varioServiceManager,
            flightDataRepository = flightDataRepository,
            windRepository = windRepository,
            igcReplayController = replayController,
            defaultDispatcher = mainDispatcherRule.dispatcher
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.waypoints.isEmpty())
        assertTrue(state.isLoadingWaypoints.not())
        val error = state.waypointError
        assertTrue(error is MapWaypointError.LoadFailed)
        assertEquals("Failed to read waypoints", (error as MapWaypointError.LoadFailed).recoveryHint)
    }

    private class SuccessfulWaypointLoader(
        private val waypoints: List<WaypointData>
    ) : WaypointLoader {
        override suspend fun load(context: Context): List<WaypointData> = waypoints
    }

    private class FailingWaypointLoader(
        private val throwable: Throwable
    ) : WaypointLoader {
        override suspend fun load(context: Context): List<WaypointData> = throw throwable
    }
}
