package com.trust3.xcpro.components

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trust3.xcpro.airspace.AirspaceViewModel
import com.trust3.xcpro.map.ui.documentRefForUri

private const val TAG = "AirspaceComponents"

@Composable
fun AirspaceSettingsContent(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val airspaceViewModel: AirspaceViewModel = hiltViewModel()
    val uiState by airspaceViewModel.uiState.collectAsStateWithLifecycle()

    val airspaceFilePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { airspaceViewModel.importFile(documentRefForUri(context, it)) }
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Airspace Settings",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = {
                try {
                    airspaceFilePickerLauncher.launch("text/plain")
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "No activity found to handle file picker: ${e.message}")
                    airspaceViewModel.setError("No file picker available on this device.")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("Select Airspace Files")
        }
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
        val fileListState = rememberLazyListState()
        val isFileScrollable = fileListState.canScrollForward || fileListState.canScrollBackward
        LazyColumn(
            state = fileListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .let { mod ->
                    if (isFileScrollable) {
                        mod.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        ).padding(8.dp)
                    } else {
                        mod
                    }
                }
        ) {
            items(uiState.fileItems, key = { it.name }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = item.enabled,
                                onCheckedChange = { airspaceViewModel.toggleFile(item.name) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { airspaceViewModel.deleteFile(item.name) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = "Airspace Classes",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        val classListState = rememberLazyListState()
        val isClassScrollable = classListState.canScrollForward || classListState.canScrollBackward
        LazyColumn(
            state = classListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .let { mod ->
                    if (isClassScrollable) {
                        mod.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        ).padding(8.dp)
                    } else {
                        mod
                    }
                }
        ) {
            items(uiState.classItems, key = { it.className }) { airspaceClass ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Class ${airspaceClass.className}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = airspaceClass.enabled,
                            onCheckedChange = { airspaceViewModel.toggleClass(airspaceClass.className) }
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    }
}
