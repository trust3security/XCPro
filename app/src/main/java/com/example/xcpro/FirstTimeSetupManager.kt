package com.example.xcpro

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId

@Singleton
class FirstTimeSetupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock
) {

    companion object {
        private const val TAG = "FirstTimeSetup"
        private const val PREFS_NAME = "first_time_setup"
        private const val KEY_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_SETUP_VERSION = "setup_version"
        private const val CURRENT_SETUP_VERSION = 1
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun isFirstLaunch(): Boolean = withContext(ioDispatcher) {
        val isFirst = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        val setupVersion = prefs.getInt(KEY_SETUP_VERSION, 0)
        isFirst || setupVersion < CURRENT_SETUP_VERSION
    }

    /**
     * Runs setup work only once per version. Safe to call repeatedly.
     */
    suspend fun runIfNeeded() = withContext(ioDispatcher) {
        if (!isFirstLaunch()) {
            Log.d(TAG, "Not first launch, skipping setup")
            return@withContext
        }

        Log.i(TAG, "Performing first-time setup...")
        try {
            clearPreviousCache()
            setupDefaultNavigationDrawer()
            setupDefaultMapSettings()
            markSetupComplete()
            Log.i(TAG, "First-time setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during first-time setup", e)
        }
    }

    private suspend fun clearPreviousCache() = withContext(ioDispatcher) {
        Log.d(TAG, "Clearing previous cache...")
        val prefsToClean = listOf(
            "drawer_config_prefs"
        )

        prefsToClean.forEach { prefName ->
            runCatching {
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()
            }.onFailure { e ->
                Log.w(TAG, "Could not clear $prefName", e)
            }
        }
    }

    private suspend fun setupDefaultNavigationDrawer() = withContext(ioDispatcher) {
        Log.d(TAG, "Setting up default navigation drawer...")
        val configFile = File(context.filesDir, "configuration.json")
        val jsonObject = JSONObject().apply {
            put("navDrawer", JSONObject().apply {
                put("profileExpanded", true)
                put("mapStyleExpanded", false)
                put("settingsExpanded", false)
            })
            put("drawer", JSONObject().apply {
                put("defaultWidth", 280)
                put("gestureEnabled", true)
                put("swipeEnabled", true)
            })
        }
        configFile.writeText(jsonObject.toString(2))
    }

    private suspend fun setupDefaultMapSettings() = withContext(ioDispatcher) {
        Log.d(TAG, "Setting up default map settings...")
        val mapPrefs = context.getSharedPreferences("MapScreenPrefs", Context.MODE_PRIVATE)
        mapPrefs.edit().apply {
            putString("map_style", "Topo")
            putFloat("default_zoom", 10f)
            putFloat("min_zoom", 3f)
            putFloat("max_zoom", 18f)
            putFloat("default_lat", 20.0f)
            putFloat("default_lon", 0.0f)
            apply()
        }
    }

    private suspend fun markSetupComplete() = withContext(ioDispatcher) {
        prefs.edit().apply {
            putBoolean(KEY_FIRST_LAUNCH, false)
            putInt(KEY_SETUP_VERSION, CURRENT_SETUP_VERSION)
            putLong("setup_timestamp", clock.nowWallMs())
            apply()
        }
    }

    suspend fun resetToFirstLaunch() = withContext(ioDispatcher) {
        Log.i(TAG, "Resetting to first launch state")
        prefs.edit().clear().apply()
    }

    suspend fun getSetupInfo(): String = withContext(ioDispatcher) {
        val isFirst = isFirstLaunch()
        val version = prefs.getInt(KEY_SETUP_VERSION, 0)
        val timestamp = prefs.getLong("setup_timestamp", 0)
        val lastSetup = if (timestamp > 0) {
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toString()
        } else {
            "Never"
        }
        "First Launch: $isFirst, Setup Version: $version, Last Setup: $lastSetup"
    }
}
