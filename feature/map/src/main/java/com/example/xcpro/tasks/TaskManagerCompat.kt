package com.example.xcpro.tasks

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun rememberTaskManagerCoordinator(context: Context): TaskManagerCoordinator {
    val appContext = context.applicationContext
    return remember {
        TaskManagerCoordinator(appContext).also { it.loadSavedTasks() }
    }
}

typealias TaskManager = TaskManagerCoordinator

// Compatibility wrappers for older call sites
@Deprecated("Use rememberTaskManagerCoordinator with an explicit context")
fun getGlobalTaskManagerCoordinator(@Suppress("UNUSED_PARAMETER") context: Context? = null): TaskManagerCoordinator? = null

@Deprecated("Use rememberTaskManagerCoordinator with an explicit context")
fun getGlobalTaskManager(@Suppress("UNUSED_PARAMETER") context: Context? = null): TaskManagerCoordinator? = null

@Deprecated("Global mutable coordinators removed; supply via DI or rememberTaskManagerCoordinator")
fun setGlobalTaskManagerCoordinator(@Suppress("UNUSED_PARAMETER") coordinator: TaskManagerCoordinator) { /* no-op */ }

