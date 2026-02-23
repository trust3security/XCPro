package com.example.xcpro.tasks.racing

import android.content.Context
import android.content.SharedPreferences
import com.example.xcpro.tasks.CupFormatUtils
import com.example.xcpro.tasks.LegacyCupStorageCleanupPolicy
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.WaypointRole
import com.google.gson.Gson
import java.io.File
import java.nio.charset.StandardCharsets

// Racing-specific imports - NO cross-contamination with AAT/DHT
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole

/**
 * Racing Task Storage - Handles all persistence operations for Racing tasks
 *
 * ZERO DEPENDENCIES on AAT or DHT modules - maintains complete separation
 * All Racing storage logic is self-contained within this class
 */
class RacingTaskStorage(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("racing_task_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val legacyTasksDir = File(context.filesDir, LEGACY_TASKS_DIR)
    private val scopedTasksDir = File(context.filesDir, RACING_TASKS_DIR)

    /**
     * Save Racing task to preferences
     */
    fun saveRacingTask(task: SimpleRacingTask) {
        val editor = prefs.edit()
        val taskJson = gson.toJson(task)
        editor.putString("current_racing_task", taskJson)
        editor.apply()
    }

    /**
     * Load Racing task from preferences
     */
    fun loadRacingTask(): SimpleRacingTask? {

        val taskJson = prefs.getString("current_racing_task", null)

        return if (taskJson != null) {
            try {
                val task = gson.fromJson(taskJson, SimpleRacingTask::class.java)
                task
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Get list of saved Racing tasks (CUP files)
     */
    fun getSavedRacingTasks(): List<String> {
        return try {
            migrateLegacyTasksIfNeeded()
            val names = linkedSetOf<String>()
            ensureDir(scopedTasksDir)
            ensureDir(legacyTasksDir)

            scopedTasksDir.listFiles { file -> file.extension == "cup" }
                ?.mapTo(names) { it.name }

            legacyTasksDir.listFiles { file -> file.extension == "cup" }
                ?.filter { isLikelyLegacyRacingCup(it) }
                ?.mapTo(names) { it.name }

            names.sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save Racing task to file (CUP format)
     */
    fun saveRacingTask(task: SimpleRacingTask, taskName: String): Boolean {
        return try {
            val tasksDir = ensureDir(scopedTasksDir)
            val fileName = if (taskName.endsWith(".cup")) taskName else "$taskName.cup"
            val file = File(tasksDir, fileName)

            // Convert task to CUP format
            val cupContent = racingTaskToCUP(task, taskName)
            file.writeText(cupContent, StandardCharsets.UTF_8)

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Load Racing task from file (CUP format)
     */
    fun loadRacingTaskFromFile(taskName: String): SimpleRacingTask? {
        return try {
            migrateLegacyTasksIfNeeded()
            val fileName = if (taskName.endsWith(".cup")) taskName else "$taskName.cup"
            val file = resolveReadableTaskFile(fileName)

            if (file != null && file.exists()) {
                val cupContent = file.readText(StandardCharsets.UTF_8)
                val task = parseCUPToRacingTask(cupContent, fileName)
                if (task != null) {
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
     * Delete Racing task file
     */
    fun deleteRacingTask(taskName: String): Boolean {
        return try {
            migrateLegacyTasksIfNeeded()
            val fileName = if (taskName.endsWith(".cup")) taskName else "$taskName.cup"
            val scopedFile = File(scopedTasksDir, fileName)
            val legacyFile = File(legacyTasksDir, fileName)
            val deletedScoped = scopedFile.exists() && scopedFile.delete()
            val deletedLegacy = legacyFile.exists() && legacyFile.delete()
            deletedScoped || deletedLegacy
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Convert Racing task to CUP format
     */
    private fun racingTaskToCUP(task: SimpleRacingTask, taskName: String): String {
        val headerRow = "name,code,country,lat,lon,elev,style,rwdir,rwlen,freq,desc"
        val metadataRow = listOf(
            CupFormatUtils.csvEscape("Racing task - $taskName"),
            CupFormatUtils.csvEscape("TASK"),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape(""),
            CupFormatUtils.csvEscape("Created by Racing Task Manager")
        ).joinToString(",")

        val rows = task.waypoints.mapIndexed { index, waypoint ->
            val role = when (waypoint.role) {
                RacingWaypointRole.START -> WaypointRole.START
                RacingWaypointRole.FINISH -> WaypointRole.FINISH
                RacingWaypointRole.TURNPOINT -> WaypointRole.TURNPOINT
            }
            listOf(
                CupFormatUtils.csvEscape(waypoint.title.take(80)),
                CupFormatUtils.csvEscape(
                    CupFormatUtils.exportCode(
                        role = role,
                        index = index,
                        taskType = TaskType.RACING
                    )
                ),
                CupFormatUtils.csvEscape(""),
                CupFormatUtils.csvEscape(CupFormatUtils.formatLatitude(waypoint.lat)),
                CupFormatUtils.csvEscape(CupFormatUtils.formatLongitude(waypoint.lon)),
                CupFormatUtils.csvEscape("0m"),
                CupFormatUtils.csvEscape("1"),
                CupFormatUtils.csvEscape(""),
                CupFormatUtils.csvEscape("${waypoint.gateWidthMeters.toInt()}m"),
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
     * Parse CUP format to Racing task
     */
    private fun parseCUPToRacingTask(cupContent: String, taskNameHint: String): SimpleRacingTask? {
        return try {
            val parsedWaypoints = CupFormatUtils.parseCupWaypoints(cupContent)
            if (parsedWaypoints.isNotEmpty()) {
                val waypoints = parsedWaypoints.mapIndexed { index, waypoint ->
                    val role = when (
                        CupFormatUtils.inferWaypointRole(
                            index = index,
                            total = parsedWaypoints.size,
                            code = waypoint.code
                        )
                    ) {
                        WaypointRole.START -> RacingWaypointRole.START
                        WaypointRole.FINISH -> RacingWaypointRole.FINISH
                        WaypointRole.TURNPOINT,
                        WaypointRole.OPTIONAL -> RacingWaypointRole.TURNPOINT
                    }
                    RacingWaypoint.createWithStandardizedDefaults(
                        id = CupFormatUtils.stableWaypointId(
                            prefix = "racing",
                            index = index,
                            code = waypoint.code,
                            name = waypoint.name
                        ),
                        title = waypoint.name,
                        subtitle = waypoint.description,
                        lat = waypoint.latitude,
                        lon = waypoint.longitude,
                        role = role
                    )
                }
                SimpleRacingTask(
                    id = CupFormatUtils.stableTaskId("racing", taskNameHint),
                    waypoints = waypoints
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
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
                if (!isLikelyLegacyRacingCup(legacyFile)) {
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

    private fun resolveReadableTaskFile(fileName: String): File? {
        val scopedFile = File(scopedTasksDir, fileName)
        if (scopedFile.exists()) {
            return scopedFile
        }
        val legacyFile = File(legacyTasksDir, fileName)
        return legacyFile.takeIf { it.exists() }
    }

    private fun isLikelyLegacyRacingCup(file: File): Boolean {
        return runCatching {
            val content = file.readText(StandardCharsets.UTF_8)
            val waypoints = CupFormatUtils.parseCupWaypoints(content)
            if (waypoints.isNotEmpty()) {
                CupFormatUtils.inferTaskType(waypoints) == TaskType.RACING
            } else {
                !file.name.contains("AAT", ignoreCase = true)
            }
        }.getOrDefault(!file.name.contains("AAT", ignoreCase = true))
    }

    private companion object {
        const val LEGACY_TASKS_DIR = "cup_tasks"
        const val RACING_TASKS_DIR = "cup_tasks/racing"
        const val KEY_LEGACY_MIGRATION_DONE = "legacy_racing_storage_migration_done_v1"
    }
}
