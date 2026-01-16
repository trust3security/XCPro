package com.example.ui1.screens.flightmgmt

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.example.ui1.screens.AirspaceClassItem
import com.example.ui1.screens.FileItem
import com.example.xcpro.AirspaceRepository
import com.example.xcpro.saveSelectedClasses
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val DEFAULT_ZONE_COUNT = 0

suspend fun buildAirspaceFileItems(
    context: Context,
    files: List<Uri>,
    checkedStates: Map<String, Boolean>
): List<FileItem> = withContext(Dispatchers.IO) {
    files.map { uri ->
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown"
        val enabled = checkedStates[fileName] ?: false
        val count = countAirspaceZones(context, uri)
        FileItem(
            name = fileName,
            enabled = enabled,
            count = count,
            status = if (enabled) "Loaded" else "Disabled",
            uri = uri
        )
    }
}

suspend fun buildAirspaceClassItems(
    context: Context,
    enabledFiles: List<Uri>,
    classStates: Map<String, Boolean>
): List<AirspaceClassItem> {
    if (enabledFiles.isEmpty()) return emptyList()
    val repository = AirspaceRepository(context)
    val classes = repository.parseClasses(enabledFiles)
    return classes.map { className ->
        AirspaceClassItem(
            className = className,
            enabled = classStates[className] ?: true,
            color = airspaceClassColor(className),
            description = airspaceClassDescription(className)
        )
    }
}

fun refreshAvailableAirspaceClasses(
    context: Context,
    selectedFiles: List<Uri>,
    checkedStates: Map<String, Boolean>,
    classStates: SnapshotStateMap<String, Boolean>,
    scope: CoroutineScope,
    persist: Boolean = true
) {
    scope.launch(Dispatchers.IO) {
        val repository = AirspaceRepository(context)
        val enabledFiles = selectedFiles.filter { uri ->
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: return@filter false
            checkedStates[fileName] == true
        }
        val latestClasses = if (enabledFiles.isEmpty()) emptySet() else repository.parseClasses(enabledFiles).toSet()

        var shouldSave = false
        var snapshot: Map<String, Boolean> = emptyMap()
        withContext(Dispatchers.Main) {
            val existing = classStates.keys.toSet()
            val obsolete = existing - latestClasses
            if (obsolete.isNotEmpty()) {
                obsolete.forEach { classStates.remove(it) }
                shouldSave = true
            }
            latestClasses.forEach { className ->
                if (!classStates.containsKey(className)) {
                    classStates[className] = true
                    shouldSave = true
                }
            }
            snapshot = classStates.toMap()
        }

        if (shouldSave && persist) {
            saveSelectedClasses(context, snapshot)
        }
    }
}

private fun countAirspaceZones(context: Context, uri: Uri): Int {
    val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: return DEFAULT_ZONE_COUNT
    val file = File(context.filesDir, fileName)
    if (!file.exists()) return DEFAULT_ZONE_COUNT
    return runCatching {
        file.useLines { lines -> lines.count { it.trimStart().startsWith("AC ") } }
    }.getOrDefault(DEFAULT_ZONE_COUNT)
}

private fun airspaceClassColor(className: String): String = when (className.uppercase()) {
    "A" -> "#FF0000" // IFR only
    "B" -> "#0000FF"
    "C" -> "#FF00FF"
    "D" -> "#0000FF"
    "E" -> "#FF00FF"
    "F" -> "#808080"
    "G" -> "#FFFFFF"
    "CTR" -> "#0000FF"
    "RMZ" -> "#00FF00"
    "RESTRICTED" -> "#FF0000"
    "DANGER" -> "#FFA500"
    else -> "#8E8E93"
}

private fun airspaceClassDescription(className: String): String = when (className.uppercase()) {
    "A" -> "IFR only"
    "B" -> "IFR and VFR"
    "C" -> "IFR and VFR with clearance"
    "D" -> "Radio communication required"
    "E" -> "IFR controlled, VFR not"
    "F" -> "Advisory airspace"
    "G" -> "Uncontrolled airspace"
    "CTR" -> "Control Zone"
    "RMZ" -> "Radio Mandatory Zone"
    "RESTRICTED" -> "Restricted area"
    "DANGER" -> "Danger area"
    else -> "Unknown class"
}
