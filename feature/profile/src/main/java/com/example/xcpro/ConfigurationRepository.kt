package com.example.xcpro

import android.content.Context
import android.net.Uri
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private const val CONFIG_FILE_NAME = "configuration.json"
private const val DEFAULT_PROFILE_ID = "default-profile"
private const val LEGACY_DEFAULT_ALIAS = "default"
private const val LEGACY_DF_ALIAS = "__default_profile__"
private const val KEY_PROFILES = "profiles"
private const val KEY_SELECTED_FILES = "selected_files"

@Singleton
class ConfigurationRepository @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val appContext = context.applicationContext
    private val configFile = File(appContext.filesDir, CONFIG_FILE_NAME)
    private val writeMutex = Mutex()

    suspend fun readConfig(): JSONObject? = withContext(ioDispatcher) {
        readConfigInternal()
    }

    fun getCachedConfig(): JSONObject? = synchronized(cacheLock) {
        cachedConfigJson?.let { JSONObject(it) }
    }

    suspend fun updateConfig(update: (JSONObject) -> Unit): JSONObject = withContext(ioDispatcher) {
        writeMutex.withLock {
            val json = readConfigInternal() ?: JSONObject()
            update(json)
            writeConfigInternal(json)
            json
        }
    }

    suspend fun saveNavDrawerConfig(
        profileExpanded: Boolean,
        mapStyleExpanded: Boolean,
        settingsExpanded: Boolean
    ) {
        updateConfig { json ->
            val navDrawerObject = json.optJSONObject("navDrawer") ?: JSONObject()
            navDrawerObject.put("profileExpanded", profileExpanded)
            navDrawerObject.put("mapStyleExpanded", mapStyleExpanded)
            navDrawerObject.put("settingsExpanded", settingsExpanded)
            json.put("navDrawer", navDrawerObject)
        }
    }

    suspend fun loadWaypointFiles(): Pair<List<DocumentRef>, MutableMap<String, Boolean>> =
        withContext(ioDispatcher) {
            val json = readConfigInternal() ?: return@withContext Pair(emptyList(), mutableMapOf())
            val waypointFiles = json.optJSONObject("waypoint_files")
                ?.optJSONObject("selected_files")
                ?: return@withContext Pair(emptyList(), mutableMapOf())

            val files = mutableListOf<DocumentRef>()
            val checkedStates = mutableMapOf<String, Boolean>()
            waypointFiles.keys().forEach { fileName ->
                val file = File(appContext.filesDir, fileName)
                if (file.exists()) {
                    files.add(DocumentRef(uri = Uri.fromFile(file).toString(), displayName = fileName))
                    checkedStates[fileName] = waypointFiles.getBoolean(fileName)
                }
            }
            Pair(files, checkedStates)
        }

    suspend fun saveWaypointFiles(files: List<DocumentRef>, checkedStates: Map<String, Boolean>) {
        updateConfig { json ->
            val waypointFiles = JSONObject()
            val filesArray = JSONObject()
            files.forEach { document ->
                val fileName = document.fileName()
                filesArray.put(fileName, checkedStates[fileName] ?: false)
            }
            waypointFiles.put("selected_files", filesArray)
            json.put("waypoint_files", waypointFiles)
        }
    }

    suspend fun saveSelectedTemplates(selectedTemplates: Map<String, Boolean>) {
        updateConfig { json ->
            val templatesJson = JSONObject()
            selectedTemplates.forEach { (key, value) -> templatesJson.put(key, value) }
            json.put("selected_templates", templatesJson)
        }
    }

    suspend fun loadSelectedTemplates(): MutableMap<String, Boolean>? = withContext(ioDispatcher) {
        val json = readConfigInternal() ?: return@withContext null
        val templatesJson = json.optJSONObject("selected_templates") ?: return@withContext null
        val templates = mutableMapOf<String, Boolean>()
        templatesJson.keys().forEach { key ->
            templates[key] = templatesJson.getBoolean(key)
        }
        templates
    }

    private fun readConfigInternal(): JSONObject? {
        if (!configFile.exists()) {
            synchronized(cacheLock) {
                cachedConfigJson = null
                cachedLastModified = -1L
            }
            return null
        }
        val lastModified = configFile.lastModified()
        val cached = synchronized(cacheLock) {
            if (cachedLastModified == lastModified) cachedConfigJson else null
        }
        if (cached != null) return JSONObject(cached)
        val jsonString = configFile.readText()
        synchronized(cacheLock) {
            cachedConfigJson = jsonString
            cachedLastModified = lastModified
        }
        return JSONObject(jsonString)
    }

    private fun writeConfigInternal(json: JSONObject) {
        val jsonString = json.toString(2)
        configFile.writeText(jsonString)
        val lastModified = configFile.lastModified()
        synchronized(cacheLock) {
            cachedConfigJson = jsonString
            cachedLastModified = lastModified
        }
    }

    private companion object {
        private val cacheLock = Any()
        private var cachedLastModified: Long = -1L
        private var cachedConfigJson: String? = null
    }
}
