package com.example.xcpro.tasks.racing

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import java.io.File
import java.util.UUID

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

    /**
     * Save Racing task to preferences
     */
    fun saveRacingTask(task: SimpleRacingTask) {
        val editor = prefs.edit()
        val taskJson = gson.toJson(task)
        editor.putString("current_racing_task", taskJson)
        editor.apply()
        println(" RACING STORAGE: Saved to preferences - ${task.waypoints.size} waypoints")
        println(" RACING STORAGE: JSON length: ${taskJson.length} chars")
    }

    /**
     * Load Racing task from preferences
     */
    fun loadRacingTask(): SimpleRacingTask? {
        println(" RACING STORAGE: ==> LOAD FUNCTION CALLED <==")
        println(" RACING STORAGE: Attempting to load from preferences...")

        val taskJson = prefs.getString("current_racing_task", null)
        println(" RACING STORAGE: Retrieved JSON from preferences: ${if (taskJson != null) "Found (${taskJson.length} chars)" else "NULL"}")

        return if (taskJson != null) {
            try {
                val task = gson.fromJson(taskJson, SimpleRacingTask::class.java)
                println(" RACING STORAGE: Loaded from preferences with ${task.waypoints.size} waypoints")
                task
            } catch (e: Exception) {
                println(" RACING STORAGE: Failed to load from preferences: ${e.message}")
                null
            }
        } else {
            println(" RACING STORAGE: No saved task found in preferences")
            null
        }
    }

    /**
     * Get list of saved Racing tasks (CUP files)
     */
    fun getSavedRacingTasks(): List<String> {
        return try {
            val tasksDir = File(context.filesDir, "cup_tasks")
            if (!tasksDir.exists()) {
                tasksDir.mkdirs()
            }
            tasksDir.listFiles { file -> file.extension == "cup" }
                ?.map { it.name }
                ?.sorted()
                ?: emptyList()
        } catch (e: Exception) {
            println(" RACING STORAGE: Error getting saved tasks: ${e.message}")
            emptyList()
        }
    }

    /**
     * Save Racing task to file (CUP format)
     */
    fun saveRacingTask(task: SimpleRacingTask, taskName: String): Boolean {
        return try {
            val tasksDir = File(context.filesDir, "cup_tasks")
            if (!tasksDir.exists()) {
                tasksDir.mkdirs()
            }
            val fileName = if (taskName.endsWith(".cup")) taskName else "$taskName.cup"
            val file = File(tasksDir, fileName)

            // Convert task to CUP format
            val cupContent = racingTaskToCUP(task, taskName)
            file.writeText(cupContent)

            println(" RACING STORAGE: Saved to file: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            println(" RACING STORAGE: Error saving task: ${e.message}")
            false
        }
    }

    /**
     * Load Racing task from file (CUP format)
     */
    fun loadRacingTaskFromFile(taskName: String): SimpleRacingTask? {
        return try {
            val fileName = if (taskName.endsWith(".cup")) taskName else "$taskName.cup"
            val file = File(context.filesDir, "cup_tasks/$fileName")

            if (file.exists()) {
                val cupContent = file.readText()
                val task = parseCUPToRacingTask(cupContent)
                if (task != null) {
                    println(" RACING STORAGE: Loaded from file: ${file.absolutePath}")
                    task
                } else {
                    println(" RACING STORAGE: Failed to parse CUP file: $fileName")
                    null
                }
            } else {
                println(" RACING STORAGE: File not found: ${file.absolutePath}")
                null
            }
        } catch (e: Exception) {
            println(" RACING STORAGE: Error loading task from file: ${e.message}")
            null
        }
    }

    /**
     * Delete Racing task file
     */
    fun deleteRacingTask(taskName: String): Boolean {
        return try {
            val fileName = if (taskName.endsWith(".cup")) taskName else "$taskName.cup"
            val file = File(context.filesDir, "cup_tasks/$fileName")

            if (file.exists()) {
                val deleted = file.delete()
                println(" RACING STORAGE: Deleted file: ${file.absolutePath} - Success: $deleted")
                deleted
            } else {
                println(" RACING STORAGE: File not found for deletion: ${file.absolutePath}")
                false
            }
        } catch (e: Exception) {
            println(" RACING STORAGE: Error deleting task: ${e.message}")
            false
        }
    }

    /**
     * Convert Racing task to CUP format
     */
    private fun racingTaskToCUP(task: SimpleRacingTask, taskName: String): String {
        val header = """
            name,code,country,lat,lon,elev,style,rwdir,rwlen,freq,desc
            "SeeYou task - $taskName","TASK","","","","","","","","","Created by Racing Task Manager"
        """.trimIndent()

        val waypoints = StringBuilder()
        task.waypoints.forEachIndexed { index, waypoint ->
            val latDegrees = waypoint.lat.toInt()
            val latMinutes = (kotlin.math.abs(waypoint.lat) - kotlin.math.abs(latDegrees)) * 60.0
            val latHem = if (waypoint.lat >= 0) "N" else "S"
            val latFormatted = "${kotlin.math.abs(latDegrees)}${String.format("%06.3f", latMinutes)}${latHem}"

            val lonDegrees = waypoint.lon.toInt()
            val lonMinutes = (kotlin.math.abs(waypoint.lon) - kotlin.math.abs(lonDegrees)) * 60.0
            val lonHem = if (waypoint.lon >= 0) "E" else "W"
            val lonFormatted = "${String.format("%03d", kotlin.math.abs(lonDegrees))}${String.format("%06.3f", lonMinutes)}${lonHem}"

            val code = when (waypoint.role) {
                RacingWaypointRole.START -> "START"
                RacingWaypointRole.FINISH -> "FINISH"
                RacingWaypointRole.TURNPOINT -> "TP${index}"
            }

            val style = "1" // Default to circle
            val radiusMeters = (waypoint.gateWidth * 1000).toInt()

            waypoints.append(
                "\"${waypoint.title}\",\"$code\",\"\",\"$latFormatted\",\"$lonFormatted\",\"0m\",\"$style\",\"\",\"${radiusMeters}m\",\"\",\"${waypoint.subtitle}\"\n"
            )
        }

        return header + "\n" + waypoints.toString()
    }

    /**
     * Parse CUP format to Racing task
     */
    private fun parseCUPToRacingTask(cupContent: String): SimpleRacingTask? {
        return try {
            val lines = cupContent.lines()
            val waypoints = mutableListOf<RacingWaypoint>()

            for (line in lines) {
                if (line.startsWith("\"") && !line.contains("name,code")) {
                    // Parse waypoint line
                    val parts = line.split(",").map { it.trim('"') }
                    if (parts.size >= 6) {
                        val title = parts[0]
                        val code = parts[1]
                        val lat = parseLatitude(parts[3])
                        val lon = parseLongitude(parts[4])

                        val role = when {
                            code.startsWith("START") -> RacingWaypointRole.START
                            code == "FINISH" -> RacingWaypointRole.FINISH
                            else -> RacingWaypointRole.TURNPOINT
                        }

                        waypoints.add(RacingWaypoint.createWithStandardizedDefaults(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            subtitle = "",
                            lat = lat,
                            lon = lon,
                            role = role
                        ))
                    }
                }
            }

            if (waypoints.isNotEmpty()) {
                SimpleRacingTask(
                    id = UUID.randomUUID().toString(),
                    waypoints = waypoints
                )
            } else {
                null
            }
        } catch (e: Exception) {
            println(" RACING STORAGE: Error parsing CUP file: ${e.message}")
            null
        }
    }

    /**
     * Parse latitude from CUP format (e.g., "5146.500N")
     */
    private fun parseLatitude(latStr: String): Double {
        val hem = latStr.last()
        val degrees = latStr.substring(0, 2).toDouble()
        val minutes = latStr.substring(2, latStr.length - 1).toDouble()
        val decimal = degrees + minutes / 60.0
        return if (hem == 'S') -decimal else decimal
    }

    /**
     * Parse longitude from CUP format (e.g., "00630.000E")
     */
    private fun parseLongitude(lonStr: String): Double {
        val hem = lonStr.last()
        val degrees = lonStr.substring(0, 3).toDouble()
        val minutes = lonStr.substring(3, lonStr.length - 1).toDouble()
        val decimal = degrees + minutes / 60.0
        return if (hem == 'W') -decimal else decimal
    }
}
