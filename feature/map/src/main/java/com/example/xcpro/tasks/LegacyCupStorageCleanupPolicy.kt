package com.example.xcpro.tasks

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * One-time cleanup policy for residual legacy CUP artifacts.
 *
 * Runs only after both task-type migrations are complete, then removes redundant legacy
 * duplicates and archives unresolved leftovers out of the shared legacy root.
 */
internal object LegacyCupStorageCleanupPolicy {

    fun runIfEligible(context: Context) {
        if (!isBothMigrationsComplete(context)) {
            return
        }
        val policyPrefs = context.getSharedPreferences(POLICY_PREFS, Context.MODE_PRIVATE)
        if (policyPrefs.getBoolean(KEY_LEGACY_CLEANUP_DONE, false)) {
            return
        }

        val filesDir = context.filesDir
        val legacyDir = File(filesDir, LEGACY_TASKS_DIR)
        if (!legacyDir.exists()) {
            policyPrefs.edit().putBoolean(KEY_LEGACY_CLEANUP_DONE, true).apply()
            return
        }

        val racingDir = File(filesDir, RACING_TASKS_DIR)
        val aatDir = File(filesDir, AAT_TASKS_DIR)
        val archiveDir = ensureDir(File(filesDir, LEGACY_ARCHIVE_DIR))
        val legacyCupFiles = legacyDir.listFiles { file -> file.isFile && file.extension == CUP_EXTENSION }
            ?: emptyArray()

        legacyCupFiles.forEach { legacyFile ->
            val racingScoped = File(racingDir, legacyFile.name)
            val aatScoped = File(aatDir, legacyFile.name)
            when {
                isDuplicateOf(legacyFile, racingScoped) -> {
                    legacyFile.delete()
                }
                isDuplicateOf(legacyFile, aatScoped) -> {
                    legacyFile.delete()
                }
                else -> {
                    val archivedFile = uniqueArchiveTarget(archiveDir, legacyFile.name)
                    runCatching {
                        legacyFile.copyTo(archivedFile, overwrite = false)
                        legacyFile.delete()
                    }
                }
            }
        }

        val hasResidualLegacyCup = legacyDir
            .listFiles { file -> file.isFile && file.extension == CUP_EXTENSION }
            ?.isNotEmpty() == true
        if (!hasResidualLegacyCup) {
            policyPrefs.edit().putBoolean(KEY_LEGACY_CLEANUP_DONE, true).apply()
        }
    }

    private fun isBothMigrationsComplete(context: Context): Boolean {
        val racingDone = context
            .getSharedPreferences(RACING_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_RACING_MIGRATION_DONE, false)
        if (!racingDone) {
            return false
        }
        val aatDone = context
            .getSharedPreferences(AAT_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AAT_MIGRATION_DONE, false)
        return aatDone
    }

    private fun ensureDir(dir: File): File {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun isDuplicateOf(legacyFile: File, scopedFile: File): Boolean {
        if (!scopedFile.exists() || !scopedFile.isFile) {
            return false
        }
        return runCatching {
            legacyFile.readText(StandardCharsets.UTF_8) == scopedFile.readText(StandardCharsets.UTF_8)
        }.getOrDefault(false)
    }

    private fun uniqueArchiveTarget(archiveDir: File, fileName: String): File {
        val base = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var candidate = File(archiveDir, fileName)
        var index = 1
        while (candidate.exists()) {
            val suffix = "_legacy_$index"
            val nameWithSuffix = if (extension.isBlank()) {
                "$base$suffix"
            } else {
                "$base$suffix.$extension"
            }
            candidate = File(archiveDir, nameWithSuffix)
            index++
        }
        return candidate
    }

    private const val POLICY_PREFS = "task_storage_migration_prefs"
    private const val KEY_LEGACY_CLEANUP_DONE = "legacy_cup_storage_cleanup_done_v1"

    private const val RACING_PREFS = "racing_task_prefs"
    private const val AAT_PREFS = "aat_task_prefs"
    private const val KEY_RACING_MIGRATION_DONE = "legacy_racing_storage_migration_done_v1"
    private const val KEY_AAT_MIGRATION_DONE = "legacy_aat_storage_migration_done_v1"

    private const val LEGACY_TASKS_DIR = "cup_tasks"
    private const val RACING_TASKS_DIR = "cup_tasks/racing"
    private const val AAT_TASKS_DIR = "cup_tasks/aat"
    private const val LEGACY_ARCHIVE_DIR = "cup_tasks/legacy_archive"

    private const val CUP_EXTENSION = "cup"
}
