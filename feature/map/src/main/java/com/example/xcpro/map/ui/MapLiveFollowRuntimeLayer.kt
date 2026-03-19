package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.livefollow.liveFollowTaskAttachmentMessage
import com.example.xcpro.livefollow.watch.LiveFollowMapRenderState
import com.example.xcpro.livefollow.watch.LiveFollowTaskRenderPolicy
import com.example.xcpro.livefollow.watch.LiveFollowWatchMapHost
import com.example.xcpro.livefollow.watch.LiveFollowWatchViewModel
import com.example.xcpro.map.TaskRenderSnapshot

internal data class MapLiveFollowTaskAttachmentState(
    val attachTask: Boolean,
    val message: String?
)

internal fun resolveMapLiveFollowTaskAttachmentState(
    mapRenderState: LiveFollowMapRenderState,
    taskRenderSnapshotProvider: () -> TaskRenderSnapshot
): MapLiveFollowTaskAttachmentState {
    return when (mapRenderState.taskRenderPolicy) {
        LiveFollowTaskRenderPolicy.BLOCKED_AMBIGUOUS -> {
            MapLiveFollowTaskAttachmentState(
                attachTask = false,
                message = liveFollowTaskAttachmentMessage(mapRenderState.taskRenderPolicy)
            )
        }

        LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE -> {
            // Keep task seam consumption on the map side without inventing watch-task ownership.
            taskRenderSnapshotProvider()
            MapLiveFollowTaskAttachmentState(
                attachTask = false,
                message = liveFollowTaskAttachmentMessage(mapRenderState.taskRenderPolicy)
            )
        }

        LiveFollowTaskRenderPolicy.HIDDEN -> MapLiveFollowTaskAttachmentState(
            attachTask = false,
            message = null
        )
    }
}

@Composable
internal fun BoxScope.MapLiveFollowRuntimeLayer(
    taskRenderSnapshotProvider: () -> TaskRenderSnapshot
) {
    val watchViewModel: LiveFollowWatchViewModel = hiltViewModel()
    val uiState = watchViewModel.uiState.collectAsStateWithLifecycle()
    val taskAttachmentState = remember(
        uiState.value.mapRenderState,
        taskRenderSnapshotProvider
    ) {
        resolveMapLiveFollowTaskAttachmentState(
            mapRenderState = uiState.value.mapRenderState,
            taskRenderSnapshotProvider = taskRenderSnapshotProvider
        )
    }

    LiveFollowWatchMapHost(
        uiState = uiState.value,
        taskAttachmentMessage = taskAttachmentState.message,
        onStopWatching = watchViewModel::stopWatching,
        onDismissMessage = watchViewModel::dismissFeedback
    )
}
