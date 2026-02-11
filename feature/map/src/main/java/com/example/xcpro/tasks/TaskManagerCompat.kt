package com.example.xcpro.tasks

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface TaskManagerCoordinatorEntryPoint {
    fun taskManagerCoordinator(): TaskManagerCoordinator
}

@Composable
fun rememberTaskManagerCoordinator(context: Context): TaskManagerCoordinator {
    val appContext = context.applicationContext
    val coordinator = remember(appContext) {
        EntryPointAccessors.fromApplication(
            appContext,
            TaskManagerCoordinatorEntryPoint::class.java
        ).taskManagerCoordinator()
    }
    LaunchedEffect(coordinator) {
        coordinator.loadSavedTasks()
    }
    return coordinator
}

typealias TaskManager = TaskManagerCoordinator

// Compatibility wrappers for older call sites
@Deprecated("Use rememberTaskManagerCoordinator with an explicit context")
fun getGlobalTaskManagerCoordinator(@Suppress("UNUSED_PARAMETER") context: Context? = null): TaskManagerCoordinator? = null

@Deprecated("Use rememberTaskManagerCoordinator with an explicit context")
fun getGlobalTaskManager(@Suppress("UNUSED_PARAMETER") context: Context? = null): TaskManagerCoordinator? = null

@Deprecated("Global mutable coordinators removed; supply via DI or rememberTaskManagerCoordinator")
fun setGlobalTaskManagerCoordinator(@Suppress("UNUSED_PARAMETER") coordinator: TaskManagerCoordinator) { /* no-op */ }

