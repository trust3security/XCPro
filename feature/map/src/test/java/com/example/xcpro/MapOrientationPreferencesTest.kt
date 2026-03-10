package com.example.xcpro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class MapOrientationPreferencesTest {

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
    fun migratesLegacyKnotsValueToMetersPerSecond() {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_MIN_SPEED_THRESHOLD, 3.9f)
            .apply()

        val subject = MapOrientationPreferences(appContext)

        val expectedMs = UnitsConverter.knotsToMs(3.9)
        assertEquals(expectedMs, subject.getMinSpeedThreshold(), 1e-3)
    }

    @Test
    fun defaultThresholdMatchesTwoMetersPerSecond() {
        val subject = MapOrientationPreferences(appContext)
        assertEquals(2.0, subject.getMinSpeedThreshold(), 1e-3)
    }

    @Test
    fun storesNewValuesInMetersPerSecond() {
        val subject = MapOrientationPreferences(appContext)
        subject.setMinSpeedThreshold(4.0)

        val stored =
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_DEFAULT_PROFILE_MIN_SPEED_THRESHOLD, 0f)
                .toDouble()

        assertEquals(UnitsConverter.knotsToMs(4.0), stored, 1e-3)
    }

    @Test
    fun activeProfileId_scopesWritesAndReads() {
        val subject = MapOrientationPreferences(appContext)

        subject.setActiveProfileId("profile-a")
        subject.setGliderScreenPercent(18)
        subject.setMapShiftBiasStrength(0.25)

        subject.setActiveProfileId("profile-b")
        subject.setGliderScreenPercent(42)
        subject.setMapShiftBiasStrength(0.75)

        subject.setActiveProfileId("profile-a")
        assertEquals(18, subject.getGliderScreenPercent())
        assertEquals(0.25, subject.getMapShiftBiasStrength(), 1e-6)

        subject.setActiveProfileId("profile-b")
        assertEquals(42, subject.getGliderScreenPercent())
        assertEquals(0.75, subject.getMapShiftBiasStrength(), 1e-6)
    }

    @Test
    fun bumpsLegacyDefaultToTwoMetersPerSecond() {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_MIN_SPEED_THRESHOLD, UnitsConverter.knotsToMs(2.0).toFloat())
            .putBoolean(KEY_MIN_SPEED_IS_MS, true)
            .apply()

        val subject = MapOrientationPreferences(appContext)

        assertEquals(2.0, subject.getMinSpeedThreshold(), 1e-3)
    }

    private fun clearPrefs() {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    companion object {
        private const val PREFS_NAME = "map_orientation_prefs"
        private const val KEY_MIN_SPEED_THRESHOLD = "min_speed_threshold_kt"
        private const val KEY_MIN_SPEED_IS_MS = "min_speed_threshold_is_ms"
        private const val KEY_DEFAULT_PROFILE_MIN_SPEED_THRESHOLD =
            "profile_default-profile_min_speed_threshold_kt"
    }
}
