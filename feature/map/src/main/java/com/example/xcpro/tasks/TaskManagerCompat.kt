package com.example.xcpro.tasks

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
internal class TaskManagerCoordinatorHostViewModel @Inject constructor(
    val taskManagerCoordinator: TaskManagerCoordinator
) : ViewModel() {
    init {
        viewModelScope.launch {
            taskManagerCoordinator.loadSavedTasks()
        }
    }
}

@Composable
fun rememberTaskManagerCoordinator(): TaskManagerCoordinator {
    val host: TaskManagerCoordinatorHostViewModel = hiltViewModel()
    return host.taskManagerCoordinator
}

typealias TaskManager = TaskManagerCoordinator

// Compatibility wrappers for older call sites
@Deprecated("Use rememberTaskManagerCoordinator()")
fun getGlobalTaskManagerCoordinator(@Suppress("UNUSED_PARAMETER") context: Context? = null): TaskManagerCoordinator? = null

@Deprecated("Use rememberTaskManagerCoordinator()")
fun getGlobalTaskManager(@Suppress("UNUSED_PARAMETER") context: Context? = null): TaskManagerCoordinator? = null

@Deprecated("Global mutable coordinators removed; supply via DI or rememberTaskManagerCoordinator()")
fun setGlobalTaskManagerCoordinator(@Suppress("UNUSED_PARAMETER") coordinator: TaskManagerCoordinator) { /* no-op */ }

