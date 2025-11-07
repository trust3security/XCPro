package com.example.xcpro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun RulesTab(
    task: SoaringTask,
    onTaskUpdated: (SoaringTask) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Task Rules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Task Type: ${task.type.displayName}")
                    Text("Start Method: Gate")
                    Text("Finish Method: Line")
                    Text("Max Start Height: 1000ft AGL")
                }
            }
        }
    }
}

@Composable
internal fun ManageTab(task: SoaringTask) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Task Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (task.name.isNotBlank()) {
                        Text("Task: ${task.name}")
                        Text("Waypoints: ${task.waypoints.size}")

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Button(
                        onClick = { /* TODO: Save task */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Task")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { /* TODO: Export to .cup */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export to .CUP")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { /* TODO: Load existing task */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load Existing Task")
                    }
                }
            }
        }
    }
}

@Composable
internal fun XXXTab() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "XXX Content",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Placeholder content for XXX tab")
                }
            }
        }
    }
}
