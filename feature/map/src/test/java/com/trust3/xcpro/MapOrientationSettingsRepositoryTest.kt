package com.trust3.xcpro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.common.orientation.MapOrientationMode
import com.trust3.xcpro.common.units.UnitsConverter
import com.trust3.xcpro.map.domain.MapShiftBiasMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MapOrientationSettingsRepositoryTest {
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
    fun updatesCruiseAndCirclingModes() {
        val repository = MapOrientationSettingsRepository(appContext)

        repository.setCruiseOrientationMode(MapOrientationMode.NORTH_UP)
        repository.setCirclingOrientationMode(MapOrientationMode.HEADING_UP)

        val settings = repository.settingsFlow.value
        assertEquals(MapOrientationMode.NORTH_UP, settings.cruiseMode)
        assertEquals(MapOrientationMode.HEADING_UP, settings.circlingMode)
    }

    @Test
    fun minSpeedThresholdUsesMetersPerSecond() {
        val repository = MapOrientationSettingsRepository(appContext)

        repository.setMinSpeedThresholdKnots(4.0)

        val settings = repository.settingsFlow.value
        assertEquals(UnitsConverter.knotsToMs(4.0), settings.minSpeedThresholdMs, 1e-3)
    }

    @Test
    fun switchingActiveProfileSwapsScopedOrientationSettings() {
        val repository = MapOrientationSettingsRepository(appContext)

        repository.writeProfileSettings(
            "sailplane-a",
            MapOrientationSettings(
                cruiseMode = MapOrientationMode.NORTH_UP,
                circlingMode = MapOrientationMode.HEADING_UP,
                minSpeedThresholdMs = 3.0,
                gliderScreenPercent = 20,
                mapShiftBiasMode = MapShiftBiasMode.TRACK,
                mapShiftBiasStrength = 0.4
            )
        )
        repository.writeProfileSettings(
            "hangglider-b",
            MapOrientationSettings(
                cruiseMode = MapOrientationMode.TRACK_UP,
                circlingMode = MapOrientationMode.NORTH_UP,
                minSpeedThresholdMs = 5.0,
                gliderScreenPercent = 45,
                mapShiftBiasMode = MapShiftBiasMode.NONE,
                mapShiftBiasStrength = 0.0
            )
        )

        repository.setActiveProfileId("sailplane-a")
        assertEquals(MapOrientationMode.NORTH_UP, repository.settingsFlow.value.cruiseMode)
        assertEquals(20, repository.settingsFlow.value.gliderScreenPercent)
        assertEquals(MapShiftBiasMode.TRACK, repository.settingsFlow.value.mapShiftBiasMode)

        repository.setActiveProfileId("hangglider-b")
        assertEquals(MapOrientationMode.TRACK_UP, repository.settingsFlow.value.cruiseMode)
        assertEquals(45, repository.settingsFlow.value.gliderScreenPercent)
        assertEquals(MapShiftBiasMode.NONE, repository.settingsFlow.value.mapShiftBiasMode)
    }

    private fun clearPrefs() {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    companion object {
        private const val PREFS_NAME = "map_orientation_prefs"
    }
}
