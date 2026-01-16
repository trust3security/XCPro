package com.example.xcpro.map.trail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MapTrailPreferencesTest {

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
    fun defaultsMatchXcsoar() {
        val subject = MapTrailPreferences(appContext)

        assertEquals(
            TrailSettings(
                length = TrailLength.LONG,
                type = TrailType.VARIO_1,
                windDriftEnabled = true,
                scalingEnabled = true
            ),
            subject.getSettings()
        )
    }

    @Test
    fun setSettings_persistsValues() {
        val subject = MapTrailPreferences(appContext)
        val expected = TrailSettings(
            length = TrailLength.SHORT,
            type = TrailType.VARIO_2_DOTS,
            windDriftEnabled = false,
            scalingEnabled = false
        )

        subject.setSettings(expected)

        assertEquals(expected, subject.getSettings())
    }

    private fun clearPrefs() {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    companion object {
        private const val PREFS_NAME = "map_trail_prefs"
    }
}
