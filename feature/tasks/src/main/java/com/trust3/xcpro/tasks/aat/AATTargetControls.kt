package com.trust3.xcpro.tasks.aat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.tasks.domain.model.TaskTargetSnapshot

/**
 * Compact row to edit an AAT target parameter and lock state.
 */
@Composable
fun AATTargetControls(
    target: TaskTargetSnapshot?,
    onParamChanged: (Double) -> Unit,
    onLockToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (target == null || !target.allowsTarget) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Target",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = target.targetParam.toFloat(),
            onValueChange = { onParamChanged(it.toDouble()) },
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${(target.targetParam * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onLockToggle) {
            Icon(
                imageVector = if (target.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = if (target.isLocked) "Unlock target" else "Lock target",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}
