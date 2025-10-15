package com.example.xcpro.navdrawer

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

private const val TAG = "DrawerConfig"

/**
 * Save navigation drawer expansion states to configuration file
 */
fun saveNavDrawerConfig(
    context: Context,
    profileExpanded: Boolean,
    mapStyleExpanded: Boolean,
    settingsExpanded: Boolean
) {
    try {
        val file = File(context.filesDir, "configuration.json")
        val jsonObject = if (file.exists()) {
            JSONObject(file.readText())
        } else {
            JSONObject()
        }
        val navDrawerObject = jsonObject.optJSONObject("navDrawer") ?: JSONObject()
        navDrawerObject.put("profileExpanded", profileExpanded)
        navDrawerObject.put("mapStyleExpanded", mapStyleExpanded)
        navDrawerObject.put("settingsExpanded", settingsExpanded)
        jsonObject.put("navDrawer", navDrawerObject)
        file.writeText(jsonObject.toString(2))
        Log.d(TAG, "Saved nav drawer config: profileExpanded=$profileExpanded, mapStyleExpanded=$mapStyleExpanded, settingsExpanded=$settingsExpanded")
    } catch (e: Exception) {
        Log.e(TAG, "Error saving nav drawer config to configuration.json: ${e.message}")
    }
}

/**
 * Load configuration from JSON file
 */
fun loadConfig(context: Context): JSONObject? {
    return try {
        val file = File(context.filesDir, "configuration.json")
        if (file.exists()) {
            val jsonString = file.readText()
            JSONObject(jsonString)
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading config from configuration.json: ${e.message}")
        null
    }
}
