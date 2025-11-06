package com.example.xcpro.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.dfcards.CardPreferences
import com.example.xcpro.WaypointData
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    @After
    fun tearDown() {
        // Clean up DataStore artifacts created by the test run.
        context.filesDir.resolve("datastore")?.takeIf { it.exists() }?.deleteRecursively()
    }

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
            injectedTaskManager = taskManager,
            injectedCardPreferences = cardPreferences,
            mapStyleRepository = mapStyleRepository,
            unitsRepository = unitsRepository,
            qnhPreferencesRepository = qnhPreferencesRepository,
            waypointLoader = loader
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(expected, state.waypoints)
        assertTrue(state.isLoadingWaypoints.not())
        assertNull(state.waypointError)
    }

    @Test
    fun refreshWaypoints_failure_setsError() = runTest(mainDispatcherRule.dispatcher) {
        val loader = FailingWaypointLoader(IllegalStateException("Failed to read waypoints"))

        val viewModel = MapScreenViewModel(
            appContext = context,
            injectedTaskManager = taskManager,
            injectedCardPreferences = cardPreferences,
            mapStyleRepository = mapStyleRepository,
            unitsRepository = unitsRepository,
            qnhPreferencesRepository = qnhPreferencesRepository,
            waypointLoader = loader
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.waypoints.isEmpty())
        assertTrue(state.isLoadingWaypoints.not())
        assertEquals("Failed to read waypoints", state.waypointError)
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
