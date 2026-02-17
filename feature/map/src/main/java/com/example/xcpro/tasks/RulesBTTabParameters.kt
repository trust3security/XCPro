package com.example.xcpro.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.tasks.core.AATTaskTimeCustomParams
import com.example.xcpro.tasks.core.Task
import java.time.Duration
import java.util.Locale

@Composable
internal fun RulesRacingTaskParameters() {
    RulesParameterSection(
        title = "Racing Task Rules",
        icon = Icons.Default.Speed,
        color = RacingTaskColor
    ) {
        RulesParameterItem(
            label = "Start Type",
            value = "Start Line / Start Circle"
        )
        RulesParameterItem(
            label = "Turnpoints",
            value = "Fixed cylinders (500m radius)"
        )
        RulesParameterItem(
            label = "Finish",
            value = "Finish line or cylinder"
        )
        RulesParameterItem(
            label = "Scoring",
            value = "Speed: Distance  Time"
        )
    }
}

@Composable
internal fun RulesAatTaskParameters(
    task: Task,
    onUpdateAATParameters: (Duration, Duration) -> Unit
) {
    val initialTimes = remember(task) { extractAatTimes(task) }
    val initialMinTime = initialTimes.first
    val initialMaxTime = initialTimes.second

    var minTime by remember { mutableStateOf(initialMinTime) }
    var maxTime by remember { mutableStateOf(initialMaxTime) }

    RulesParameterSection(
        title = "AAT Task Parameters",
        icon = Icons.Default.LocationOn,
        color = AatTaskColor
    ) {
        Column {
            Text(
                text = "Minimum Time: ${String.format(Locale.US, "%.1f", minTime)} hours",
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = minTime,
                onValueChange = {
                    minTime = it
                    onUpdateAATParameters(
                        Duration.ofMinutes((minTime * 60).toLong()),
                        Duration.ofMinutes((maxTime * 60).toLong())
                    )
                },
                valueRange = 1.0f..6.0f,
                steps = 49
            )
        }

        Spacer(Modifier.height(8.dp))

        Column {
            Text(
                text = "Maximum Time: ${String.format(Locale.US, "%.1f", maxTime)} hours",
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = maxTime,
                onValueChange = {
                    maxTime = it
                    onUpdateAATParameters(
                        Duration.ofMinutes((minTime * 60).toLong()),
                        Duration.ofMinutes((maxTime * 60).toLong())
                    )
                },
                valueRange = 2.0f..8.0f,
                steps = 59
            )
        }

        Spacer(Modifier.height(12.dp))

        RulesParameterItem(
            label = "Areas",
            value = "Cylinders/Sectors (10km+ radius)"
        )
        RulesParameterItem(
            label = "Strategy",
            value = "Maximize distance within time"
        )
        RulesParameterItem(
            label = "Scoring",
            value = "Distance handicapped by time"
        )
    }
}

private fun extractAatTimes(task: Task): Pair<Float, Float> {
    val sample = task.waypoints.firstOrNull()?.customParameters ?: emptyMap()
    val times = AATTaskTimeCustomParams.from(
        source = sample,
        fallbackMinimumTimeSeconds = Duration.ofHours(3).seconds.toDouble(),
        fallbackMaximumTimeSeconds = Duration.ofHours(4).seconds.toDouble()
    )
    val minHours = (times.minimumTimeSeconds / 3600.0).toFloat()
    val maxHours = ((times.maximumTimeSeconds ?: Duration.ofHours(4).seconds.toDouble()) / 3600.0).toFloat()
    return minHours to maxHours
}

@Composable
private fun RulesParameterSection(
    title: String,
    icon: ImageVector,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun RulesParameterItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
