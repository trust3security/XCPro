package com.example.xcpro.navdrawer

import android.content.Context
import android.util.Log
import com.example.xcpro.ConfigurationRepository
import org.json.JSONObject

private const val TAG = "DrawerConfig"

/**
 * Save navigation drawer expansion states to configuration file
 */
suspend fun saveNavDrawerConfig(
    context: Context,
    profileExpanded: Boolean,
    mapStyleExpanded: Boolean,
    settingsExpanded: Boolean
) {
    try {
        ConfigurationRepository(context).saveNavDrawerConfig(
            profileExpanded = profileExpanded,
            mapStyleExpanded = mapStyleExpanded,
            settingsExpanded = settingsExpanded
        )
        Log.d(TAG, "Saved nav drawer config: profileExpanded=$profileExpanded, mapStyleExpanded=$mapStyleExpanded, settingsExpanded=$settingsExpanded")
    } catch (e: Exception) {
        Log.e(TAG, "Error saving nav drawer config to configuration.json: ${e.message}")
    }
}

/**
 * Load configuration from JSON file
 */
suspend fun loadConfig(context: Context): JSONObject? {
    return try {
        ConfigurationRepository(context).readConfig()
    } catch (e: Exception) {
        Log.e(TAG, "Error loading config from configuration.json: ${e.message}")
        null
    }
}
