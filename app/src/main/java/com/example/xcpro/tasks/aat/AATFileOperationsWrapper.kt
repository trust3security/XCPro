package com.example.xcpro.tasks.aat

import android.content.Context
import com.example.xcpro.tasks.aat.persistence.AATTaskFileIO

/**
 * AAT File Operations Wrapper - Simple delegation wrapper for file operations
 *
 * Extracted from AATTaskManager.kt for file size compliance.
 * Provides clean API for file-based task persistence by delegating to AATTaskFileIO.
 *
 * STATELESS HELPER: All methods delegate to fileIO
 */
internal class AATFileOperationsWrapper(
    private val fileIO: AATTaskFileIO?
) {

    /**
     * Get list of saved AAT task files
     */
    fun getSavedTaskFiles(): List<String> {
        return fileIO?.getSavedTaskFiles() ?: emptyList()
    }

    /**
     * Save AAT task to file
     */
    fun saveTaskToFile(task: SimpleAATTask, taskName: String): Boolean {
        return fileIO?.saveTaskToFile(task, taskName) ?: false
    }

    /**
     * Load AAT task from file
     * @return Loaded task, or null if loading failed
     */
    fun loadTaskFromFile(taskName: String): SimpleAATTask? {
        return fileIO?.loadTaskFromFile(taskName)
    }

    /**
     * Delete AAT task file
     */
    fun deleteTaskFile(taskName: String): Boolean {
        return fileIO?.deleteTaskFile(taskName) ?: false
    }

    /**
     * Save task to preferences (for active task persistence)
     */
    fun saveToPreferences(task: SimpleAATTask) {
        fileIO?.saveToPreferences(task)
    }

    /**
     * Load task from preferences (for active task restoration)
     */
    fun loadFromPreferences(): SimpleAATTask? {
        return fileIO?.loadFromPreferences()
    }
}
