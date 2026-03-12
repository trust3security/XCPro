package com.example.xcpro.tasks.aat.persistence

import android.content.Context
import android.content.SharedPreferences
import com.example.xcpro.tasks.CupFormatUtils
import com.example.xcpro.tasks.LegacyCupStorageCleanupPolicy
import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATRadiusAuthority
import com.example.xcpro.tasks.core.TaskType
import com.google.gson.Gson
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * AAT Task File I/O Manager
 *
 * Handles all persistence operations for AAT tasks:
 * - SharedPreferences (current task state)
 * - CUP file format (save/load/delete task files)
 *
 * REFACTORED FROM: AATTaskManager.kt (Stage 2 - File I/O Extraction)
 * DEPENDENCIES: SimpleAATTask model, AATWaypoint models
 */
class AATTaskFileIO(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("aat_task_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val legacyTasksDir = File(context.filesDir, LEGACY_TASKS_DIR)
    private val scopedTasksDir = File(context.filesDir, AAT_TASKS_DIR)

    // ==================== SharedPreferences Operations ====================

    /**
     * Save AAT task to SharedPreferences
     *
     * Persists the current task state so it survives app restarts.
     *
     * @param task The task to save
     */
    fun saveToPreferences(task: SimpleAATTask) {
        val editor = prefs.edit()
        val taskJson = gson.toJson(task)
        editor.putString("current_aat_task", taskJson)
        editor.apply()
    }

    /**
     * Load AAT task from SharedPreferences
     *
     * Retrieves the previously saved task state.
     *
     * @return The saved task, or null if no task exists or parsing fails
     */
    fun loadFromPreferences(): SimpleAATTask? {
        val taskJson = prefs.getString("current_aat_task", null)
        return if (taskJson != null) {
            try {
                val task = gson.fromJson(taskJson, SimpleAATTask::class.java)
                task
            } catch (e: Exception) {
                null
            }
        } else null
    }

    // ==================== CUP File Operations ====================

    /**
     * Get list of saved AAT task files
     *
     * Scans the tasks directory for CUP files containing "AAT" in the name.
     *
     * @return List of task filenames (sorted alphabetically)
     */
    fun getSavedTaskFiles(): List<String> {
        return try {
            migrateLegacyTasksIfNeeded()
            val names = linkedSetOf<String>()
            ensureDir(scopedTasksDir)
            ensureDir(legacyTasksDir)

            scopedTasksDir.listFiles { file -> file.extension == "cup" }
                ?.mapTo(names) { it.name }

            legacyTasksDir.listFiles { file -> file.extension == "cup" }
                ?.filter { isLikelyLegacyAatCup(it) }
                ?.mapTo(names) { it.name }

            names.sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save AAT task to CUP file
     *
     * Exports the task to SeeYou CUP format for use in flight computers
     * and competition management software.
     *
     * @param task The task to save
     * @param taskName The filename (will be prefixed with "AAT_" if not already)
     * @return true if save succeeded, false otherwise
     */
    fun saveTaskToFile(task: SimpleAATTask, taskName: String): Boolean {
        return try {
            val tasksDir = ensureDir(scopedTasksDir)
            val aatTaskName = if (taskName.contains("AAT")) taskName else "AAT_$taskName"
            val fileName = if (aatTaskName.endsWith(".cup")) aatTaskName else "$aatTaskName.cup"
            val file = File(tasksDir, fileName)

            // Convert task to CUP format
            val cupContent = taskToCUP(task, aatTaskName)
            file.writeText(cupContent, StandardCharsets.UTF_8)

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Load AAT task from CUP file
     *
     * Imports a task from SeeYou CUP format.
     *
     * @param taskName The filename to load (with or without .cup extension)
     * @return The loaded task, or null if file doesn't exist or parsing fails
     */
    fun loadTaskFromFile(taskName: String): SimpleAATTask? {
        return try {
            migrateLegacyTasksIfNeeded()
            val file = resolveReadableTaskFile(taskName)

            if (file != null && file.exists()) {
                val fileName = file.name
                val cupContent = file.readText(StandardCharsets.UTF_8)
                val waypoints = parseCUPBasicWaypoints(cupContent)
                if (waypoints.isNotEmpty()) {
                    val task = SimpleAATTask(
                        id = CupFormatUtils.stableTaskId(prefix = "aat", fileNameHint = fileName),
                        waypoints = waypoints,
                        minimumTime = Duration.ofHours(3),
                        maximumTime = Duration.ofHours(6)
                    )
                    task
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete AAT task file
     *
     * Removes a task file from the tasks directory.
     *
     * @param taskName The filename to delete (with or without .cup extension)
     * @return true if deletion succeeded, false otherwise
     */
    fun deleteTaskFile(taskName: String): Boolean {
        return try {
            migrateLegacyTasksIfNeeded()
            val deletedAny = buildFileNameCandidates(taskName).any { fileName ->
                val scopedFile = File(scopedTasksDir, fileName)
                val legacyFile = File(legacyTasksDir, fileName)
                (scopedFile.exists() && scopedFile.delete()) || (legacyFile.exists() && legacyFile.delete())
            }
            deletedAny
        } catch (e: Exception) {
            false
        }
    }

    // ==================== CUP Format Conversion (Private) ====================

    /**
     * Convert AAT task to SeeYou CUP format
     *
     * CUP format is a widely-used waypoint and task file format for gliding
     * flight computers and competition management software.
     *
     * @param task The task to convert
     * @param taskName The task name for the header
     * @return CUP format string
     */
    private fun taskToCUP(task: SimpleAATTask, taskName: String): String {
        val headerRow = "name,code,country,lat,lon,elev,style,rwdir,rwlen,freq,desc"
        val metadataRow = listOf(
            CupFormatUtils.csvEscape("AAT task - $taskName"),
            CupFormatUtils.csvEscape("TASK"),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape("Created by AAT Task Manager")
        ).joinToString(",")

        val rows = task.waypoints.mapIndexed { index, waypoint ->
            val code = when (waypoint.role) {
                AATWaypointRole.START -> "START"
                AATWaypointRole.TURNPOINT -> "AAT$index"
                AATWaypointRole.FINISH -> "FINISH"
            }
            val radiusMeters = waypoint.effectiveRadiusMeters.toInt()
            listOf(
                CupFormatUtils.csvEscape(waypoint.title.take(80)),
                CupFormatUtils.csvEscape(code),
                CupFormatUtils.csvEscape(""),
                CupFormatUtils.csvEscape(CupFormatUtils.formatLatitude(waypoint.lat)),
                CupFormatUtils.csvEscape(CupFormatUtils.formatLongitude(waypoint.lon)),
                CupFormatUtils.csvEscape("0m"),
                CupFormatUtils.csvEscape("2"),
                CupFormatUtils.csvEscape(""),
                CupFormatUtils.csvEscape("${radiusMeters}m"),
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

    /**
     * Parse CUP file waypoints into AAT waypoints
     *
     * Basic parser that extracts waypoint coordinates and roles from CUP format.
     *
     * @param cupContent The CUP file content
     * @return List of AAT waypoints
     */
    private fun parseCUPBasicWaypoints(cupContent: String): List<AATWaypoint> {
        return try {
            val parsedWaypoints = CupFormatUtils.parseCupWaypoints(cupContent)
            parsedWaypoints.mapIndexed { index, waypoint ->
                val role = when (
                    CupFormatUtils.inferWaypointRole(
                        index = index,
                        total = parsedWaypoints.size,
                        code = waypoint.code
                    )
                ) {
                    com.example.xcpro.tasks.core.WaypointRole.START -> AATWaypointRole.START
                    com.example.xcpro.tasks.core.WaypointRole.FINISH -> AATWaypointRole.FINISH
                    com.example.xcpro.tasks.core.WaypointRole.TURNPOINT,
                    com.example.xcpro.tasks.core.WaypointRole.OPTIONAL -> AATWaypointRole.TURNPOINT
                }

                // COMPETITION-CRITICAL: Use AATRadiusAuthority for all AAT radii
                val radiusMeters = AATRadiusAuthority.getRadiusMetersForRole(role)

                AATWaypoint(
                    id = CupFormatUtils.stableWaypointId(
                        prefix = "aat",
                        index = index,
                        code = waypoint.code,
                        name = waypoint.name
                    ),
                    title = waypoint.name,
                    subtitle = waypoint.description,
                    lat = waypoint.latitude,
                    lon = waypoint.longitude,
                    role = role,
                    assignedArea = AATAssignedArea(
                        shape = AATAreaShape.CIRCLE,
                        radiusMeters = radiusMeters
                    )
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun ensureDir(dir: File): File {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun migrateLegacyTasksIfNeeded() {
        if (prefs.getBoolean(KEY_LEGACY_MIGRATION_DONE, false)) {
            LegacyCupStorageCleanupPolicy.runIfEligible(context)
            return
        }

        ensureDir(scopedTasksDir)
        ensureDir(legacyTasksDir)
        legacyTasksDir.listFiles { file -> file.extension == "cup" }
            ?.forEach { legacyFile ->
                if (!isLikelyLegacyAatCup(legacyFile)) {
                    return@forEach
                }
                val scopedFile = File(scopedTasksDir, legacyFile.name)
                runCatching {
                    if (scopedFile.exists()) {
                        val duplicate = runCatching {
                            scopedFile.readText(StandardCharsets.UTF_8) == legacyFile.readText(StandardCharsets.UTF_8)
                        }.getOrDefault(false)
                        if (duplicate) {
                            legacyFile.delete()
                        }
                    } else {
                        legacyFile.copyTo(scopedFile, overwrite = false)
                        legacyFile.delete()
                    }
                }
            }

        prefs.edit().putBoolean(KEY_LEGACY_MIGRATION_DONE, true).apply()
        LegacyCupStorageCleanupPolicy.runIfEligible(context)
    }

    private fun buildFileNameCandidates(taskName: String): List<String> {
        if (taskName.isBlank()) {
            return emptyList()
        }
        val normalized = if (taskName.endsWith(".cup")) taskName else "$taskName.cup"
        val withAatPrefix = if (normalized.startsWith("AAT_")) normalized else "AAT_$normalized"
        return listOf(normalized, withAatPrefix).distinct()
    }

    private fun resolveReadableTaskFile(taskName: String): File? {
        val names = buildFileNameCandidates(taskName)
        names.forEach { fileName ->
            val scopedFile = File(scopedTasksDir, fileName)
            if (scopedFile.exists()) {
                return scopedFile
            }
        }
        names.forEach { fileName ->
            val legacyFile = File(legacyTasksDir, fileName)
            if (legacyFile.exists()) {
                return legacyFile
            }
        }
        return null
    }

    private fun isLikelyLegacyAatCup(file: File): Boolean {
        return runCatching {
            val content = file.readText(StandardCharsets.UTF_8)
            val waypoints = CupFormatUtils.parseCupWaypoints(content)
            if (waypoints.isNotEmpty()) {
                CupFormatUtils.inferTaskType(waypoints) == TaskType.AAT
            } else {
                file.name.contains("AAT", ignoreCase = true)
            }
        }.getOrDefault(file.name.contains("AAT", ignoreCase = true))
    }

    private companion object {
        const val LEGACY_TASKS_DIR = "cup_tasks"
        const val AAT_TASKS_DIR = "cup_tasks/aat"
        const val KEY_LEGACY_MIGRATION_DONE = "legacy_aat_storage_migration_done_v1"
    }
}
