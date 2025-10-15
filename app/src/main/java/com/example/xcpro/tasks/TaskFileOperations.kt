package com.example.xcpro.tasks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object TaskFileOperations {

    fun exportTaskToDownloads(context: Context, taskManager: TaskManagerCoordinator) {
        try {
            val task = taskManager.currentTask
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "task_$timestamp.cup"

            val cupContent = taskToCUP(task, "Generated Task")
            saveToDownloads(context, fileName, cupContent)

            Toast.makeText(context, "Task exported to Downloads/$fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun showImportInfo(context: Context) {
        val message = """
            To import tasks:

            1. Copy .cup files to Downloads folder
            2. Files will appear in the Files tab
            3. Tap a file to import it

            Supported formats:
            • CUP format (.cup)
            • SeeYou format (.cup)
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
        try {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)

            if (!file.exists()) {
                Toast.makeText(context, "File not found: $fileName", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Sharing task file: $fileName")
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
            val cupContent = taskToCUP(task, "Generated Task")

            shareDirectly(context, fileName, cupContent, task, taskManager)
        } catch (e: Exception) {
            Toast.makeText(context, "Export with sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToDownloads(context: Context, fileName: String, cupContent: String) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val file = File(downloadsDir, fileName)
        FileOutputStream(file).use { output ->
            output.write(cupContent.toByteArray())
        }
    }

    private fun shareDirectly(context: Context, fileName: String, cupContent: String, task: Task, taskManager: TaskManagerCoordinator) {
        try {
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { output ->
                output.write(cupContent.toByteArray())
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "application/octet-stream"
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
}
