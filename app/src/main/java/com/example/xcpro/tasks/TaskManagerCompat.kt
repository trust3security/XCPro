package com.example.xcpro.tasks

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private var globalTaskManagerCoordinator: TaskManagerCoordinator? = null

fun getGlobalTaskManagerCoordinator(context: Context? = null): TaskManagerCoordinator? {
    if (globalTaskManagerCoordinator == null && context != null) {
        globalTaskManagerCoordinator = TaskManagerCoordinator(context)
    }
    return globalTaskManagerCoordinator
}

@Composable
fun rememberTaskManagerCoordinator(context: Context? = null): TaskManagerCoordinator {
    return remember {
        if (globalTaskManagerCoordinator == null) {
            globalTaskManagerCoordinator = TaskManagerCoordinator(context)
            globalTaskManagerCoordinator?.loadSavedTasks()
        }
        globalTaskManagerCoordinator!!
    }
}

typealias TaskManager = TaskManagerCoordinator

fun getGlobalTaskManager(context: Context? = null): TaskManagerCoordinator? {
    return getGlobalTaskManagerCoordinator(context)
}

@Composable
fun rememberTaskManager(context: Context? = null): TaskManagerCoordinator {
    return rememberTaskManagerCoordinator(context)
}

