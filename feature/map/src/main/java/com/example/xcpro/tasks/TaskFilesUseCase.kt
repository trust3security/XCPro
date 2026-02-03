package com.example.xcpro.tasks

import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.tasks.core.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class TaskFilesUseCase @Inject constructor(
    private val repository: TaskFilesRepository,
    private val taskManager: TaskManagerCoordinator
) {
    suspend fun loadDownloads(): List<CupDownloadEntry> = repository.queryDownloads()

    suspend fun importTaskFile(document: DocumentRef): TaskImportResult {
        val displayName = document.displayName
            ?: repository.resolveDisplayName(document)
            ?: document.fileName()
            ?: return TaskImportResult.Failure("Unable to resolve file name")

        return if (displayName.endsWith(".json", ignoreCase = true)) {
            val json = repository.readText(document)
                ?: return TaskImportResult.Failure("Unable to read task file")
            TaskImportResult.Json(displayName = displayName, json = json)
        } else {
            val success = taskManager.loadTask(repository.appContext(), displayName)
            TaskImportResult.Cup(displayName = displayName, success = success)
        }
    }

    fun buildShareRequest(document: DocumentRef, displayName: String): ShareRequest? {
        val mime = repository.resolveMimeType(document) ?: "application/octet-stream"
        return ShareRequest(
            document = document,
            mime = mime,
            subject = "Task file: $displayName",
            text = "Sharing task file: $displayName",
            chooserTitle = "Share Task File"
        )
    }

    fun exportTaskToDownloads(task: Task): TaskExportResult {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cupName = "task_${timestamp}.cup"
        val jsonName = "task_${timestamp}.xcp.json"

        val cupContent = taskToCup(task)
        val jsonContent = TaskPersistSerializer.serialize(
            task = task,
            taskType = taskManager.taskType,
            targets = emptyList()
        )

        val saved = mutableListOf<String>()
        val cupRef = repository.saveToDownloads(cupName, cupContent)
        val jsonRef = repository.saveToDownloads(jsonName, jsonContent)

        if (cupRef != null) saved.add(cupName)
        if (jsonRef != null) saved.add(jsonName)

        return if (saved.isEmpty()) {
            TaskExportResult(savedNames = emptyList(), errorMessage = "Export failed: unable to save files")
        } else {
            TaskExportResult(savedNames = saved, errorMessage = null)
        }
    }

    fun shareTask(task: Task): List<ShareRequest> {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cupName = "task_${timestamp}.cup"
        val jsonName = "task_${timestamp}.xcp.json"

        val cupContent = taskToCup(task)
        val jsonContent = TaskPersistSerializer.serialize(
            task = task,
            taskType = taskManager.taskType,
            targets = emptyList()
        )

        val requests = mutableListOf<ShareRequest>()
        val cupRef = repository.writeCacheFile(cupName, cupContent)
        requests.add(buildShareRequest(cupRef, cupName)!!)

        val jsonRef = repository.writeCacheFile(jsonName, jsonContent)
        requests.add(
            ShareRequest(
                document = jsonRef,
                mime = "application/json",
                subject = "Task: ${task.waypoints.size} waypoints",
                text = buildShareText(task),
                chooserTitle = "Share Task"
            )
        )

        return requests
    }

    fun shareExistingDownload(displayName: String): ShareRequest? {
        val document = repository.findDownloadFileRef(displayName) ?: return null
        return buildShareRequest(document, displayName)
    }

    private fun taskToCup(task: Task): String {
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

    private fun buildShareText(task: Task): String = buildString {
        appendLine("Task Details:")
        appendLine("Type: ${taskManager.taskType}")
        appendLine("Waypoints: ${task.waypoints.size}")
        appendLine()
        task.waypoints.forEachIndexed { index, waypoint ->
            appendLine("${index + 1}. ${waypoint.title}")
            appendLine("   ${waypoint.lat}, ${waypoint.lon}")
        }
    }
}
