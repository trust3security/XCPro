package com.example.xcpro.profiles.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.xcpro.profiles.ProfileCreationRequest
import com.example.xcpro.profiles.ProfileUiState
import com.example.xcpro.profiles.UserProfile

@Composable
fun ProfileSelectionContent(
    state: ProfileUiState,
    onSelectProfile: (UserProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onCreateProfile: (ProfileCreationRequest) -> Unit,
    onShowCreateDialog: () -> Unit,
    onHideCreateDialog: () -> Unit,
    onClearError: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    onEditProfile: (UserProfile) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProfileSelectionHeader()

        when {
            state.isLoading -> ProfileSelectionLoading(
                modifier = Modifier.weight(1f)
            )

            state.profiles.isEmpty() -> ProfileEmptyState(
                modifier = Modifier.weight(1f),
                onCreateFirstProfile = onShowCreateDialog
            )

            else -> ProfileListSection(
                profiles = state.profiles,
                activeProfileId = state.activeProfile?.id,
                onSelectProfile = onSelectProfile,
                onDeleteProfile = onDeleteProfile,
                onShowCreateDialog = onShowCreateDialog,
                onEditProfile = onEditProfile,
                modifier = Modifier.weight(1f)
            )
        }

        state.activeProfile?.let { active ->
            SelectedProfileCard(
                profile = active,
                onContinue = onContinue
            )
        }

        state.error?.let { message ->
            ProfileErrorCard(
                message = message,
                onDismiss = onClearError
            )
        }

        if (state.profiles.isNotEmpty() && state.activeProfile == null) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Skip for now (continue without profile)")
            }
        }
    }

    if (state.showCreateDialog) {
        CreateProfileDialog(
            onDismiss = onHideCreateDialog,
            onCreate = onCreateProfile
        )
    }
}

@Composable
private fun ProfileSelectionHeader() {
    Text(
        text = "Select Flight Profile",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 24.dp)
    )

    Text(
        text = "Choose the profile for your current aircraft and flight configuration.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 32.dp)
    )
}

@Composable
private fun ProfileSelectionLoading(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ProfileEmptyState(
    modifier: Modifier = Modifier,
    onCreateFirstProfile: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "XCPro",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Welcome to your flight app!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Create your first flight profile to get started.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Button(
                onClick = onCreateFirstProfile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Your First Profile")
            }
        }
    }
}

@Composable
private fun SelectedProfileCard(
    profile: UserProfile,
    onContinue: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = profile.aircraftType.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Profile Selected!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = profile.getDisplayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Continue to Flight Map", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ProfileErrorCard(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss error",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
