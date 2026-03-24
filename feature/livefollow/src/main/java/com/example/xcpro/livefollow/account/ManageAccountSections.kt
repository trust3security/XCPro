package com.example.xcpro.livefollow.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ScreenIntroCard() {
    SectionCard(title = "XCPro private follow") {
        Text(
            text = "Search, follow requests, and relationship state now live in the signed-in private-follow account area.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The current public share-code LiveFollow lane remains unchanged.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun MessageCard(
    statusMessage: String?,
    errorMessage: String?,
    onDismiss: () -> Unit
) {
    val body = statusMessage ?: errorMessage ?: return
    SectionCard(
        title = if (errorMessage != null) "Status" else "Update",
        action = if (statusMessage != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        } else {
            null
        }
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = if (errorMessage != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
internal fun SignedOutCard(
    uiState: XcAccountUiState,
    onSignIn: (XcAccountSignInMethod) -> Unit
) {
    SectionCard(title = "Sign in") {
        Text(
            text = "An XCPro account is required for the future private-follow lane. Public watching by share code still works without sign-in.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        uiState.signInCapabilities.forEachIndexed { index, capability ->
            SignInOption(
                capability = capability,
                isBusy = uiState.isSigningIn,
                onSignIn = onSignIn
            )
            if (index != uiState.signInCapabilities.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun SignInOption(
    capability: XcAccountSignInCapability,
    isBusy: Boolean,
    onSignIn: (XcAccountSignInMethod) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = capability.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = capability.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        capability.availabilityNote?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledTonalButton(
            onClick = { onSignIn(capability.method) },
            enabled = capability.isAvailable && !isBusy
        ) {
            Text(capability.title)
        }
    }
}

@Composable
internal fun SignedInUnavailableCard(
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    SectionCard(title = "Account details unavailable") {
        Text(
            text = "Your XCPro session is stored, but the app could not load your pilot profile and privacy settings yet.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onRefresh) {
                Text("Retry")
            }
            OutlinedButton(onClick = onSignOut) {
                Text("Sign out")
            }
        }
    }
}

@Composable
internal fun AccountSummaryCard(
    uiState: XcAccountUiState,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    SectionCard(title = "Account") {
        LabeledValue(label = "User ID", value = uiState.userId ?: "")
        uiState.authMethodLabel?.let {
            Spacer(modifier = Modifier.height(8.dp))
            LabeledValue(label = "Session source", value = it)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onRefresh, enabled = !uiState.isLoading) {
                Text("Refresh")
            }
            OutlinedButton(onClick = onSignOut, enabled = !uiState.isLoading) {
                Text("Sign out")
            }
        }
    }
}

@Composable
internal fun ProfileEditorCard(
    uiState: XcAccountUiState,
    onHandleChanged: (String) -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onCompNumberChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    SectionCard(
        title = if (uiState.needsProfileCompletion) {
            "Complete pilot profile"
        } else {
            "Pilot profile"
        }
    ) {
        if (uiState.needsProfileCompletion) {
            Text(
                text = "Handle and display name are required before the private-follow lane can use this account.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = uiState.handle,
            onValueChange = onHandleChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Handle") },
            supportingText = {
                Text(XCPRO_HANDLE_RULE_MESSAGE)
            },
            singleLine = true,
            enabled = !uiState.isSavingProfile && !uiState.isLoading
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.displayName,
            onValueChange = onDisplayNameChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Display name") },
            supportingText = {
                Text("Shown to future signed-in viewers.")
            },
            singleLine = true,
            enabled = !uiState.isSavingProfile && !uiState.isLoading
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.compNumber,
            onValueChange = onCompNumberChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Competition number") },
            supportingText = {
                Text("Optional. Leave blank if you do not use one.")
            },
            singleLine = true,
            enabled = !uiState.isSavingProfile && !uiState.isLoading
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onSave,
            enabled = uiState.profileSaveEnabled
        ) {
            Text(if (uiState.isSavingProfile) "Saving..." else "Save profile")
        }
    }
}

@Composable
internal fun PrivacyCard(
    uiState: XcAccountUiState,
    onDiscoverabilitySelected: (XcDiscoverability) -> Unit,
    onFollowPolicySelected: (XcFollowPolicy) -> Unit,
    onDefaultLiveVisibilitySelected: (XcDefaultLiveVisibility) -> Unit,
    onConnectionListVisibilitySelected: (XcConnectionListVisibility) -> Unit,
    onSave: () -> Unit
) {
    SectionCard(title = "Privacy defaults") {
        Text(
            text = "These settings are stored now for later follow and authenticated-live slices. They do not change the current public share-code lane yet.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        OptionGroup(
            title = "Discoverability",
            options = XcDiscoverability.entries,
            selected = uiState.privacy.discoverability,
            titleOf = { it.title },
            subtitleOf = { it.subtitle },
            onSelected = onDiscoverabilitySelected
        )
        Spacer(modifier = Modifier.height(16.dp))
        OptionGroup(
            title = "Follow policy",
            options = XcFollowPolicy.entries,
            selected = uiState.privacy.followPolicy,
            titleOf = { it.title },
            subtitleOf = { it.subtitle },
            onSelected = onFollowPolicySelected
        )
        Spacer(modifier = Modifier.height(16.dp))
        OptionGroup(
            title = "Default live visibility",
            options = XcDefaultLiveVisibility.entries,
            selected = uiState.privacy.defaultLiveVisibility,
            titleOf = { it.title },
            subtitleOf = { it.subtitle },
            onSelected = onDefaultLiveVisibilitySelected
        )
        Spacer(modifier = Modifier.height(16.dp))
        OptionGroup(
            title = "Connection list visibility",
            options = XcConnectionListVisibility.entries,
            selected = uiState.privacy.connectionListVisibility,
            titleOf = { it.title },
            subtitleOf = { it.subtitle },
            onSelected = onConnectionListVisibilitySelected
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onSave,
            enabled = uiState.privacySaveEnabled
        ) {
            Text(if (uiState.isSavingPrivacy) "Saving..." else "Save privacy")
        }
    }
}

@Composable
private fun <T> OptionGroup(
    title: String,
    options: List<T>,
    selected: T,
    titleOf: (T) -> String,
    subtitleOf: (T) -> String,
    onSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        options.forEach { option ->
            Surface(
                onClick = { onSelected(option) },
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = option == selected,
                        onClick = null
                    )
                    Column {
                        Text(
                            text = titleOf(option),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = subtitleOf(option),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LabeledValue(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
internal fun SectionCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                action?.invoke()
            }
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}
