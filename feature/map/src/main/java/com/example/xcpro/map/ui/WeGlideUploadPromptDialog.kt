package com.example.xcpro.map.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.xcpro.map.WeGlideUploadPromptUiState

@Composable
internal fun WeGlideUploadPromptDialogHost(
    prompt: WeGlideUploadPromptUiState?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (prompt == null) {
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Upload to WeGlide")
        },
        text = {
            Text(
                text = buildString {
                    append("Upload ")
                    append(prompt.fileName)
                    append(" to WeGlide using ")
                    append(prompt.aircraftName)
                    append("?")
                    prompt.profileName?.takeIf { it.isNotBlank() }?.let { profileName ->
                        append("\n\nProfile: ")
                        append(profileName)
                    }
                },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Yes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("No")
            }
        }
    )
}
