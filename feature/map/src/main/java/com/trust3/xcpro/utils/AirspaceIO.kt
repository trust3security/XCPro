package com.trust3.xcpro

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.trust3.xcpro.core.time.TimeBridge
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val AIRSPACE_IO_TAG = "AirspaceIO"

fun copyFileToInternalStorage(context: Context, uri: Uri): String {
    val contentResolver = context.contentResolver
    val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    } ?: "file_${TimeBridge.nowWallMs()}.txt"

    val outputFile = File(context.filesDir, fileName)
    contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(outputFile).use { output ->
            input.copyTo(output)
        }
    } ?: throw IOException("Failed to open input stream for URI: $uri")

    return fileName
}

fun saveAirspaceFiles(context: Context, files: List<Uri>, checkedStates: Map<String, Boolean>) {
    try {
        val file = File(context.filesDir, "configuration.json")
        val json = if (file.exists()) JSONObject(file.readText()) else JSONObject()
        val airspaceFiles = JSONObject()
        val filesArray = JSONObject()
        files.forEach { uri ->
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: return@forEach
            filesArray.put(fileName, checkedStates[fileName] ?: false)
        }
        airspaceFiles.put("selected_files", filesArray)
        json.put("airspace_files", airspaceFiles)
        file.writeText(json.toString(2))
        Log.d(AIRSPACE_IO_TAG, "Saved airspace files selection: $checkedStates")
    } catch (e: Exception) {
        Log.e(AIRSPACE_IO_TAG, "Error saving airspace files: ${e.message}")
    }
}
