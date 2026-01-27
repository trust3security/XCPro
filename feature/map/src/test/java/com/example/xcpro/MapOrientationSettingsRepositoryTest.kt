package com.example.xcpro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.units.UnitsConverter
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

    private fun clearPrefs() {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    companion object {
        private const val PREFS_NAME = "map_orientation_prefs"
    }
}