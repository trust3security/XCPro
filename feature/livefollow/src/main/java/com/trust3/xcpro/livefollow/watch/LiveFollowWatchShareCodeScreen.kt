package com.trust3.xcpro.livefollow.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.livefollow.normalizeLiveFollowShareCode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveFollowWatchShareCodeScreen(
    onNavigateBack: () -> Unit,
    onOpenWatch: (String) -> Unit
) {
    var shareCodeInput by rememberSaveable { mutableStateOf("") }
    val normalizedShareCode = normalizeLiveFollowShareCode(shareCodeInput)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Watch By Share Code") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Enter the 8-character share code from the pilot device.",
                style = MaterialTheme.typography.bodyLarge
            )
            OutlinedTextField(
                value = shareCodeInput,
                onValueChange = { nextValue ->
                    shareCodeInput = nextValue
                        .uppercase(Locale.US)
                        .filter(Char::isLetterOrDigit)
                        .take(8)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Share code") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                )
            )
            Text(
                text = "Paste or type the pilot share code, then open the public LiveFollow watch view.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    normalizedShareCode?.let(onOpenWatch)
                },
                enabled = normalizedShareCode != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open watch")
            }
        }
    }
}
