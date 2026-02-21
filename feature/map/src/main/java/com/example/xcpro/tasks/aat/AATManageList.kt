package com.example.xcpro.tasks.aat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.tasks.TaskSheetViewModel
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.domain.model.TaskTargetSnapshot
import kotlinx.coroutines.launch

@Composable
internal fun AATReorderableWaypointList(
    waypoints: List<TaskWaypoint>,
    targets: List<TaskTargetSnapshot>,
    allWaypoints: List<WaypointData> = emptyList(),
    onReorder: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onWaypointReplace: (Int, SearchWaypoint) -> Unit,
    taskViewModel: TaskSheetViewModel,
    currentQNH: String? = null,
    modifier: Modifier = Modifier
) {
    var expandedWaypointIndex by remember { mutableStateOf<Int?>(null) }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val navBarBottom = androidx.compose.foundation.layout.WindowInsets
        .navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val bottomPad = navBarBottom + 48.dp

    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 8.dp, start = 0.dp, end = 0.dp, bottom = bottomPad)
    ) {
        itemsIndexed(
            items = waypoints,
            key = { index, wp -> "aat_wp_${index}_${wp.id}" }
        ) { index, taskWaypoint ->
            AATReorderableWaypointItem(
                waypoint = SearchWaypoint(
                    id = taskWaypoint.id,
                    title = taskWaypoint.title,
                    subtitle = taskWaypoint.subtitle,
                    lat = taskWaypoint.lat,
                    lon = taskWaypoint.lon
                ),
                taskWaypoint = taskWaypoint,
                allWaypoints = allWaypoints,
                index = index,
                totalCount = waypoints.size,
                role = when {
                    index == 0 -> "Start"
                    index == waypoints.lastIndex && waypoints.size > 1 -> "Finish"
                    else -> "Turn $index"
                },
                nextWaypoint = if (index < waypoints.lastIndex) waypoints[index + 1] else null,
                targetSnapshot = targets.getOrNull(index),
                taskViewModel = taskViewModel,
                isExpanded = expandedWaypointIndex == index,
                onExpandToggle = { shouldExpand ->
                    if (shouldExpand) {
                        expandedWaypointIndex = index
                        coroutineScope.launch { lazyListState.animateScrollToItem(index) }
                    } else {
                        expandedWaypointIndex = null
                    }
                },
                onMoveUp = if (index > 0) ({ onReorder(index, index - 1) }) else null,
                onMoveDown = if (index < waypoints.lastIndex) ({ onReorder(index, index + 1) }) else null,
                onRemove = { onRemove(index) },
                onWaypointReplace = { newWp -> onWaypointReplace(index, newWp) },
                currentQNH = currentQNH
            )
        }
    }
}
