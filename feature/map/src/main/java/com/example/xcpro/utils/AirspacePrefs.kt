package com.example.xcpro

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.io.File

private const val AIRSPACE_TAG = "AirspacePrefs"

fun saveSelectedClasses(context: Context, selectedClasses: Map<String, Boolean>) {
    try {
        val file = File(context.filesDir, "configuration.json")
        val json = if (file.exists()) JSONObject(file.readText()) else JSONObject()
        val airspaceJson = json.optJSONObject("airspace") ?: JSONObject()
        val selectedClassesJson = JSONObject()
        selectedClasses.forEach { (key, value) -> selectedClassesJson.put(key, value) }
        airspaceJson.put("selectedClasses", selectedClassesJson)
        json.put("airspace", airspaceJson)
        file.writeText(json.toString(2))
        Log.d(AIRSPACE_TAG, "Saved selected classes: $selectedClasses")
    } catch (e: Exception) {
        Log.e(AIRSPACE_TAG, "Error saving selected classes: ${e.message}")
    }
}

fun loadSelectedClasses(context: Context): MutableMap<String, Boolean>? {
    return try {
        val file = File(context.filesDir, "configuration.json")
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        val selectedClassesJson = json.optJSONObject("airspace")?.optJSONObject("selectedClasses")
            ?: return null
        val selectedClasses = mutableMapOf<String, Boolean>()
        selectedClassesJson.keys().forEach { key ->
            selectedClasses[key] = selectedClassesJson.getBoolean(key)
        }
        selectedClasses
    } catch (e: Exception) {
        Log.e(AIRSPACE_TAG, "Error loading selected classes: ${e.message}")
        null
    }
}

fun loadAirspaceFiles(context: Context): Pair<List<Uri>, MutableMap<String, Boolean>> {
    try {
        val file = File(context.filesDir, "configuration.json")
        if (!file.exists()) return Pair(emptyList(), mutableMapOf())
        val json = JSONObject(file.readText())
        val airspaceFiles = json.optJSONObject("airspace_files")?.optJSONObject("selected_files")
            ?: return Pair(emptyList(), mutableMapOf())
        val files = mutableListOf<Uri>()
        val checkedStates = mutableMapOf<String, Boolean>()
        airspaceFiles.keys().forEach { fileName ->
            val f = File(context.filesDir, fileName)
            if (f.exists()) {
                files.add(Uri.fromFile(f))
                checkedStates[fileName] = airspaceFiles.getBoolean(fileName)
            }
        }
        return Pair(files, checkedStates)
    } catch (e: Exception) {
        Log.e(AIRSPACE_TAG, "Error loading airspace files: ${e.message}")
        return Pair(emptyList(), mutableMapOf())
    }
}
