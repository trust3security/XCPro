package com.example.xcpro.tasks.aat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.xcpro.tasks.aat.map.AATEditSession
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint

@Composable
fun AATEditModeOverlay(
    editSession: AATEditSession,
    nowMs: Long,
    onSaveChanges: () -> Unit,
    onDiscardChanges: () -> Unit,
    onResetToCenter: () -> Unit,
    onExitEditMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editModeVisible by remember(editSession.isEditingArea) {
        derivedStateOf { editSession.isEditingArea }
    }

    AnimatedVisibility(
        visible = editModeVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300)
        ) + fadeOut(),
        modifier = modifier
    ) {
        if (editSession.isEditingArea && editSession.focusedWaypoint != null) {
            EditModeOverlayContent(
                waypoint = editSession.focusedWaypoint!!,
                currentTargetPoint = editSession.currentTargetPoint!!,
                originalTargetPoint = editSession.originalTargetPoint!!,
                hasUnsavedChanges = editSession.hasUnsavedChanges,
                sessionDuration = editSession.sessionDurationMs(nowMs),
                onSaveChanges = onSaveChanges,
                onDiscardChanges = onDiscardChanges,
                onResetToCenter = onResetToCenter,
                onExitEditMode = onExitEditMode
            )
        }
    }
}

@Composable
private fun EditModeOverlayContent(
    waypoint: AATWaypoint,
    currentTargetPoint: AATLatLng,
    originalTargetPoint: AATLatLng,
    hasUnsavedChanges: Boolean,
    sessionDuration: Long,
    onSaveChanges: () -> Unit,
    onDiscardChanges: () -> Unit,
    onResetToCenter: () -> Unit,
    onExitEditMode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditModeHeader(
            waypoint = waypoint,
            sessionDuration = sessionDuration
        )

        TargetPointInfoCard(
            waypoint = waypoint,
            currentTargetPoint = currentTargetPoint,
            originalTargetPoint = originalTargetPoint
        )

        EditModeActionButtons(
            hasUnsavedChanges = hasUnsavedChanges,
            onSaveChanges = onSaveChanges,
            onDiscardChanges = onDiscardChanges,
            onResetToCenter = onResetToCenter,
            onExitEditMode = onExitEditMode
        )
    }
}
