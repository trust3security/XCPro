package com.example.xcpro.tasks.aat.persistence

import android.content.Context
import android.content.SharedPreferences
import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATRadiusAuthority
import com.google.gson.Gson
import java.io.File
import java.time.Duration
import java.util.UUID
import kotlin.math.abs

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
        println(" AAT FILE I/O: Saved task to preferences (${task.waypoints.size} waypoints)")
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
                println(" AAT FILE I/O: Loaded task from preferences (${task.waypoints.size} waypoints)")
                task
            } catch (e: Exception) {
                println(" AAT FILE I/O: Failed to load from preferences: ${e.message}")
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
            val tasksDir = File(context.filesDir, "cup_tasks")
            if (!tasksDir.exists()) {
                tasksDir.mkdirs()
            }
            tasksDir.listFiles { file -> file.name.contains("AAT") && file.extension == "cup" }
                ?.map { it.name }
                ?.sorted()
                ?: emptyList()
        } catch (e: Exception) {
            println(" AAT FILE I/O: Error getting saved tasks: ${e.message}")
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
            val tasksDir = File(context.filesDir, "cup_tasks")
            if (!tasksDir.exists()) {
                tasksDir.mkdirs()
            }
            val aatTaskName = if (taskName.contains("AAT")) taskName else "AAT_$taskName"
            val fileName = if (aatTaskName.endsWith(".cup")) aatTaskName else "$aatTaskName.cup"
            val file = File(tasksDir, fileName)

            // Convert task to CUP format
            val cupContent = taskToCUP(task, aatTaskName)
            file.writeText(cupContent)

            println(" AAT FILE I/O: Saved to file: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            println(" AAT FILE I/O: Error saving task: ${e.message}")
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
            val fileName = if (taskName.endsWith(".cup")) taskName else "$taskName.cup"
            val file = File(context.filesDir, "cup_tasks/$fileName")

            if (file.exists()) {
                val cupContent = file.readText()
                val waypoints = parseCUPBasicWaypoints(cupContent)
                if (waypoints.isNotEmpty()) {
                    val task = SimpleAATTask(
                        id = UUID.randomUUID().toString(),
                        waypoints = waypoints,
                        minimumTime = Duration.ofHours(3),
                        maximumTime = Duration.ofHours(6)
                    )
                    println(" AAT FILE I/O: Loaded from file: ${file.absolutePath} (${waypoints.size} waypoints)")
                    task
                } else {
                    println(" AAT FILE I/O: No waypoints found in file")
                    null
                }
            } else {
                println(" AAT FILE I/O: File not found: ${file.absolutePath}")
                null
            }
        } catch (e: Exception) {
            println(" AAT FILE I/O: Error loading task from file: ${e.message}")
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
            val fileName = if (taskName.endsWith(".cup")) taskName else "$taskName.cup"
            val file = File(context.filesDir, "cup_tasks/$fileName")

            if (file.exists()) {
                val deleted = file.delete()
                println(" AAT FILE I/O: Deleted file: ${file.absolutePath} - Success: $deleted")
                deleted
            } else {
                println(" AAT FILE I/O: File not found for deletion: ${file.absolutePath}")
                false
            }
        } catch (e: Exception) {
            println(" AAT FILE I/O: Error deleting task: ${e.message}")
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
        val header = """
            name,code,country,lat,lon,elev,style,rwdir,rwlen,freq,desc
            "SeeYou task - $taskName","TASK","","","","","","","","","Created by AAT Task Manager"
        """.trimIndent()

        val waypoints = StringBuilder()
        task.waypoints.forEachIndexed { index, waypoint ->
            val latDegrees = waypoint.lat.toInt()
            val latMinutes = (abs(waypoint.lat) - abs(latDegrees)) * 60.0
            val latHem = if (waypoint.lat >= 0) "N" else "S"
            val latFormatted = "${abs(latDegrees)}${String.format("%06.3f", latMinutes)}${latHem}"

            val lonDegrees = waypoint.lon.toInt()
            val lonMinutes = (abs(waypoint.lon) - abs(lonDegrees)) * 60.0
            val lonHem = if (waypoint.lon >= 0) "E" else "W"
            val lonFormatted = "${String.format("%03d", abs(lonDegrees))}${String.format("%06.3f", lonMinutes)}${lonHem}"

            val code = when (waypoint.role) {
                AATWaypointRole.START -> "START"
                AATWaypointRole.TURNPOINT -> "AAT${index}"
                AATWaypointRole.FINISH -> "FINISH"
            }

            val style = "2" // AAT area style
            val radiusMeters = waypoint.effectiveRadiusMeters.toInt()

            waypoints.append(
                "\"${waypoint.title}\",\"$code\",\"\",\"$latFormatted\",\"$lonFormatted\",\"0m\",\"$style\",\"\",\"${radiusMeters}m\",\"\",\"${waypoint.subtitle}\"\n"
            )
        }

        return header + "\n" + waypoints.toString()
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
        val waypoints = mutableListOf<AATWaypoint>()
        try {
            val lines = cupContent.lines()
            for (line in lines) {
                if (line.startsWith("\"") && !line.contains("name,code")) {
                    val parts = line.split(",").map { it.trim('"') }
                    if (parts.size >= 6) {
                        val title = parts[0]
                        val code = parts[1]
                        val lat = parseLatitude(parts[3])
                        val lon = parseLongitude(parts[4])

                        val role = when {
                            code.startsWith("START") -> AATWaypointRole.START
                            code == "FINISH" -> AATWaypointRole.FINISH
                            else -> AATWaypointRole.TURNPOINT
                        }

                        //  COMPETITION-CRITICAL: Use AATRadiusAuthority for all AAT radii
                        val radiusKm = AATRadiusAuthority.getRadiusForRole(role)
                        val radiusMeters = radiusKm * 1000.0
                        println(" AAT FILE I/O: Creating waypoint ${title} (${role}) - using AUTHORITY ${radiusKm}km radius")

                        waypoints.add(AATWaypoint(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            subtitle = "",
                            lat = lat,
                            lon = lon,
                            role = role,
                            assignedArea = AATAssignedArea(
                                shape = AATAreaShape.CIRCLE,
                                radiusMeters = radiusMeters // Use authority radius based on role
                            )
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            println(" AAT FILE I/O: Error parsing CUP: ${e.message}")
        }
        return waypoints
    }

    /**
     * Parse CUP latitude string to decimal degrees
     *
     * CUP format: DDMM.MMMh (e.g., "5230.500N")
     * Where DD = degrees, MM.MMM = minutes, h = hemisphere (N/S)
     *
     * @param latStr The CUP latitude string
     * @return Latitude in decimal degrees
     */
    private fun parseLatitude(latStr: String): Double {
        val hem = latStr.last()
        val degrees = latStr.substring(0, 2).toDouble()
        val minutes = latStr.substring(2, latStr.length - 1).toDouble()
        val decimal = degrees + minutes / 60.0
        return if (hem == 'S') -decimal else decimal
    }

    /**
     * Parse CUP longitude string to decimal degrees
     *
     * CUP format: DDDMM.MMMh (e.g., "00845.250E")
     * Where DDD = degrees, MM.MMM = minutes, h = hemisphere (E/W)
     *
     * @param lonStr The CUP longitude string
     * @return Longitude in decimal degrees
     */
    private fun parseLongitude(lonStr: String): Double {
        val hem = lonStr.last()
        val degrees = lonStr.substring(0, 3).toDouble()
        val minutes = lonStr.substring(3, lonStr.length - 1).toDouble()
        val decimal = degrees + minutes / 60.0
        return if (hem == 'W') -decimal else decimal
    }
}
