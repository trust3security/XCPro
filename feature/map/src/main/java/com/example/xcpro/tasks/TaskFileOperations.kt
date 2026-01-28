package com.example.xcpro.tasks

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.xcpro.tasks.core.TaskType
import com.google.gson.JsonParseException
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.TaskSheetViewModel

object TaskFileOperations {

    fun exportTaskToDownloads(context: Context, taskManager: TaskManagerCoordinator) {
        try {
            val task = taskManager.currentTask
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "task_$timestamp.cup"
            val jsonFileName = "task_$timestamp.xcp.json"

            val cupContent = taskToCUP(task, "Generated Task")
            val savedUri = saveToDownloads(context, fileName, cupContent)
            val jsonContent = TaskPersistSerializer.serialize(
                task = task,
                taskType = taskManager.taskType,
                targets = emptyList()
            )
            saveToDownloads(context, jsonFileName, jsonContent)
            if (savedUri != null) {
                Toast.makeText(context, "Task exported to Downloads/$fileName (+ $jsonFileName)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Export failed: Unable to create file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun showImportInfo(context: Context) {
        val message = """
            To import tasks:

            1. Copy .cup files to Downloads folder
            2. (Optional) Copy .xcp.json files for full-fidelity tasks (OZ + targets)
            3. Files will appear in the Files tab
            4. Tap a file to import it

            Supported formats:
             CUP format (.cup) - waypoints only
             XCPro JSON (.xcp.json) - preserves task type, OZ, targets
        """.trimIndent()

        android.app.AlertDialog.Builder(context)
            .setTitle("Import Tasks")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun taskToCUP(task: Task, taskName: String): String {
        val stringBuilder = StringBuilder()

        stringBuilder.appendLine("name,code,country,lat,lon,elev,style,rwdir,rwlen,freq,desc")

        task.waypoints.forEachIndexed { index, waypoint ->
            val name = waypoint.title.take(8)
            val code = String.format("%03d", index + 1)
            val country = "XX"
            val lat = formatLatitude(waypoint.lat)
            val lon = formatLongitude(waypoint.lon)
            val elev = "0.0m"
            val style = when (index) {
                0 -> "2"  // Start
                task.waypoints.lastIndex -> "3"  // Finish
                else -> "1"  // Turnpoint
            }
            val rwdir = "0"
            val rwlen = "0.0m"
            val freq = "0.0"
            val desc = waypoint.subtitle.take(20)

            stringBuilder.appendLine("$name,$code,$country,$lat,$lon,$elev,$style,$rwdir,$rwlen,$freq,$desc")
        }

        return stringBuilder.toString()
    }

    private fun formatLatitude(lat: Double): String {
        val degrees = kotlin.math.abs(lat).toInt()
        val minutes = (kotlin.math.abs(lat) - degrees) * 60.0
        val direction = if (lat >= 0) "N" else "S"
        return String.format("%02d%06.3f%s", degrees, minutes, direction)
    }

    private fun formatLongitude(lon: Double): String {
        val degrees = kotlin.math.abs(lon).toInt()
        val minutes = (kotlin.math.abs(lon) - degrees) * 60.0
        val direction = if (lon >= 0) "E" else "W"
        return String.format("%03d%06.3f%s", degrees, minutes, direction)
    }

    fun shareTask(context: Context, fileName: String) {
        val uri = findDownloadFileUri(context, fileName)
            ?: run {
                Toast.makeText(context, "File not found: $fileName", Toast.LENGTH_SHORT).show()
                return
            }
        shareTask(context, uri, fileName)
    }

    fun shareTask(context: Context, fileUri: Uri, displayName: String) {
        try {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = context.contentResolver.getType(fileUri) ?: "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_TEXT, "Sharing task file: $displayName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Task File"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app available to share files", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportTaskWithSharing(context: Context, taskManager: TaskManagerCoordinator) {
        try {
            val task = taskManager.currentTask
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "task_$timestamp.cup"
            val jsonFileName = "task_$timestamp.xcp.json"
            val cupContent = taskToCUP(task, "Generated Task")
            val jsonContent = TaskPersistSerializer.serialize(
                task = task,
                taskType = taskManager.taskType,
                targets = emptyList()
            )

            shareDirectly(context, fileName, cupContent, task, taskManager, mime = "application/octet-stream")
            shareDirectly(context, jsonFileName, jsonContent, task, taskManager, mime = "application/json")
        } catch (e: Exception) {
            Toast.makeText(context, "Export with sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToDownloads(context: Context, fileName: String, cupContent: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

            resolver.delete(
                collection,
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(fileName)
            )

            val pendingValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                val mime = if (fileName.endsWith(".json")) "application/json" else "application/octet-stream"
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val itemUri = resolver.insert(collection, pendingValues) ?: return null

            resolver.openOutputStream(itemUri)?.use { output ->
                output.write(cupContent.toByteArray())
            } ?: return null

            ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, this, null, null)
            }

            itemUri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { output ->
                output.write(cupContent.toByteArray())
            }
            Uri.fromFile(file)
        }
    }

    fun importTaskFile(
        context: Context,
        uri: Uri,
        taskManager: TaskManagerCoordinator,
        taskViewModel: TaskSheetViewModel
    ): Boolean {
        val name = queryDisplayName(context, uri) ?: return false
        return if (name.endsWith(".json", ignoreCase = true)) {
            importJsonTask(context, uri, taskManager, taskViewModel)
        } else {
            // Keep existing CUP behaviour minimal: delegate to task manager
            val ok = taskManager.loadTask(context, name)
            ok
        }
    }

    private fun importJsonTask(
        context: Context,
        uri: Uri,
        taskManager: TaskManagerCoordinator,
        taskViewModel: TaskSheetViewModel
    ): Boolean {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return false
            taskViewModel.importPersistedTask(json)
            true
        } catch (e: JsonParseException) {
            Toast.makeText(context, "Invalid task JSON: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        } catch (e: Exception) {
            Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    private fun shareDirectly(
        context: Context,
        fileName: String,
        content: String,
        task: Task,
        taskManager: TaskManagerCoordinator,
        mime: String
    ) {
        try {
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { output ->
                output.write(content.toByteArray())
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Task: ${task.waypoints.size} waypoints")
                putExtra(Intent.EXTRA_TEXT, buildString {
                    appendLine("Task Details:")
                    appendLine("Type: ${taskManager.taskType}")
                    appendLine("Waypoints: ${task.waypoints.size}")
                    appendLine()
                    task.waypoints.forEachIndexed { index, waypoint ->
                        appendLine("${index + 1}. ${waypoint.title}")
                        appendLine("   ${waypoint.lat}, ${waypoint.lon}")
                    }
                })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Task"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app available to share files", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Direct share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun findDownloadFileUri(context: Context, fileName: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Downloads._ID)
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(fileName),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val id = cursor.getLong(idColumn)
                    ContentUris.withAppendedId(collection, id)
                } else {
                    null
                }
            }
        } else {
            val legacyFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            if (legacyFile.exists()) {
                Uri.fromFile(legacyFile)
            } else {
                null
            }
        }
    }
}
