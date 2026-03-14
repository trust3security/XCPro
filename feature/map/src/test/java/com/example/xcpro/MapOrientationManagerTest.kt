package com.example.xcpro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.common.orientation.OrientationFlightDataSnapshot
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationSensorData
import com.example.xcpro.orientation.OrientationClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class MapOrientationManagerTest {
    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        clearPrefs()
    }

    @After
    fun tearDown() {
        clearPrefs()
    }

    @Test
    fun userOverrideFreezesUntilTimeout() = runTest {
        val settingsRepository = MapOrientationSettingsRepository(appContext)
        val fakeSource = FakeOrientationSensorSource()
        val factory = Mockito.mock(OrientationDataSourceFactory::class.java)
        val clock = FakeOrientationClock()

        val manager = MapOrientationManager(
            scope = backgroundScope,
            orientationDataSourceFactory = factory,
            settingsRepository = settingsRepository,
            clock = clock,
            orientationDataSourceOverride = fakeSource
        )

        clock.monoMs = 1_000L
        clock.wallMs = 2_000L
        fakeSource.emit(
            OrientationSensorData(
                track = 90.0,
                groundSpeed = 5.0,
                isGPSValid = true
            )
        )
        advanceTimeBy(100L)
        advanceUntilIdle()

        assertEquals(90.0, manager.orientationFlow.value.bearing, 1e-6)

        manager.onUserInteraction()
        advanceUntilIdle()

        clock.monoMs = 2_000L
        clock.wallMs = 3_000L
        fakeSource.emit(
            OrientationSensorData(
                track = 180.0,
                groundSpeed = 6.0,
                isGPSValid = true
            )
        )
        advanceTimeBy(100L)
        advanceUntilIdle()

        assertEquals(90.0, manager.orientationFlow.value.bearing, 1e-6)

        clock.monoMs = 12_500L
        clock.wallMs = 13_500L
        fakeSource.emit(
            OrientationSensorData(
                track = 180.0,
                groundSpeed = 5.0,
                isGPSValid = true
            )
        )
        advanceTimeBy(100L)
        advanceUntilIdle()

        assertEquals(180.0, manager.orientationFlow.value.bearing, 1e-6)
        manager.stop()
        advanceUntilIdle()
    }

    @Test
    fun settingsChangeRecomputesOrientation() = runTest {
        val settingsRepository = MapOrientationSettingsRepository(appContext)
        val fakeSource = FakeOrientationSensorSource()
        val factory = Mockito.mock(OrientationDataSourceFactory::class.java)
        val clock = FakeOrientationClock()

        val manager = MapOrientationManager(
            scope = backgroundScope,
            orientationDataSourceFactory = factory,
            settingsRepository = settingsRepository,
            clock = clock,
            orientationDataSourceOverride = fakeSource
        )

        clock.monoMs = 1_000L
        clock.wallMs = 2_000L
        fakeSource.emit(
            OrientationSensorData(
                track = 45.0,
                groundSpeed = 5.0,
                isGPSValid = true
            )
        )
        advanceTimeBy(100L)
        advanceUntilIdle()

        assertEquals(45.0, manager.orientationFlow.value.bearing, 1e-6)
        assertEquals(MapOrientationMode.TRACK_UP, manager.orientationFlow.value.mode)

        settingsRepository.setCruiseOrientationMode(MapOrientationMode.NORTH_UP)
        val updated = withTimeout(1_000L) {
            manager.orientationFlow.first { it.mode == MapOrientationMode.NORTH_UP }
        }

        assertEquals(0.0, updated.bearing, 1e-6)
        assertEquals(MapOrientationMode.NORTH_UP, updated.mode)
        manager.stop()
        advanceUntilIdle()
    }

    private fun clearPrefs() {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private class FakeOrientationSensorSource(
        initial: OrientationSensorData = OrientationSensorData()
    ) : OrientationSensorSource {
        private val _flow = MutableStateFlow(initial)
        override val orientationFlow: StateFlow<OrientationSensorData> = _flow.asStateFlow()
        var lastMinSpeedThreshold: Double = 0.0

        override fun getCurrentData(): OrientationSensorData = _flow.value

        override fun updateFromFlightData(flightData: OrientationFlightDataSnapshot) {
            // No-op for tests
        }

        override fun updateMinSpeedThreshold(thresholdMs: Double) {
            lastMinSpeedThreshold = thresholdMs
        }

        override fun start() {
            // No-op for tests
        }

        override fun stop() {
            // No-op for tests
        }

        fun emit(data: OrientationSensorData) {
            _flow.value = data
        }
    }

    private class FakeOrientationClock : OrientationClock {
        var monoMs: Long = 0L
        var wallMs: Long = 0L

        override fun nowMonoMs(): Long = monoMs

        override fun nowWallMs(): Long = wallMs
    }

    private companion object {
        private const val PREFS_NAME = "map_orientation_prefs"
    }
}
