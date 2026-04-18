package com.trust3.xcpro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.core.time.FakeClock
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class FirstTimeSetupManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val configFile = File(context.filesDir, "configuration.json")

    @Before
    fun setUp() {
        clearState()
    }

    @After
    fun tearDown() {
        clearState()
    }

    @Test
    fun runIfNeeded_upgradeClearsLegacyMapScreenPrefs_andDoesNotRecreateThem() = runTest {
        val setupPrefs = context.getSharedPreferences("first_time_setup", Context.MODE_PRIVATE)
        setupPrefs.edit()
            .putBoolean("is_first_launch", false)
            .putInt("setup_version", 1)
            .commit()

        val legacyMapPrefs = context.getSharedPreferences("MapScreenPrefs", Context.MODE_PRIVATE)
        legacyMapPrefs.edit()
            .putString("map_style", "Topo")
            .putFloat("default_zoom", 10f)
            .putFloat("min_zoom", 3f)
            .putFloat("max_zoom", 18f)
            .putFloat("default_lat", 20.0f)
            .putFloat("default_lon", 0.0f)
            .commit()

        val manager = FirstTimeSetupManager(
            context = context,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            clock = FakeClock(wallMs = 1_234L)
        )

        manager.runIfNeeded()

        val updatedSetupPrefs = context.getSharedPreferences("first_time_setup", Context.MODE_PRIVATE)
        assertFalse(updatedSetupPrefs.getBoolean("is_first_launch", true))
        assertEquals(2, updatedSetupPrefs.getInt("setup_version", 0))
        assertEquals(1_234L, updatedSetupPrefs.getLong("setup_timestamp", 0L))

        assertTrue(configFile.exists())
        val configJson = JSONObject(configFile.readText())
        val navDrawer = configJson.getJSONObject("navDrawer")
        assertTrue(navDrawer.getBoolean("profileExpanded"))
        assertFalse(navDrawer.getBoolean("mapStyleExpanded"))
        assertFalse(navDrawer.getBoolean("settingsExpanded"))

        val updatedLegacyMapPrefs = context.getSharedPreferences("MapScreenPrefs", Context.MODE_PRIVATE)
        assertTrue(updatedLegacyMapPrefs.all.isEmpty())
        assertFalse(updatedLegacyMapPrefs.contains("map_style"))
    }

    private fun clearState() {
        listOf("first_time_setup", "MapScreenPrefs", "drawer_config_prefs").forEach { prefsName ->
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
        configFile.delete()
    }
}
