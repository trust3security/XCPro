package com.example.xcpro.screens.navdrawer.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.example.xcpro.common.documents.DocumentRef

@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(Color(0xFFFFE2E2), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = Color(0xFF8B0000),
            modifier = Modifier
        )
        Text(
            text = "Dismiss",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = Color(0xFF8B0000),
            modifier = Modifier
                .padding(start = 8.dp)
                .clickable { onDismiss() }
        )
    }
}

@Composable
fun TaskSelectedFileList(
    files: List<DocumentRef>,
    onRemove: (DocumentRef) -> Unit
) {
    if (files.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        files.forEach { document ->
            val fileName = document.fileName()
            androidx.compose.material.Surface(
                shape = RoundedCornerShape(10.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(fileName, style = MaterialTheme.typography.body1)
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.caption,
                        color = Color.Red,
                        modifier = Modifier.clickable { onRemove(document) }
                    )
                }
            }
        }
    }
}
