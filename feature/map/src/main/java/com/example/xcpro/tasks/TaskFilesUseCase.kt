package com.example.xcpro.tasks

import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.core.time.Clock
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.domain.model.TaskTargetSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class TaskFilesUseCase @Inject constructor(
    private val repository: TaskFilesRepository,
    private val taskManager: TaskManagerCoordinator,
    private val clock: Clock
) {
    suspend fun loadDownloads(): List<CupDownloadEntry> = repository.queryDownloads()

    suspend fun importTaskFile(document: DocumentRef): TaskImportResult {
        val displayName = document.displayName
            ?: repository.resolveDisplayName(document)
            ?: document.fileName()
            ?: return TaskImportResult.Failure("Unable to resolve file name")

        val fileText = repository.readText(document)
            ?: return TaskImportResult.Failure("Unable to read task file")

        if (displayName.endsWith(".json", ignoreCase = true)) {
            return TaskImportResult.Json(displayName = displayName, json = fileText)
        }

        val waypoints = CupFormatUtils.parseCupWaypoints(fileText)
        if (waypoints.size < 2) {
            return TaskImportResult.Failure("Import failed: CUP needs at least 2 valid waypoints")
        }

        val taskType = CupFormatUtils.inferTaskType(waypoints)
        val task = Task(
            id = CupFormatUtils.stableTaskId(prefix = "cup", fileNameHint = displayName),
            waypoints = waypoints.mapIndexed { index, waypoint ->
                TaskWaypoint(
                    id = CupFormatUtils.stableWaypointId(
                        prefix = "cup",
                        index = index,
                        code = waypoint.code,
                        name = waypoint.name
                    ),
                    title = waypoint.name,
                    subtitle = waypoint.description,
                    lat = waypoint.latitude,
                    lon = waypoint.longitude,
                    role = CupFormatUtils.inferWaypointRole(
                        index = index,
                        total = waypoints.size,
                        code = waypoint.code
                    )
                )
            }
        )
        val json = TaskPersistSerializer.serialize(
            task = task,
            taskType = taskType,
            targets = emptyList()
        )
        return TaskImportResult.Json(displayName = displayName, json = json)
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

    suspend fun exportTaskToDownloads(
        task: Task,
        taskType: TaskType = taskManager.taskType,
        targets: List<TaskTargetSnapshot> = emptyList()
    ): TaskExportResult {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date(clock.nowWallMs()))
        val cupName = "task_${timestamp}.cup"
        val jsonName = "task_${timestamp}.xcp.json"

        val cupContent = taskToCup(task = task, taskType = taskType)
        val jsonContent = TaskPersistSerializer.serialize(
            task = task,
            taskType = taskType,
            targets = targets
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

    suspend fun shareTask(
        task: Task,
        taskType: TaskType = taskManager.taskType,
        targets: List<TaskTargetSnapshot> = emptyList()
    ): ShareRequest? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date(clock.nowWallMs()))
        val cupName = "task_${timestamp}.cup"
        val jsonName = "task_${timestamp}.xcp.json"

        val cupContent = taskToCup(task = task, taskType = taskType)
        val jsonContent = TaskPersistSerializer.serialize(
            task = task,
            taskType = taskType,
            targets = targets
        )

        val cupRef = runCatching { repository.writeCacheFile(cupName, cupContent) }.getOrNull()
        val jsonRef = runCatching { repository.writeCacheFile(jsonName, jsonContent) }.getOrNull()

        val primary = jsonRef ?: cupRef ?: return null
        val additional = listOfNotNull(
            if (primary.uri != jsonRef?.uri) jsonRef else null,
            if (primary.uri != cupRef?.uri) cupRef else null
        )

        return ShareRequest(
            document = primary,
            mime = if (additional.isNotEmpty()) "*/*" else if (primary.displayName?.endsWith(".json", ignoreCase = true) == true) "application/json" else "application/octet-stream",
            subject = "Task: ${task.waypoints.size} waypoints",
            text = buildShareText(task, taskType),
            chooserTitle = "Share Task",
            additionalDocuments = additional
        )
    }

    suspend fun shareExistingDownload(displayName: String): ShareRequest? {
        val document = repository.findDownloadFileRef(displayName) ?: return null
        return buildShareRequest(document, displayName)
    }

    private fun taskToCup(task: Task, taskType: TaskType): String {
        val headerRow = "name,code,country,lat,lon,elev,style,rwdir,rwlen,freq,desc"
        val metadataRow = listOf(
            CupFormatUtils.csvEscape("XCPro task"),
            CupFormatUtils.csvEscape("TASK"),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape("Created by XCPro")
        ).joinToString(",")

        val rows = task.waypoints.mapIndexed { index, waypoint ->
            val code = CupFormatUtils.exportCode(
                role = waypoint.role,
                index = index,
                taskType = taskType
            )
            val style = if (taskType == TaskType.AAT) "2" else "1"
            listOf(
                CupFormatUtils.csvEscape(waypoint.title.take(80)),
                CupFormatUtils.csvEscape(code),
                CupFormatUtils.csvEscape("XX"),
                CupFormatUtils.csvEscape(CupFormatUtils.formatLatitude(waypoint.lat)),
                CupFormatUtils.csvEscape(CupFormatUtils.formatLongitude(waypoint.lon)),
                CupFormatUtils.csvEscape("0m"),
                CupFormatUtils.csvEscape(style),
                CupFormatUtils.csvEscape(""),
                CupFormatUtils.csvEscape(""),
                CupFormatUtils.csvEscape(""),
                CupFormatUtils.csvEscape(waypoint.subtitle.take(120))
            ).joinToString(",")
        }
        return buildString {
            appendLine(headerRow)
            appendLine(metadataRow)
            rows.forEach { appendLine(it) }
        }
    }

    private fun buildShareText(task: Task, taskType: TaskType): String = buildString {
        appendLine("Task Details:")
        appendLine("Type: $taskType")
        appendLine("Waypoints: ${task.waypoints.size}")
        appendLine()
        task.waypoints.forEachIndexed { index, waypoint ->
            appendLine("${index + 1}. ${waypoint.title}")
            appendLine("   ${waypoint.lat}, ${waypoint.lon}")
        }
    }
}
