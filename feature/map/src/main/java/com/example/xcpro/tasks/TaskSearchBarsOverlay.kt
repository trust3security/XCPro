package com.example.xcpro.tasks

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.common.waypoint.toSearchWaypoint
import com.example.xcpro.tasks.TaskManagerCoordinator
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSearchBarsOverlay(
    allWaypoints: List<WaypointData>,
    taskManager: TaskManagerCoordinator,
    onClose: () -> Unit,
    onGoto: (SearchWaypoint) -> Unit
) {
    val context = LocalContext.current

    var query by remember { mutableStateOf(TextFieldValue()) }
    var results by remember { mutableStateOf<List<SearchWaypoint>>(emptyList()) }
    var recentWaypoints by remember { mutableStateOf<List<SearchWaypoint>>(emptyList()) }

    // focus + keyboard handling
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var refocusKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        recentWaypoints = loadRecentWaypoints(context)
        results = recentWaypoints
    }

    // whenever refocusKey increments, re-request focus on the next frame
    LaunchedEffect(refocusKey) {
        if (refocusKey > 0) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 32.dp
            )
    ) {
        // search bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 1.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                BasicTextField(
                    value = query.text,
                    onValueChange = { newText ->
                        query = query.copy(text = newText)
                        results = if (newText.isEmpty()) {
                            recentWaypoints
                        } else if (newText.length >= 2) {
                            allWaypoints.filter { wp ->
                                wp.name.contains(newText, ignoreCase = true) ||
                                        wp.code.contains(newText, ignoreCase = true)
                            }.take(30).map { it.toSearchWaypoint() }
                        } else emptyList()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = Color.Black
                    ),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (query.text.isEmpty()) {
                            Text(
                                text = "Search waypoints...",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Gray
                            )
                        }
                        innerTextField()
                    }
                )

                if (query.text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            query = TextFieldValue(text = "", selection = TextRange(0))
                            results = recentWaypoints
                            refocusKey++
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // results list
        if (results.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp) // Increased from 200.dp to 300.dp for halfway display
                    ) {
                        items(results.size) { i ->
                            val wp = results[i]
                            ListItem(
                                headlineContent = { Text(wp.title) },
                                supportingContent = { Text(wp.subtitle) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newRecentWaypoints =
                                            (listOf(wp) + recentWaypoints.filter { it.id != wp.id }).take(5)
                                        recentWaypoints = newRecentWaypoints
                                        saveRecentWaypoints(context, newRecentWaypoints)

                                        taskManager.addWaypoint(wp)
                                        onGoto(wp)

                                        query = TextFieldValue(text = "", selection = TextRange(0))
                                        results = recentWaypoints
                                        refocusKey++
                                    }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

    }
}

private fun saveRecentWaypoints(context: Context, waypoints: List<SearchWaypoint>) {
    try {
        val sharedPrefs = context.getSharedPreferences("RecentWaypoints", Context.MODE_PRIVATE)
        val json = JSONArray()
        waypoints.forEach { waypoint ->
            val waypointJson = JSONObject().apply {
                put("id", waypoint.id)
                put("title", waypoint.title)
                put("subtitle", waypoint.subtitle)
                put("lat", waypoint.lat)
                put("lon", waypoint.lon)
            }
            json.put(waypointJson)
        }
        sharedPrefs.edit()
            .putString("recent_waypoints", json.toString())
            .apply()
    } catch (e: Exception) {
        println("Error saving recent waypoints: ${e.message}")
    }
}

private fun loadRecentWaypoints(context: Context): List<SearchWaypoint> {
    return try {
        val sharedPrefs = context.getSharedPreferences("RecentWaypoints", Context.MODE_PRIVATE)
        val jsonString = sharedPrefs.getString("recent_waypoints", null) ?: return emptyList()
        val json = JSONArray(jsonString)
        val waypoints = mutableListOf<SearchWaypoint>()
        for (i in 0 until json.length()) {
            val waypointJson = json.getJSONObject(i)
            val waypoint = SearchWaypoint(
                id = waypointJson.getString("id"),
                title = waypointJson.getString("title"),
                subtitle = waypointJson.getString("subtitle"),
                lat = waypointJson.getDouble("lat"),
                lon = waypointJson.getDouble("lon")
            )
            waypoints.add(waypoint)
        }
        waypoints
    } catch (e: Exception) {
        emptyList()
    }
}


