package com.trust3.xcpro.tasks.data.persistence

import android.content.Context
import android.content.SharedPreferences
import com.trust3.xcpro.tasks.TaskPersistSerializer
import com.trust3.xcpro.tasks.aat.persistence.AATTaskFileIO
import com.trust3.xcpro.tasks.aat.toCoreTask
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import java.io.File
import java.nio.charset.StandardCharsets

internal class AATCanonicalTaskStorage(
    private val namedTasksDir: File,
    private val autosavePreference: CanonicalTaskStringPreference,
    private val legacyStore: LegacyAATTaskSource? = null
) {

    fun listTaskNames(): List<String> {
        val names = linkedSetOf<String>()
        namedTasksDir.listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
            ?.mapNotNullTo(names) { file -> normalizeTaskName(file.name) }
        legacyStore?.listTaskNames()?.mapNotNullTo(names, ::normalizeTaskName)
        return names.sorted()
    }

    fun loadAutosavedTask(): Task? {
        deserializeTask(autosavePreference.read())?.let { return it }
        return legacyStore?.loadAutosavedTask()?.also(::saveAutosavedTask)
    }

    fun saveAutosavedTask(task: Task) {
        autosavePreference.write(serializeTask(task))
    }

    fun clearAutosavedTask() {
        autosavePreference.clear()
        legacyStore?.clearAutosavedTask()
    }

    fun loadTask(taskName: String): Task? {
        val normalized = normalizeTaskName(taskName) ?: return null
        readTaskFile(taskFile(normalized))?.let { return it }

        val legacyTask = loadLegacyTask(taskName, normalized) ?: return null
        saveTask(normalized, legacyTask)
        return legacyTask
    }

    fun saveTask(taskName: String, task: Task): Boolean {
        val normalized = normalizeTaskName(taskName) ?: return false
        return runCatching {
            ensureDir(namedTasksDir)
            taskFile(normalized).writeText(serializeTask(task), StandardCharsets.UTF_8)
            true
        }.getOrDefault(false)
    }

    fun deleteTask(taskName: String): Boolean {
        val normalized = normalizeTaskName(taskName) ?: return false
        var deleted = false
        val file = taskFile(normalized)
        if (file.exists()) {
            deleted = file.delete()
        }
        if (deleteLegacyTask(taskName, normalized)) {
            deleted = true
        }
        return deleted
    }

    private fun loadLegacyTask(taskName: String, normalizedTaskName: String): Task? {
        val fromOriginal = legacyStore?.loadTask(taskName)
        if (fromOriginal != null) {
            return fromOriginal
        }
        if (normalizedTaskName != taskName) {
            return legacyStore?.loadTask(normalizedTaskName)
        }
        return null
    }

    private fun deleteLegacyTask(taskName: String, normalizedTaskName: String): Boolean {
        val deletedOriginal = legacyStore?.deleteTask(taskName) == true
        if (deletedOriginal) {
            return true
        }
        if (normalizedTaskName != taskName) {
            return legacyStore?.deleteTask(normalizedTaskName) == true
        }
        return false
    }

    private fun readTaskFile(file: File): Task? {
        if (!file.exists()) {
            return null
        }
        return deserializeTask(
            runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull()
        )
    }

    private fun taskFile(taskName: String): File =
        File(ensureDir(namedTasksDir), taskName + FILE_SUFFIX)

    private fun ensureDir(dir: File): File {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun serializeTask(task: Task): String =
        TaskPersistSerializer.serialize(task = task, taskType = TaskType.AAT, targets = emptyList())

    private fun deserializeTask(json: String?): Task? {
        if (json.isNullOrBlank()) {
            return null
        }
        return runCatching {
            TaskPersistSerializer.toTask(TaskPersistSerializer.deserialize(json)).first
        }.getOrNull()
    }

    private fun normalizeTaskName(taskName: String): String? {
        val trimmed = taskName.trim()
        if (trimmed.isBlank()) {
            return null
        }
        return when {
            trimmed.endsWith(FILE_SUFFIX) -> trimmed.removeSuffix(FILE_SUFFIX)
            trimmed.endsWith(".cup") -> trimmed.removeSuffix(".cup")
            trimmed.endsWith(".json") -> trimmed.removeSuffix(".json")
            else -> trimmed
        }
    }

    companion object {
        private const val PREFS_NAME = "aat_canonical_task_store"
        private const val AUTOSAVE_KEY = "aat_autosave_task_json"
        private const val FILE_SUFFIX = ".xcp.json"
        private const val TASKS_DIR = "task_store/aat"

        fun create(context: Context): AATCanonicalTaskStorage {
            return AATCanonicalTaskStorage(
                namedTasksDir = File(context.filesDir, TASKS_DIR),
                autosavePreference = SharedPreferencesStringPreference(
                    prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                    key = AUTOSAVE_KEY
                ),
                legacyStore = LegacyAATTaskSourceAdapter(AATTaskFileIO(context))
            )
        }
    }
}

internal interface CanonicalTaskStringPreference {
    fun read(): String?
    fun write(value: String)
    fun clear()
}

internal class SharedPreferencesStringPreference(
    private val prefs: SharedPreferences,
    private val key: String
) : CanonicalTaskStringPreference {
    override fun read(): String? = prefs.getString(key, null)

    override fun write(value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun clear() {
        prefs.edit().remove(key).apply()
    }
}

internal interface LegacyAATTaskSource {
    fun listTaskNames(): List<String>
    fun loadAutosavedTask(): Task?
    fun clearAutosavedTask()
    fun loadTask(taskName: String): Task?
    fun deleteTask(taskName: String): Boolean
}

internal class LegacyAATTaskSourceAdapter(
    private val fileIO: AATTaskFileIO
) : LegacyAATTaskSource {
    override fun listTaskNames(): List<String> =
        fileIO.getSavedTaskFiles().map { it.removeSuffix(".cup") }

    override fun loadAutosavedTask(): Task? =
        fileIO.loadFromPreferences()?.toCoreTask()

    override fun clearAutosavedTask() {
        fileIO.saveToPreferences(com.trust3.xcpro.tasks.aat.SimpleAATTask(id = "", waypoints = emptyList()))
    }

    override fun loadTask(taskName: String): Task? =
        fileIO.loadTaskFromFile(taskName)?.toCoreTask()

    override fun deleteTask(taskName: String): Boolean =
        fileIO.deleteTaskFile(taskName)
}
